package com.evalscope.fasthttp.pool;

import com.evalscope.fasthttp.exception.ConnectionPoolException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages a pool of reusable HTTP connections with advanced configuration options
 */
public class ConnectionPool implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(ConnectionPool.class.getName());

    private final ConnectionPoolConfig config;
    private final EventLoopGroup eventLoopGroup;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalCreated = new AtomicInteger(0);

    // Connection storage
    private final ConcurrentHashMap<String, List<PooledConnection>> availableConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<PooledConnection>> inUseConnections = new ConcurrentHashMap<>();
    private final Set<PooledConnection> allConnections = Collections.synchronizedSet(new HashSet<>());

    // Connection limits and wait management
    private final int maxConnections;
    private final BlockingDeque<PooledConnectionWaitQueue.RequestWrapper> waitQueue = new LinkedBlockingDeque<>();
    private final ScheduledExecutorService cleanupScheduler;
    private final boolean ownsEventLoopGroup;

    private final AtomicBoolean isActive = new AtomicBoolean(true);

    public ConnectionPool(ConnectionPoolConfig config) {
        this(config, null);
    }

    public ConnectionPool(ConnectionPoolConfig config, EventLoopGroup eventLoopGroup) {
        this.config = (config != null) ? config : ConnectionPoolConfig.defaultConfig();
        this.maxConnections = this.config.getMaxConnections();
        this.ownsEventLoopGroup = eventLoopGroup == null;
        this.eventLoopGroup = ownsEventLoopGroup ? new NioEventLoopGroup() : eventLoopGroup;
        this.cleanupScheduler = Executors.newScheduledThreadPool(2);

        // Start background cleanup task
        scheduleIdleConnectionCleanup();
        scheduleConnectionHealthCheck();

        logger.log(Level.INFO, "ConnectionPool initialized with max connections: " + maxConnections);
    }

    /**
     * Acquire a connection for the given host
     */
    public CompletableFuture<PooledConnection> acquireConnection(String host, int port, boolean useSsl) {
        if (!isActive.get()) {
            CompletableFuture<PooledConnection> failed = new CompletableFuture<>();
            failed.completeExceptionally(new ConnectionPoolException("Connection pool is closed"));
            return failed;
        }

        String connectionHost = host + ":" + port;

        return CompletableFuture.supplyAsync(() -> {
            try {
                return acquireConnectionSync(connectionHost, host, port, useSsl);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    private PooledConnection acquireConnectionSync(String connectionHost, String host, int port, boolean useSsl)
            throws ConnectionPoolException, InterruptedException {

        // First, try to get an available connection from pool
        PooledConnection connection = getAvailableConnection(connectionHost);
        if (connection != null && connection.acquire()) {
            logger.log(Level.FINE, "Acquired existing connection " + connection.getConnectionId());
            return connection;
        }

        // If pool is not full and we can create new connection
        if (canCreateNewConnection()) {
            PooledConnection newConnection = createConnection(host, port, useSsl);
            if (newConnection.acquire()) {
                logger.log(Level.INFO, String.format("Created new connection [%s] - Pool: %d/%d active",
                        newConnection.getConnectionId(), activeConnections.get(), maxConnections));
                return newConnection;
            }
        }

        // Handle overflow based on strategy
        return handleConnectionFull(connectionHost, host, port, useSsl);
    }

    /**
     * Try to get an available connection from pool
     */
    private PooledConnection getAvailableConnection(String connectionHost) {
        List<PooledConnection> available = availableConnections.get(connectionHost);
        if (available != null && !available.isEmpty()) {
            synchronized (available) {
                Iterator<PooledConnection> iterator = available.iterator();
                while (iterator.hasNext()) {
                    PooledConnection conn = iterator.next();
                    if (conn.isAvailable() && conn.isValid()) {
                        return conn;
                    } else if (!conn.isValid()) {
                        iterator.remove();
                        removeConnectionFromPool(conn);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check if we can create new connections
     */
    private boolean canCreateNewConnection() {
        return activeConnections.get() < maxConnections;
    }

    /**
     * Handle connection pool full scenario based on configured strategy
     */
    private PooledConnection handleConnectionFull(String connectionHost, String host, int port, boolean useSsl)
            throws ConnectionPoolException, InterruptedException {

        ConnectionPoolConfig.OverflowStrategy strategy = config.getOverflowStrategy();
        long waitTimeoutMs = config.getWaitTimeoutMs();

        switch (strategy) {
            case QUEUE_WAIT:
                return handleQueueWait(connectionHost, host, port, useSsl, waitTimeoutMs);

            case DIRECT_REJECT:
                logger.log(Level.WARNING, String.format("Connection pool full (%d/%d). Direct rejecting request.",
                        activeConnections.get(), maxConnections));
                throw ConnectionPoolException.poolFull(maxConnections);

            case FAIL_FAST:
                logger.log(Level.WARNING, String.format("Connection pool full (%d/%d). Fast failing. Active per host: %s",
                        activeConnections.get(), maxConnections, getActiveConnectionCount(host)));
                throw new ConnectionPoolException(String.format(
                        "Connection pool at capacity (%d/%d). Consider using retry with backoff or increase pool size.",
                        activeConnections.get(), maxConnections));

            default:
                throw new IllegalStateException("Unknown overflow strategy: " + strategy);
        }
    }

    /**
     * Handle queue wait strategy
     */
    private PooledConnection handleQueueWait(String connectionHost, String host, int port, boolean useSsl, long waitTimeoutMs)
            throws ConnectionPoolException, InterruptedException {

        long startTime = System.currentTimeMillis();
        long deadline = startTime + waitTimeoutMs;

        logger.log(Level.INFO, "Connection pool full, queuing request for " + connectionHost);

        while (System.currentTimeMillis() < deadline) {
            long remainingTime = deadline - System.currentTimeMillis();
            if (remainingTime <= 0) break;

            // Try again to get a connection
            PooledConnection connection = getAvailableConnection(connectionHost);
            if (connection != null && connection.acquire()) {
                logger.log(Level.FINE, "Acquired connection after waiting " +
                        (System.currentTimeMillis() - startTime) + " ms");
                return connection;
            }

            // If we can create new connections, create one
            if (canCreateNewConnection()) {
                PooledConnection newConnection = createConnection(host, port, useSsl);
                if (newConnection.acquire()) {
                    logger.log(Level.INFO, String.format("Created new connection [%s] after wait %d ms - Pool: %d/%d active",
                            newConnection.getConnectionId(), (System.currentTimeMillis() - startTime),
                            activeConnections.get(), maxConnections));
                    return newConnection;
                }
            }

            // Wait a bit before trying again (exponential backoff)
            Thread.sleep(Math.min(100, remainingTime / 10));
        }

        throw ConnectionPoolException.connectionTimeout((int) waitTimeoutMs, activeConnections.get(), maxConnections);
    }

    /**
     * Create a new connection
     */
    private PooledConnection createConnection(String host, int port, boolean useSsl) {
        try {
            PooledConnection connection = PooledConnection.builder()
                    .connectionId("conn-" + totalCreated.incrementAndGet() + "-" + System.currentTimeMillis())
                    .host(host)
                    .port(port)
                    .useSsl(useSsl)
                    .eventLoopGroup(eventLoopGroup)
                    .build();

            // Add to internal tracking
            allConnections.add(connection);

            // Add to available connections map
            String connectionHost = host + ":" + port;
            availableConnections.computeIfAbsent(connectionHost, k -> new ArrayList<>()).add(connection);

            activeConnections.incrementAndGet();

            logger.log(Level.FINER, "Created new pooled connection: " + connection);
            return connection;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create connection for " + host + ":" + port, e);
            throw new RuntimeException("Failed to create connection", e);
        }
    }

    /**
     * Release connection back to pool
     */
    public void releaseConnection(PooledConnection connection) {
        if (connection == null || !isActive.get()) {
            return;
        }

        try {
            if (connection.isValid()) {
                connection.release();
                logger.log(Level.FINER, "Released connection: " + connection.getConnectionId());

                // If there are waiting requests, try to fulfill them
                fulfillWaitingRequests();
            } else {
                // Invalid connection, remove from pool
                removeConnectionFromPool(connection);
                logger.log(Level.FINER, "Removed invalid connection: " + connection.getConnectionId());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error releasing connection: " + connection.getConnectionId(), e);
            removeConnectionFromPool(connection);
        }
    }

    /**
     * Get connection count for a specific host
     */
    public int getActiveConnectionCount(String host) {
        String connectionHost = host;
        int count = 0;
        List<PooledConnection> inUseList = inUseConnections.get(connectionHost);
        if (inUseList != null) {
            count = inUseList.size();
        }
        return count;
    }

    /**
     * Get pool statistics
     */
    public ConnectionPoolStats getStats() {
        return new ConnectionPoolStats(
                activeConnections.get(),
                allConnections.size(),
                maxConnections,
                inUseConnections.size()
        );
    }

    /**
     * Schedule idle connection cleanup task
     */
    private void scheduleIdleConnectionCleanup() {
        long cleanupIntervalMs = Math.max(config.getMaxIdleTimeMs() / 4, 5000); // Every 25% of idle time or minimum 5 seconds

        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupIdleConnections();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during idle connection cleanup", e);
            }
        }, cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedule connection health check task
     */
    private void scheduleConnectionHealthCheck() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                checkConnectionHealth();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during connection health check", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Clean up idle connections that have exceeded max idle time
     */
    private void cleanupIdleConnections() {
        long currentTime = System.currentTimeMillis();
        long maxIdleTimeMs = config.getMaxIdleTimeMs();

        for (Map.Entry<String, List<PooledConnection>> entry : availableConnections.entrySet()) {
            List<PooledConnection> connectionList = entry.getValue();
            synchronized (connectionList) {
                Iterator<PooledConnection> iterator = connectionList.iterator();
                while (iterator.hasNext()) {
                    PooledConnection connection = iterator.next();
                    if (connection.isIdleTimeout(maxIdleTimeMs) && connection.isAvailable()) {
                        logger.log(Level.FINER, "Cleaning up idle connection: " + connection);
                        iterator.remove();
                        connection.close();
                        removeConnectionFromPool(connection);
                    }
                }
            }
        }
    }

    /**
     * Check health of all connections and clean up invalid ones
     */
    private void checkConnectionHealth() {
        Iterator<PooledConnection> iterator = allConnections.iterator();
        while (iterator.hasNext()) {
            PooledConnection connection = iterator.next();
            if (!connection.isValid()) {
                logger.log(Level.FINER, "Removing invalid connection: " + connection);
                iterator.remove();
                removeConnectionFromPool(connection);
                connection.close();
            }
        }
    }

    /**
     * Try to fulfill waiting requests
     */
    private void fulfillWaitingRequests() {
        // Simple implementation - in a more sophisticated version,
        // we would have proper notification mechanisms
        PooledConnectionWaitQueue.RequestWrapper waitingRequest = waitQueue.poll();
        if (waitingRequest != null) {
            try {
                PooledConnection connection = acquireConnectionSync(
                        waitingRequest.connectionHost,
                        waitingRequest.host,
                        waitingRequest.port,
                        waitingRequest.useSsl
                );
                waitingRequest.complete(connection);
            } catch (Exception e) {
                waitingRequest.fail(e);
            }
        }
    }

    /**
     * Remove connection from all tracking maps
     */
    private void removeConnectionFromPool(PooledConnection connection) {
        String connectionHost = connection.getHost() + ":" + connection.getPort();

        // Remove from available connections
        List<PooledConnection> availableList = availableConnections.get(connectionHost);
        if (availableList != null) {
            synchronized (availableList) {
                availableList.remove(connection);
            }
        }

        // Remove from in-use connections
        List<PooledConnection> inUseList = inUseConnections.get(connectionHost);
        if (inUseList != null) {
            synchronized (inUseList) {
                inUseList.remove(connection);
            }
        }

        // Remove from all connections tracking
        allConnections.remove(connection);

        // Decrement active connections count
        activeConnections.decrementAndGet();
    }

    /**
     * Close the connection pool
     */
    @Override
    public void close() {
        if (isActive.compareAndSet(true, false)) {
            try {
                cleanupScheduler.shutdown();
                cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                cleanupScheduler.shutdownNow();
            }

            // Close all connections
            for (PooledConnection connection : allConnections) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Ignore errors during shutdown
                }
            }

            if (ownsEventLoopGroup) {
                eventLoopGroup.shutdownGracefully();
            }

            availableConnections.clear();
            inUseConnections.clear();
            allConnections.clear();
            activeConnections.set(0);
        }
    }

    /**
     * Get wait queue size
     */
    public int getWaitQueueSize() {
        return waitQueue.size();
    }

    /**
     * Connection pool statistics
     */
    public static class ConnectionPoolStats {
        private final int activeConnections;
        private final int totalConnections;
        private final int maxConnections;
        private final int uniqueHosts;

        public ConnectionPoolStats(int activeConnections, int totalConnections, int maxConnections, int uniqueHosts) {
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.maxConnections = maxConnections;
            this.uniqueHosts = uniqueHosts;
        }

        public int getActiveConnections() {
            return activeConnections;
        }

        public int getTotalConnections() {
            return totalConnections;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public int getUniqueHosts() {
            return uniqueHosts;
        }

        public double getUsagePercentage() {
            return totalConnections * 100.0 / maxConnections;
        }

        @Override
        public String toString() {
            return String.format("ConnectionPoolStats{active=%d, total=%d, max=%d, hosts=%d, usage=%.1f%%}",
                    activeConnections, totalConnections, maxConnections, uniqueHosts, getUsagePercentage());
        }
    }
}