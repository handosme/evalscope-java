package com.evalscope.batchjob.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;

/**
 * 处理来自服务器的HTTP响应
 */
public class BatchRequestHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private final NettyBatchClient client;
    private final ResponseAggregator responseAggregator;

    /**
     * 创建一个新的BatchRequestHandler
     * 
     * @param client Netty客户端
     * @param responseAggregator 响应聚合器
     */
    public BatchRequestHandler(NettyBatchClient client, ResponseAggregator responseAggregator) {
        this.client = client;
        this.responseAggregator = responseAggregator;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
        // 从响应头中获取请求ID和批次ID
        String requestId = response.headers().get("X-Request-ID");
        String batchId = response.headers().get("X-Batch-ID");
        
        if (requestId == null || batchId == null) {
            // 尝试从其他头部获取
            for (String name : response.headers().names()) {
                String value = response.headers().get(name);
                if (name.toLowerCase().contains("request") && name.toLowerCase().contains("id")) {
                    requestId = value;
                }
                if (name.toLowerCase().contains("batch") && name.toLowerCase().contains("id")) {
                    batchId = value;
                }
            }
        }
        
        // 如果仍然找不到ID，使用默认值
        if (requestId == null) {
            requestId = "unknown-request";
        }
        if (batchId == null) {
            batchId = "unknown-batch";
        }
        
        // 检查响应状态
        int statusCode = response.status().code();
        if (statusCode >= 200 && statusCode < 300) {
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
        // 处理异常
        String errorMessage = "连接错误: " + cause.getMessage();
        // 由于无法确定具体的请求ID和批次ID，这里只能记录错误
        // 实际应用中可能需要更复杂的错误处理机制
        System.err.println(errorMessage);
        cause.printStackTrace();
    }
}