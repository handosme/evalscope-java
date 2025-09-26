package com.evalscope.fasthttp.client;

import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import com.evalscope.fasthttp.exception.FastHttpException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refactored FastHttpClient to fix exception handling issues.
 */
public class FastHttpClientRefactored implements AutoCloseable {
    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private FastHttpClientRefactored(Builder builder) {
        this.eventLoopGroup = builder.eventLoopGroup;
        this.executor = builder.executor;

        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
    }

    public CompletableFuture<Response> execute(Request request) {
        if (closed.get()) {
            CompletableFuture<Response> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Client is closed"));
            return failed;
        }

        CompletableFuture<Response> future = new CompletableFuture<>();

        try {
            URI uri = new URI(request.url());
            boolean isHttps = "https".equals(uri.getScheme());

            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();

                    // Add SSL handler if HTTPS
                    if (isHttps) {
                        SslContext sslContext = createSslContext();
                        pipeline.addLast("ssl", sslContext.newHandler(ch.alloc(), uri.getHost(), getPort(uri)));
                    }

                    // Add timeout handlers
                    pipeline.addLast("readTimeout", new ReadTimeoutHandler(request.timeoutMillis() / 1000));
                    pipeline.addLast("writeTimeout", new WriteTimeoutHandler(request.timeoutMillis() / 1000));

                    // Add HTTP codec
                    pipeline.addLast("httpCodec", new HttpClientCodec());

                    // Add response handler
                    pipeline.addLast("responseHandler", new HttpResponseHandler(request, future));
                }
            });

            String host = uri.getHost();
            int port = getPort(uri);

            ChannelFuture connectFuture = bootstrap.connect(host, port);

            connectFuture.addListener((ChannelFutureListener) cf -> {
                if (!cf.isSuccess()) {
                    future.completeExceptionally(
                        new FastHttpException("Failed to connect to " + host + ":" + port, cf.cause()));
                }
            });

        } catch (URISyntaxException e) {
            future.completeExceptionally(new FastHttpException("Invalid URL: " + request.url(), e));
        } catch (Exception e) {
            future.completeExceptionally(new FastHttpException("HTTP execution failed", e));
        }

        return future;
    }

    private SslContext createSslContext() throws Exception {
        try {
            return SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (SSLException e) {
            throw new RuntimeException("SSL initialization failed", e);
        }
    }

    private int getPort(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            if ("https".equals(uri.getScheme())) {
                return 443;
            } else if ("http".equals(uri.getScheme())) {
                return 80;
            }
        }
        return port;
    }

    @Override
    public void close() throws Exception {
        if (closed.compareAndSet(false, true)) {
            try {
                Future<?> groupFuture = eventLoopGroup.shutdownGracefully();
                executor.shutdown();

                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // Log and continue closing
                System.err.println("Error closing FastHttpClient: " + e.getMessage());
            }
        }
    }

    public static class Builder {
        private EventLoopGroup eventLoopGroup;
        private ExecutorService executor;

        public Builder() {}

        public Builder eventLoopGroup(EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public FastHttpClientRefactored build() {
            if (eventLoopGroup == null) {
                eventLoopGroup = new NioEventLoopGroup();
            }
            if (executor == null) {
                executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            }
            return new FastHttpClientRefactored(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}