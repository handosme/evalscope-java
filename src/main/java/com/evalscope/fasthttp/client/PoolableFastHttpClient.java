package com.evalscope.fasthttp.client;

import com.evalscope.fasthttp.client.FastHttpClient;
import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import com.evalscope.fasthttp.exception.FastHttpException;
import com.evalscope.fasthttp.pool.ConnectionPool;
import com.evalscope.fasthttp.pool.ConnectionPoolConfig;
import com.evalscope.fasthttp.pool.PooledConnection;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.buffer.ByteBuf;
import io.netty.util.AttributeKey;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * FastHttpClient implementation with connection pooling support
 * Provides advanced connection management including:
 * - Connection pooling with configurable limits
 * - Connection idle timeout management
 * - Overflow handling strategies (queue wait, direct reject)
 * - Connection health monitoring
 */
public class PoolableFastHttpClient implements AutoCloseable {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(PoolableFastHttpClient.class.getName());

    // Netty channel attribute to track connection source
    private static final AttributeKey<PooledConnection> POOL_CONNECTION_KEY = AttributeKey.valueOf("pool_connection");

    private final ConnectionPool connectionPool;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    private final ConnectionPoolConfig config;

    private PoolableFastHttpClient(Builder builder) {

        // Create connection pool with provided configuration
        ConnectionPoolConfig poolConfig = builder.connectionPoolConfig;
        if (poolConfig == null) {
            poolConfig = ConnectionPoolConfig.defaultConfig();
        }

        this.connectionPool = new ConnectionPool(poolConfig) {
            @Override
            public void close() {
                // Custom close logic could be added here
                try {
                    super.close();
                } catch (Exception e) {
                    // Convert checked exception to unchecked
                    throw new RuntimeException("Connection pool close failed", e);
                }
            }
        };
        this.config = poolConfig;
    }

    public CompletableFuture<Response> execute(Request request) throws Exception {
        if (closed.get()) {
            CompletableFuture<Response> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Client is closed"));
            return failed;
        }

        try {
            URI uri = new URI(request.url());
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
            boolean useSsl = "https".equals(uri.getScheme());

            return acquireConnectionAndExecute(host, port, useSsl, request);

        } catch (URISyntaxException e) {
            CompletableFuture<Response> failed = new CompletableFuture<>();
            failed.completeExceptionally(new FastHttpException("Invalid URL: " + request.url(), e));
            return failed;
        } catch (Exception e) {
            CompletableFuture<Response> failed = new CompletableFuture<>();
            failed.completeExceptionally(new FastHttpException("Request execution failed", e));
            return failed;
        }
    }

