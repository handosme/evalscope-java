package com.evalscope.fasthttp.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Pooled connection that wraps a Netty channel with connection management features
 */
public class PooledConnection {
    private static final Logger logger = Logger.getLogger(PooledConnection.class.getName());

    private final String connectionId;
    private final String host;
    private final int port;
    private final Channel channel;
    private final boolean isHttps;
    private AtomicBoolean isAvailable = new AtomicBoolean(true);
    private AtomicBoolean isValid = new AtomicBoolean(true);
    private final long createdTime;
    private final AtomicLong lastUsedTime;
    private final AtomicLong useCount = new AtomicLong(0);

    /**
     * Connection states
     */
    public enum ConnectionState {
        AVAILABLE,     // Ready to be used
        IN_USE,        // Currently in use
        INVALID,       // Connection is broken or timed out
        CLOSED         // Connection has been closed
    }

    private volatile ConnectionState state = ConnectionState.AVAILABLE;

    private PooledConnection(Builder builder) throws Exception {
        this.connectionId = builder.connectionId;
        this.host = builder.host;
        this.port = builder.port;
        this.isHttps = builder.useSsl;
        this.createdTime = System.currentTimeMillis();
        this.lastUsedTime = new AtomicLong(createdTime);

        this.channel = createChannel(builder.eventLoopGroup);
    }

    /**
     * Create a new channel for this connection
     */
    private Channel createChannel(EventLoopGroup eventLoopGroup) throws Exception {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                .handler(new io.netty.channel.ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        io.netty.channel.ChannelPipeline pipeline = ch.pipeline();

                        // Add SSL handler if HTTPS
                        if (isHttps) {
                            SslContext sslContext = createSslContext();
                            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc(), host, port));
                        }

                        // Add HTTP codec
                        pipeline.addLast("httpCodec", new HttpClientCodec());
                    }
                });

        ChannelFuture connectFuture = bootstrap.connect(host, port);

        // Wait for connection to complete (synchronously during creation)
        connectFuture.sync();

        if (!connectFuture.isSuccess()) {
            throw new RuntimeException("Failed to establish connection to " + host + ":" + port, connectFuture.cause());
        }

        Channel channel = connectFuture.channel();

        // Add close listener to detect when connection is closed
        channel.closeFuture().addListener((ChannelFutureListener) future -> {
            logger.log(Level.FINE, "Connection " + connectionId + " closed: " + (future.cause() != null ? future.cause().getMessage() : "Normal close"));
            isValid.set(false);
            setState(ConnectionState.CLOSED);
        });

        return channel;
    }

    private SslContext createSslContext() throws SSLException {
        return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    /**
     * Check if connection is available for use
     */
    public boolean isAvailable() {
        return isAvailable.get() && isValid.get() && state == ConnectionState.AVAILABLE;
    }

    /**
     * Check if connection is valid (not closed, not broken)
     */
    public boolean isValid() {
        if (!isValid.get() || state == ConnectionState.CLOSED) {
            return false;
        }
        if (channel == null || !channel.isActive()) {
            isValid.set(false);
            setState(ConnectionState.INVALID);
            return false;
        }
        return true;
    }

    /**
     * Acquire connection for use
     */
    public boolean acquire() {
        if (isAvailable.compareAndSet(true, false)) {
            setState(ConnectionState.IN_USE);
            useCount.incrementAndGet();
            lastUsedTime.set(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    /**
     * Release connection back to pool
     */
    public void release() {
        if (isAvailable.compareAndSet(false, true)) {
            setState(ConnectionState.AVAILABLE);
            lastUsedTime.set(System.currentTimeMillis());
        }
    }

    /**
     * Check if connection has been idle for too long
     */
    public boolean isIdleTimeout(long maxIdleTimeMs) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - getLastUsedTime()) > maxIdleTimeMs;
    }

    /**
     * Mark connection as invalid and close it
     */
    public void invalidate() {
        isValid.set(false);
        setState(ConnectionState.INVALID);
        close();
    }

    /**
     * Close the connection
     */
    public void close() {
        setState(ConnectionState.CLOSED);
        if (channel != null \u0026\u0026 channel.isOpen()) {
            channel.close();
        }
    }

    private synchronized void setState(ConnectionState newState) {
        this.state = newState;
    }

    public synchronized ConnectionState getState() {
        return state;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getLastUsedTime() {
        return lastUsedTime.get();
    }

    public long getUseCount() {
        return useCount.get();
    }

    public boolean isHttps() {
        return isHttps;
    }

    @Override
    public String toString() {
        return String.format("PooledConnection[%s: %s:%d - state: %s, idle: %d ms, used: %d times]",
                connectionId, host, port, state, System.currentTimeMillis() - getLastUsedTime(), getUseCount());
    }

    /**
     * Builder class for PooledConnection
     */
    public static class Builder {
        private String connectionId;
        private String host;
        private int port;
        private boolean useSsl;
        private EventLoopGroup eventLoopGroup;

        public Builder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder useSsl(boolean useSsl) {
            this.useSsl = useSsl;
            return this;
        }

        public Builder eventLoopGroup(EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public PooledConnection build() throws Exception {
            // Validate required parameters
            if (connectionId == null || connectionId.isEmpty()) {
                connectionId = "conn-" + host + "-" + port + "-" + System.currentTimeMillis();
            }
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Host is required");
            }
            if (port <= 0) {
                port = useSsl ? 443 : 80;
            }
            if (eventLoopGroup == null) {
                eventLoopGroup = new NioEventLoopGroup();
            }

            return new PooledConnection(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}