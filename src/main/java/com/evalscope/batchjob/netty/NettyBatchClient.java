package com.evalscope.batchjob.netty;

import com.evalscope.batchjob.BatchJobConfig;
import com.evalscope.batchjob.model.BatchRequest;
import com.evalscope.batchjob.model.BatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 使用Netty实现的高性能批处理客户端
 * 支持高并发、异步非阻塞IO，用于大批量API调用
 */
public class NettyBatchClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyBatchClient.class);

    private final BatchJobConfig config;
    private final EventLoopGroup eventLoopGroup;
    private final RequestCorrelationManager correlationManager;
    private final Map<String, Consumer<BatchResponse>> responseCallbacks;
    private final Map<String, Consumer<Throwable>> errorCallbacks;
    private final Semaphore requestSemaphore;
    private final AtomicInteger activeConnections;
    private final ThreadPoolExecutor executorService;
    private final ResponseAggregator responseAggregator;
    private Channel channel;
    private Bootstrap bootstrap;
    private URI uri;
    private boolean isHttps;

    /**
     * 创建一个新的NettyBatchClient
     * 
     * @param config 批处理配置
     */
    public NettyBatchClient(BatchJobConfig config) {
        this.config = config;
        this.responseCallbacks = new ConcurrentHashMap<>();
        this.errorCallbacks = new ConcurrentHashMap<>();
        this.requestSemaphore = new Semaphore(config.getMaxConcurrentRequests());
        this.activeConnections = new AtomicInteger(0);
        this.responseAggregator = new ResponseAggregator(this);
        this.correlationManager = new RequestCorrelationManager();
        
        // 创建线程池
        this.executorService = new ThreadPoolExecutor(
            config.getThreadPoolSize(),
            config.getThreadPoolSize(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new DefaultThreadFactory("netty-batch-client-pool")
        );
        
        // 创建事件循环组
        this.eventLoopGroup = new NioEventLoopGroup(
            config.getThreadPoolSize(),
            new DefaultThreadFactory("netty-event-loop")
        );
        
        try {
            this.uri = new URI(config.getApiEndpoint());
            this.isHttps = "https".equalsIgnoreCase(uri.getScheme());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid API endpoint URL: " + config.getApiEndpoint(), e);
        }
    }

    /**
     * 连接到API服务器
     */
    public void connect() {
        try {
            final SslContext sslContext = isHttps ? 
                SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build() : null;
            
            bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout())
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        
                        // SSL处理器（如果需要）
                        if (sslContext != null) {
                            p.addLast(sslContext.newHandler(ch.alloc(), uri.getHost(), getPort()));
                        }
                        
                        // 超时处理器
                        p.addLast(new ReadTimeoutHandler(config.getRequestTimeout(), TimeUnit.MILLISECONDS));
                        p.addLast(new WriteTimeoutHandler(config.getRequestTimeout(), TimeUnit.MILLISECONDS));
                        
                        // HTTP编解码器
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpContentDecompressor());
                        p.addLast(new HttpObjectAggregator(10 * 1024 * 1024)); // 10MB最大响应大小
                        
                        // 自定义处理器
                        p.addLast(new BatchRequestHandler(NettyBatchClient.this, responseAggregator, correlationManager));
                    }
                });
            
            // 连接到服务器
            ChannelFuture future = bootstrap.connect(uri.getHost(), getPort()).sync();
            channel = future.channel();
            
        } catch (SSLException | InterruptedException e) {
            throw new RuntimeException("Failed to connect to API server: " + config.getApiEndpoint(), e);
        }
    }

    /**
     * 关闭客户端，释放资源
     */
    public void shutdown() {
        logger.info("正在关闭NettyBatchClient，等待活跃连接完成，当前活跃连接数: {}", getActiveConnections());

        // 首先停止接收新请求
        if (channel != null && channel.isActive()) {
            channel.close();
        }

        // 等待执行器中的任务完成（最多等待30秒）
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("等待执行器关闭超时，强制关闭");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("等待执行器关闭被中断，强制关闭");
            executorService.shutdownNow();
        }

        // 优雅关闭事件循环组
        try {
            if (!eventLoopGroup.shutdownGracefully(5, 10, TimeUnit.SECONDS).await(15, TimeUnit.SECONDS)) {
                logger.warn("事件循环组关闭超时，强制关闭");
            }
        } catch (InterruptedException e) {
            logger.warn("等待事件循环组关闭被中断，强制关闭");
            eventLoopGroup.shutdownNow();
        }

        logger.info("NettyBatchClient已关闭");
    }

    /**
     * 发送批量请求
     * 
     * @param batchId 批次ID
     * @param requests 请求列表
     */
    public void sendBatchRequest(String batchId, List<BatchRequest> requests) {
        sendBatchRequest(batchId, requests, null, null);
    }

    /**
     * 发送批量请求，并设置回调
     * 
     * @param batchId 批次ID
     * @param requests 请求列表
     * @param responseCallback 响应回调
     * @param errorCallback 错误回调
     */
    public void sendBatchRequest(String batchId, List<BatchRequest> requests, 
                                Consumer<BatchResponse> responseCallback,
                                Consumer<Throwable> errorCallback) {
        if (responseCallback != null) {
            responseCallbacks.put(batchId, responseCallback);
        }
        
        if (errorCallback != null) {
            errorCallbacks.put(batchId, errorCallback);
        }
        
        // 创建批处理响应对象
        BatchResponse batchResponse = new BatchResponse(batchId);
        responseAggregator.registerBatch(batchId, batchResponse, requests.size());
        
        // 分批处理请求
        int maxBatchSize = config.getMaxBatchSize();
        for (int i = 0; i < requests.size(); i += maxBatchSize) {
            int endIndex = Math.min(i + maxBatchSize, requests.size());
            List<BatchRequest> batch = requests.subList(i, endIndex);
            
            executorService.submit(() -> {
                try {
                    // 获取信号量，限制并发请求数
                    requestSemaphore.acquire();
                    activeConnections.incrementAndGet();
                    
                    try {
                        // 为每个请求创建HTTP请求并发送
                        for (BatchRequest request : batch) {
                            sendSingleRequest(batchId, request);
                        }
                    } finally {
                        activeConnections.decrementAndGet();
                        requestSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    handleError(batchId, e);
                }
            });
        }
    }

    /**
     * 发送单个请求
     *
     * @param batchId 批次ID
     * @param request 请求
     */
    private void sendSingleRequest(String batchId, BatchRequest request) {
        try {
            // 注册请求关联
            String correlatedRequestId = correlationManager.registerRequest(batchId, request);

            // 构建HTTP请求，并将关联的ID放入头部
            FullHttpRequest httpRequest = buildHttpRequest(request, correlatedRequestId);

            // 发送请求
            channel.writeAndFlush(httpRequest).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    handleSendError(correlatedRequestId, future.cause());
                }
            });

        } catch (Exception e) {
            // 注册失败或发送前异常的处理
            if (request.getRequestId() != null) {
                correlationManager.completeRequest(request.getRequestId());
            }
            responseAggregator.addError(batchId, request.getRequestId(), e.getMessage());
        }
    }

    /**
     * 处理发送请求时的错误
     *
     * @param correlatedRequestId 关联的请求ID
     * @param cause 错误原因
     */
    private void handleSendError(String correlatedRequestId, Throwable cause) {
        // 找出关联的请求信息
        RequestCorrelationManager.OutstandingRequest outstandingRequest = correlationManager.completeRequest(correlatedRequestId);

        if (outstandingRequest != null) {
            // 将错误添加到聚合器中
            responseAggregator.addError(
                    outstandingRequest.getBatchId(),
                    outstandingRequest.getRequestId(),
                    "发送请求失败: " + cause.getMessage()
            );
        } else {
            // 如果找不到对应的请求，记录日志（这个不应该发生）
            logger.warn("收到发送请求失败的回调，但找不到对应的关联请求: {}", correlatedRequestId, cause);
        }
    }

    /**
     * 构建HTTP请求
     *
     * @param request 批处理请求
     * @param correlatedRequestId 关联的请求ID（用于响应关联）
     * @return HTTP请求
     */
    private FullHttpRequest buildHttpRequest(BatchRequest request, String correlatedRequestId) {
        // 这里根据实际API格式构建请求
        // 示例实现，实际应用中需要根据目标API调整
        String jsonPayload = String.format(
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"max_tokens\":%d,\"temperature\":%f}",
            request.getModelName(),
            request.getPrompt().replace("\"", "\\\""),
            request.getMaxTokens(),
            request.getTemperature()
        );
        
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            uri.getPath()
        );
        
        // 设置请求头
        httpRequest.headers().set(HttpHeaderNames.HOST, uri.getHost());
        httpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        httpRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

        // 使用我们内部关联的请求ID（这取代了原来的随机或依赖服务端头部的做法）
        httpRequest.headers().set("X-Request-ID", correlatedRequestId);

        // 优先使用原始请求ID，如果没有则使用关联ID
        if (request.getRequestId() != null && !request.getRequestId().equals(correlatedRequestId)) {
            httpRequest.headers().set("X-Original-Request-ID", request.getRequestId());
        }
        
        // 如果有API密钥，添加授权头
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            httpRequest.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + config.getApiKey());
        }
        
        // 设置请求体
        ByteBufUtil.writeUtf8(httpRequest.content(), jsonPayload);
        httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpRequest.content().readableBytes());
        
        return httpRequest;
    }

    /**
     * 处理批处理响应
     * 
     * @param batchId 批次ID
     * @param response 响应
     */
    void handleResponse(String batchId, BatchResponse response) {
        Consumer<BatchResponse> callback = responseCallbacks.remove(batchId);
        if (callback != null) {
            callback.accept(response);
        }
    }

    /**
     * 处理连接错误
     * 当网络层发生异常时，处理所有未完成的请求
     *
     * @param cause 错误原因
     */
    public void handleConnectionError(Throwable cause) {
        logger.error("网络连接错误影响到未完成请求: {}", cause.getMessage(), cause);

        // 目前不立即标记所有请求为失败，因为错误可能是临时的
        // 具体请求的关联错误会在响应处理层处理
    }

    /**
     * 处理来自HTTP响应的错误
     *
     * @param batchId 批次ID
     * @param error 错误
     */
    void handleError(String batchId, Throwable error) {
        Consumer<Throwable> callback = errorCallbacks.remove(batchId);
        if (callback != null) {
            callback.accept(error);
        }
    }

    /**
     * 获取端口号
     * 
     * @return 端口号
     */
    private int getPort() {
        int port = uri.getPort();
        if (port == -1) {
            return isHttps ? 443 : 80;
        }
        return port;
    }

    /**
     * 获取活跃连接数
     * 
     * @return 活跃连接数
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * 获取配置
     * 
     * @return 配置
     */
    public BatchJobConfig getConfig() {
        return config;
    }

    /**
     * 获取响应聚合器
     * 
     * @return 响应聚合器
     */
    ResponseAggregator getResponseAggregator() {
        return responseAggregator;
    }
}