    /**
     * Acquire connection and execute request
     */
    private CompletableFuture<Response> acquireConnectionAndExecute(String host, int port, boolean useSsl, Request request) {
        CompletableFuture<Response> future = new CompletableFuture<>();

        connectionPool.acquireConnection(host, port, useSsl).whenComplete((pooledConnection, acquireError) -> {
            if (acquireError != null) {
                future.completeExceptionally(acquireError);
                return;
            }

            try {
                executeRequestWithPooledConnection(pooledConnection, request, future);
            } catch (Exception e) {
                // Release connection on error
                connectionPool.releaseConnection(pooledConnection);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Execute request using pre-acquired pooled connection
     */
    private void executeRequestWithPooledConnection(PooledConnection pooledConnection, Request request, CompletableFuture<Response> future) {
        Channel channel = pooledConnection.getChannel();

        if (channel == null || !channel.isActive()) {
            connectionPool.releaseConnection(pooledConnection);
            future.completeExceptionally(new FastHttpException("Pooled connection is not active: " + pooledConnection.getConnectionId()));
            return;
        }

        // Store pooled connection reference in channel attributes
        channel.attr(POOL_CONNECTION_KEY).set(pooledConnection);

        // Remove existing handlers and setup for new request
        cleanChannel(channel);
        setupChannel(channel, request, future, pooledConnection);

        // Encode and send request
        try {
            FullHttpRequest httpRequest = encodeRequest(request);
            channel.writeAndFlush(httpRequest).addListener((io.netty.channel.ChannelFuture channelFuture) -> {
                if (!channelFuture.isSuccess()) {
                    future.completeExceptionally(new FastHttpException("Failed to send HTTP request", channelFuture.cause()));
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(new FastHttpException("Request encoding failed", e));
        }
    }

    /**
     * Setup Netty channel with required handlers
     */
    private void setupChannel(Channel channel, Request request, CompletableFuture<Response> future, PooledConnection pooledConnection) {
        io.netty.channel.ChannelPipeline pipeline = channel.pipeline();

        // Add timeout handlers
        pipeline.addLast("readTimeout", new ReadTimeoutHandler(request.timeoutMillis() / 1000));
        pipeline.addLast("writeTimeout", new WriteTimeoutHandler(request.timeoutMillis() / 1000));

        // Add response handler
        pipeline.addLast("responseHandler", new PoolResponseHandler(request, future, pooledConnection, this));
    }

    /**
     * Clean existing handlers from channel
     */
    private void cleanChannel(Channel channel) {
        io.netty.channel.ChannelPipeline pipeline = channel.pipeline();
        pipeline.remove("readTimeout");
        pipeline.remove("writeTimeout");
        pipeline.remove("responseHandler");
    }

    /**
     * Encode Request to FullHttpRequest
     */
    private FullHttpRequest encodeRequest(Request request) throws Exception {
        URI uri = new URI(request.url());
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        String query = uri.getRawQuery();
        if (query != null \u0026\u0026 !query.isEmpty()) {
            path = path + "?" + query;
        }

        HttpMethod method = getHttpMethod(request.method());
        ByteBuf content = request.body() != null && !request.body().isEmpty()
                ? io.netty.buffer.Unpooled.wrappedBuffer(request.body().getBytes())
                : io.netty.buffer.Unpooled.EMPTY_BUFFER;

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                method,
                path,
                content
        );

        // Set headers
        for (java.util.Map.Entry<String, java.util.List<String>> entry : request.headers()) {
            String headerName = entry.getKey();
            for (String headerValue : entry.getValue()) {
                httpRequest.headers().add(headerName, headerValue);
            }
        }

        // Set required headers
        if (!httpRequest.headers().contains(HttpHeaderNames.HOST)) {
            httpRequest.headers().set(HttpHeaderNames.HOST, uri.getHost());
        }

        if (!httpRequest.headers().contains(HttpHeaderNames.CONNECTION)) {
            httpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        if (request.body() != null \u0026\u0026 !httpRequest.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        if (request.body() != null \u0026\u0026 !httpRequest.headers().contains(HttpHeaderNames.CONTENT_TYPE)) {
            httpRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
        }

        return httpRequest;
    }

    private HttpMethod getHttpMethod(String method) {
        switch (method.toUpperCase()) {
            case "GET": return HttpMethod.GET;
            case "POST": return HttpMethod.POST;
            case "PUT": return HttpMethod.PUT;
            case "DELETE": return HttpMethod.DELETE;
            case "HEAD": return HttpMethod.HEAD;
            case "OPTIONS": return HttpMethod.OPTIONS;
            case "PATCH": return HttpMethod.PATCH;
            case "TRACE": return HttpMethod.TRACE;
            case "CONNECT": return HttpMethod.CONNECT;
            default: return new HttpMethod(method);
        }
    }

    /**
     * Get connection pool statistics
     */
    public com.evalscope.fasthttp.pool.ConnectionPool.ConnectionPoolStats getPoolStats() {
        return connectionPool.getStats();
    }

    /**
     * Get connection pool configuration
     */
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return config; // Store config reference in builder and main class
    }

    @Override
    public void close() throws Exception {
        if (closed.compareAndSet(false, true)) {
            connectionPool.close();
        }
    }

    /**
     * Response handler for pooled connections
     */
    private static class PoolResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final Request request;
        private final CompletableFuture<Response> future;
        private final PooledConnection pooledConnection;
        private final PoolableFastHttpClient client;

        private final long startTime;
        private final StringBuilder responseBody = new StringBuilder();
        private Response.Builder responseBuilder;
        private HttpResponse httpResponse;

        PoolResponseHandler(Request request, CompletableFuture<Response> future,
                           PooledConnection pooledConnection, PoolableFastHttpClient client) {
            this.request = request;
            this.future = future;
            this.pooledConnection = pooledConnection;
            this.client = client;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void channelActive(io.netty.channel.ChannelHandlerContext ctx) {
            logger.log(java.util.logging.Level.FINER, "Channel active for connection: " + pooledConnection.getConnectionId());
        }

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, HttpObject msg) {
            try {
                if (pooledConnection.getState() == PooledConnection.ConnectionState.CLOSED) {
                    completeWithError(ctx, new FastHttpException("Connection closed during response"));
                    return;
                }

                if (msg instanceof HttpResponse) {
                    httpResponse = (HttpResponse) msg;

                    // Check if we should keep the connection alive
                    boolean keepAlive = HttpUtil.isKeepAlive(httpResponse) \u0026\u0026 client.getConnectionPoolConfig().isEnableConnectionReuse();

                    responseBuilder = new Response.Builder()
                            .request(request)
                            .code(httpResponse.status().code())
                            .message(httpResponse.status().reasonPhrase())
                            .elapsedTimeMs(System.currentTimeMillis() - startTime);

                    // Process headers
                    for (String name : httpResponse.headers().names()) {
                        for (String value : httpResponse.headers().getAll(name)) {
                            responseBuilder.addHeader(name, value);
                        }
                    }

                    // Set connection header based on keep-alive decision
                    responseBuilder.addHeader("Connection", keepAlive ? "keep-alive" : "close");
                }

                if (msg instanceof HttpContent) {
                    HttpContent httpContent = (HttpContent) msg;
                    ByteBuf content = httpContent.content();
                    if (content.isReadable()) {
                        responseBody.append(content.toString(StandardCharsets.UTF_8));
                    }

                    if (msg instanceof LastHttpContent) {
                        // Complete the response
                        try {
                            Response response = responseBuilder
                                    .body(responseBody.toString())
                                    .elapsedTimeMs(System.currentTimeMillis() - startTime)
                                    .build();

                            future.complete(response);
                        } catch (Exception e) {
                            completeWithError(ctx, e);
                        } finally {
                            // Always release connection back to pool when done
                            client.connectionPool.releaseConnection(pooledConnection);
                            ctx.close();
                        }
                    }
                }
            } catch (Exception e) {
                completeWithError(ctx, e);
            }
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            completeWithError(ctx, new FastHttpException("Connection error", cause));
        }

        @Override
        public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) {
            if (!future.isDone()) {
                completeWithError(ctx, new FastHttpException("Connection closed by remote host"));
            }
        }

        private void completeWithError(io.netty.channel.ChannelHandlerContext ctx, Exception error) {
            if (!future.isDone()) {
                future.completeExceptionally(error);
            }
            // Always release connection on error
            client.connectionPool.releaseConnection(pooledConnection);
            ctx.close();
        }
    }

    /**
     * Builder for PoolableFastHttpClient
     */
    public static class Builder {
        private ConnectionPoolConfig connectionPoolConfig;

        public Builder connectionPoolConfig(ConnectionPoolConfig config) {
            this.connectionPoolConfig = config;
            return this;
        }

        public ConnectionPoolConfig connectionPoolConfig() {
            return connectionPoolConfig;
        }

        public PoolableFastHttpClient build() {
            return new PoolableFastHttpClient(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a client with default connection pool configuration
     */
    public static PoolableFastHttpClient createDefault() {
        return builder().connectionPoolConfig(ConnectionPoolConfig.defaultConfig()).build();
    }

    /**
     * Create a client with high-performance connection pool configuration
     */
    public static PoolableFastHttpClient createHighPerformance() {
        return builder().connectionPoolConfig(ConnectionPoolConfig.highPerformanceConfig()).build();
    }

    /**
     * Create a client with conservative connection pool configuration
     */
    public static PoolableFastHttpClient createConservative() {
        return builder().connectionPoolConfig(ConnectionPoolConfig.conservativeConfig()).build();
    }
}