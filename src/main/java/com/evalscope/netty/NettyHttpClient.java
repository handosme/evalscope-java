package com.evalscope.netty;

import com.evalscope.model.openai.OpenAIRequest;
import com.evalscope.model.openai.OpenAIResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Netty-based HTTP client for OpenAI API with SSE streaming support
 */
public class NettyHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttpClient.class);

    private final Bootstrap bootstrap;
    private final EventLoopGroup group;
    private final ObjectMapper objectMapper;
    private final SslContext sslContext;

    public NettyHttpClient() throws SSLException {
        this(null);
    }

    public NettyHttpClient(SslContext sslContext) throws SSLException {
        this.sslContext = sslContext != null ? sslContext : createDefaultSslContext();
        this.group = new NioEventLoopGroup();
        this.objectMapper = new ObjectMapper();
        this.bootstrap = createBootstrap();
    }

    private Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .option(ChannelOption.SO_TIMEOUT, 60)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc()));
                        }
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(1048576)); // 1MB
                    }
                });
        return bootstrap;
    }

    private SslContext createDefaultSslContext() throws SSLException {
        return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    /**
     * Send HTTP request and return complete response as string
     */
    public CompletableFuture<String> sendRequest(String url, String requestBody, String authHeader) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            String path = uri.getPath();
            if (path.isEmpty()) path = "/";
            if (uri.getQuery() != null) path += "?" + uri.getQuery();

            HttpRequest request = createRequest(uri, host, path, requestBody, authHeader);

            logger.debug("Sending {} request to {}:{}", uri.getScheme(), host, port);

            // No need for connectPromise, we'll use the channelFuture directly

            ChannelFuture channelFuture = bootstrap.connect(host, port);
            channelFuture.addListener((ChannelFutureListener) futureChannel -> {
                if (futureChannel.isSuccess()) {
                    Channel channel = futureChannel.channel();
                    channel.config().setAutoRead(false);

                    channel.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
                            int statusCode = response.status().code();
                            ByteBuf content = response.content();
                            String responseBody = content.toString(CharsetUtil.UTF_8);

                            logger.debug("Received response with status: {} and body length: {}", statusCode, responseBody.length());

                            if (statusCode >= 200 && statusCode < 300) {
                                future.complete(responseBody);
                            } else {
                                future.completeExceptionally(
                                    new RuntimeException("HTTP " + statusCode + ": " + responseBody)
                                );
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            logger.error("Error during request", cause);
                            future.completeExceptionally(cause);
                            ctx.close();
                        }
                    });

                    channel.writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
                        if (!writeFuture.isSuccess()) {
                            future.completeExceptionally(writeFuture.cause());
                        } else {
                            logger.debug("Request sent successfully");
                        }
                    });

                } else {
                    future.completeExceptionally(futureChannel.cause());
                }
            });

        } catch (URISyntaxException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Send HTTP request with SSE streaming support
     */
    public CompletableFuture<Void> sendStreamingRequest(String url, String requestBody, String authHeader,
                                                      Consumer<String> chunkConsumer, Consumer<Throwable> errorConsumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            String path = uri.getPath();
            if (path.isEmpty()) path = "/";
            if (uri.getQuery() != null) path += "?" + uri.getQuery();

            HttpRequest request = createRequest(uri, host, path, requestBody, authHeader);
            request.headers().add(HttpHeaderNames.ACCEPT, "text/event-stream");
            request.headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache");
            request.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

            logger.debug("Sending streaming {} request to {}:{}", uri.getScheme(), host, port);

            ChannelFuture channelFuture = bootstrap.connect(host, port);
            channelFuture.addListener((ChannelFutureListener) futureChannel -> {
                if (futureChannel.isSuccess()) {
                    Channel channel = futureChannel.channel();

                    // Add streaming handler to process SSE events
                    channel.pipeline().addLast(new SSEChunkedResponseHandler(chunkConsumer, errorConsumer, future));

                    channel.writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
                        if (!writeFuture.isSuccess()) {
                            future.completeExceptionally(writeFuture.cause());
                        }
                    });

                } else {
                    future.completeExceptionally(futureChannel.cause());
                }
            });

        } catch (URISyntaxException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private FullHttpRequest createRequest(URI uri, String host, String path, String requestBody, String authHeader) {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            path,
            Unpooled.copiedBuffer(requestBody, CharsetUtil.UTF_8)
        );

        // Set headers
        HttpHeaders headers = request.headers();
        headers.set(HttpHeaderNames.HOST, host);
        headers.set(HttpHeaderNames.USER_AGENT, "EvalScope-NettyClient/1.0");
        headers.set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        headers.set(HttpHeaderNames.ACCEPT, "application/json");
        headers.set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());

        if (authHeader != null && !authHeader.isEmpty()) {
            headers.set(HttpHeaderNames.AUTHORIZATION, authHeader);
        }

        return request;
    }

    public void shutdown() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    /**
     * SSE Chunked Response Handler for processing streaming data
     */
    private static class SSEChunkedResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final Consumer<String> chunkConsumer;
        private final Consumer<Throwable> errorConsumer;
        private final CompletableFuture<Void> future;
        private static final Logger logger = LoggerFactory.getLogger(SSEChunkedResponseHandler.class);

        public SSEChunkedResponseHandler(Consumer<String> chunkConsumer, Consumer<Throwable> errorConsumer, CompletableFuture<Void> future) {
            this.chunkConsumer = chunkConsumer;
            this.errorConsumer = errorConsumer;
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                logger.debug("Received {} response", response.status());

                if (response.status().code() >= 400) {
                    throw new RuntimeException("HTTP " + response.status().code() + " error");
                }
            }

            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                ByteBuf buffer = content.content();
                String chunkData = buffer.toString(CharsetUtil.UTF_8);
                logger.debug("Received chunk of {} bytes", chunkData.length());

                // Process SSE events
                processSSEData(chunkData);

                buffer.release();

                if (msg instanceof LastHttpContent) {
                    logger.debug("Received last content chunk");
                    future.complete(null);
                }
            }
        }

        private void processSSEData(String data) {
            String[] lines = data.split("\n");
            StringBuilder eventBuilder = new StringBuilder();

            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    String eventData = line.substring(6);
                    if ("[DONE]".equals(eventData.trim())) {
                        future.complete(null);
                        return;
                    }

                    try {
                        chunkConsumer.accept(eventData);
                    } catch (Exception e) {
                        logger.error("Error processing chunk", e);
                        errorConsumer.accept(e);
                        future.completeExceptionally(e);
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Error in streaming handler", cause);
            errorConsumer.accept(cause);
            future.completeExceptionally(cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.debug("Channel closed, completing stream");
            if (!future.isDone()) {
                future.complete(null);
            }
            super.channelInactive(ctx);
        }
    }

    public static class SSEEvent {
        private String id;
        private String event;
        private String data;
        private String retry;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }

        public String getRetry() { return retry; }
        public void setRetry(String retry) { this.retry = retry; }
    }
}