package com.evalscope.batchjob.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理来自服务器的HTTP响应
 */
public class BatchRequestHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger logger = LoggerFactory.getLogger(BatchRequestHandler.class);

    private final NettyBatchClient client;
    private final ResponseAggregator responseAggregator;
    private final RequestCorrelationManager correlationManager;

    /**
     * 创建一个新的BatchRequestHandler
     *
     * @param client Netty客户端
     * @param responseAggregator 响应聚合器
     * @param correlationManager 请求关联管理器
     */
    public BatchRequestHandler(NettyBatchClient client, ResponseAggregator responseAggregator, RequestCorrelationManager correlationManager) {
        this.client = client;
        this.responseAggregator = responseAggregator;
        this.correlationManager = correlationManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
        // 首先尝试从我们发送请求时设置的关联ID头部获取请求ID
        String correlatedRequestId = response.headers().get("X-Request-ID");

        if (correlatedRequestId == null) {
            // 如果没有关联ID，尝试使用其他可能的头部
            for (String name : response.headers().names()) {
                if (name.toLowerCase().contains("request") && name.toLowerCase().contains("id")) {
                    String value = response.headers().get(name);
                    if (value != null && !value.isEmpty()) {
                        correlatedRequestId = value;
                        break;
                    }
                }
            }
        }

        // 如果没找到关联ID，使用逻辑检查默认值
        if (correlatedRequestId == null) {
            correlatedRequestId = "unknown-request";
        }

        // 使用关联管理器找出对应的请求信息
        RequestCorrelationManager.OutstandingRequest outstandingRequest =
            correlationManager.findOutstandingRequestByExternalId(correlatedRequestId);

        String requestId;
        String batchId;

        if (outstandingRequest != null) {
            // 找到了对应的请求信息，使用真实的信息
            requestId = outstandingRequest.getRequestId();
            batchId = outstandingRequest.getBatchId();

            // 成功关联，现在可以移除这个请求了
            correlationManager.completeRequest(correlatedRequestId);
        } else {
            // 如果没找到（这不应该发生，但留个后路），尝试使用头部信息或其他逻辑
            logger.warn("无法根据关联ID找到对应的请求: {}，使用回退逻辑", correlatedRequestId);
            requestId = correlatedRequestId;

            // 尝试从响应头获取批次ID
            batchId = response.headers().get("X-Batch-ID");
            if (batchId == null) {
                // 如果没有批次ID，尝试使用推理：相关性管理器知道当前活跃批次
                // 如果需要，可以在这里添加更智能的批次检测逻辑
                batchId = "fallback-batch";
            }
        }

        // 检查响应状态
        int statusCode = response.status().code();
        if (statusCode >= 200 && statusCode < 500) {
            // 成功响应
            String content = response.content().toString(CharsetUtil.UTF_8);
            responseAggregator.addResponse(batchId, requestId, content);
        } else {
            // 错误响应
            String errorMessage = "HTTP错误: " + statusCode + " " + response.status().reasonPhrase();
            String errorContent = response.content().toString(CharsetUtil.UTF_8);
            if (errorContent != null && !errorContent.isEmpty()) {
                errorMessage += " - " + errorContent;
            }
            responseAggregator.addError(batchId, requestId, errorMessage);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 处理网络层异常，这可能影响多个请求
        logger.warn("网络连接异常: {}", cause.getMessage(), cause);

        // 这里无法确定具体影响哪些请求，因为异常发生在连接层面
        // 让上层的NettyBatchClient来处理积压的未完成请求
        client.handleConnectionError(cause);
    }
}