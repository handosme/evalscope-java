package com.evalscope.fasthttp.pool;

import java.util.concurrent.TimeUnit;

/**
 * Connection pool configuration for FastHttpClient
 */
public class ConnectionPoolConfig {
    public static final int DEFAULT_MAX_CONNECTIONS = 200;
    public static final int DEFAULT_MAX_IDLE_TIME_MS = 30000; // 30 seconds
    public static final int DEFAULT_WAIT_TIMEOUT_MS = 10000; // 10 seconds
    public static final OverflowStrategy DEFAULT_OVERFLOW_STRATEGY = OverflowStrategy.QUEUE_WAIT;

    private final int maxConnections;
    private final long maxIdleTimeMs;
    private final long waitTimeoutMs;
    private final OverflowStrategy overflowStrategy;
    private final boolean enableConnectionReuse;
    private final int maxConnectionsPerHost;

    private ConnectionPoolConfig(Builder builder) {
        this.maxConnections = builder.maxConnections;
        this.maxIdleTimeMs = builder.maxIdleTimeMs;
        this.waitTimeoutMs = builder.waitTimeoutMs;
        this.overflowStrategy = builder.overflowStrategy;
        this.enableConnectionReuse = builder.enableConnectionReuse;
        this.maxConnectionsPerHost = builder.maxConnectionsPerHost;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public long getMaxIdleTimeMs() {
        return maxIdleTimeMs;
    }

    public long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public OverflowStrategy getOverflowStrategy() {
        return overflowStrategy;
    }

    public boolean isEnableConnectionReuse() {
        return enableConnectionReuse;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public enum OverflowStrategy {
        QUEUE_WAIT,  // Queue requests when pool is full, wait for available connection
        DIRECT_REJECT, // Reject requests immediately when pool is full
        FAIL_FAST    // Fast failure with retry suggestion
    }

    public static class Builder {
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private long maxIdleTimeMs = DEFAULT_MAX_IDLE_TIME_MS;
        private long waitTimeoutMs = DEFAULT_WAIT_TIMEOUT_MS;
        private OverflowStrategy overflowStrategy = DEFAULT_OVERFLOW_STRATEGY;
        private boolean enableConnectionReuse = true;
        private int maxConnectionsPerHost = 50;

        /**
         * Maximum number of connections in the pool
         */
        public Builder maxConnections(int maxConnections) {
            if (maxConnections < 1) {
                throw new IllegalArgumentException("maxConnections must be >= 1");
            }
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * Maximum time a connection can be idle before being closed
         */
        public Builder maxIdleTime(long maxIdleTimeMs) {
            if (maxIdleTimeMs < 1000) {
                throw new IllegalArgumentException("maxIdleTime must be >= 1000 ms");
            }
            this.maxIdleTimeMs = maxIdleTimeMs;
            return this;
        }

        /**
         * Maximum time to wait for an available connection when pool is full
         */
        public Builder waitTimeout(long waitTimeoutMs) {
            if (waitTimeoutMs < 0) {
                throw new IllegalArgumentException("waitTimeout must be >= 0");
            }
            this.waitTimeoutMs = waitTimeoutMs;
            return this;
        }

        public Builder waitTimeout(long timeout, TimeUnit unit) {
            return waitTimeout(unit.toMillis(timeout));
        }

        /**
         * Strategy to handle requests when connection pool is full
         */
        public Builder overflowStrategy(OverflowStrategy strategy) {
            this.overflowStrategy = strategy;
            return this;
        }

        /**
         * Enable connection reuse (keep-alive)
         */
        public Builder enableConnectionReuse(boolean enable) {
            this.enableConnectionReuse = enable;
            return this;
        }

        /**
         * Maximum connections per host
         */
        public Builder maxConnectionsPerHost(int maxConnectionsPerHost) {
            if (maxConnectionsPerHost < 1) {
                throw new IllegalArgumentException("maxConnectionsPerHost must be >= 1");
            }
            this.maxConnectionsPerHost = maxConnectionsPerHost;
            return this;
        }

        public ConnectionPoolConfig build() {
            validateConfiguration();
            return new ConnectionPoolConfig(this);
        }

        private void validateConfiguration() {
            if (maxConnectionsPerHost > maxConnections) {
                throw new IllegalArgumentException("maxConnectionsPerHost cannot be greater than maxConnections");
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a connection pool configuration with reasonable defaults
     */
    public static ConnectionPoolConfig defaultConfig() {
        return builder().build();
    }

    /**
     * Create aggressive connection pool configuration for high-load scenarios
     */
    public static ConnectionPoolConfig highPerformanceConfig() {
        return builder()
                .maxConnections(500)
                .maxIdleTime(10000)
                .waitTimeout(5000)
                .maxConnectionsPerHost(100)
                .build();
    }

    /**
     * Create conservative connection pool configuration for resource-constrained environments
     */
    public static ConnectionPoolConfig conservativeConfig() {
        return builder()
                .maxConnections(50)
                .maxIdleTime(60000)
                .waitTimeout(30000)
                .maxConnectionsPerHost(10)
                .overflowStrategy(OverflowStrategy.DIRECT_REJECT)
                .build();
    }
}