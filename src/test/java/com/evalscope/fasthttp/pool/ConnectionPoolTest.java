package com.evalscope.fasthttp.pool;

import com.evalscope.fasthttp.exception.ConnectionPoolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class ConnectionPoolTest {

    private ConnectionPool connectionPool;
    private ConnectionPoolConfig defaultConfig;

    @BeforeEach
    public void setUp() {
        defaultConfig = ConnectionPoolConfig.defaultConfig();
        connectionPool = new ConnectionPool(defaultConfig);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connectionPool != null) {
            connectionPool.close();
        }
    }

    @Test
    @DisplayName("Test basic connection acquisition")
    public void testBasicConnectionAcquisition() throws Exception {
        CompletableFuture<PooledConnection> future = connectionPool.acquireConnection("example.com", 80, false);
        PooledConnection connection = future.get(5, TimeUnit.SECONDS);

        assertNotNull(connection);
        assertEquals("example.com", connection.getHost());
        assertEquals(80, connection.getPort());
        assertFalse(connection.isHttps());
        assertEquals(PooledConnection.ConnectionState.IN_USE, connection.getState());

        // Release connection
        connectionPool.releaseConnection(connection);
        assertEquals(PooledConnection.ConnectionState.AVAILABLE, connection.getState());
    }

    @Test
    @DisplayName("Test connection pool statistics")
    public void testPoolStatistics() throws Exception {
        ConnectionPool.ConnectionPoolStats stats = connectionPool.getStats();

        assertEquals(0, stats.getActiveConnections());
        assertEquals(0, stats.getTotalConnections());
        assertTrue(stats.getMaxConnections() > 0);
        assertEquals(0, stats.getUniqueHosts());
        assertEquals(0.0, stats.getUsagePercentage(), 0.01);

        // Acquire a connection
        PooledConnection connection = connectionPool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);

        stats = connectionPool.getStats();
        assertEquals(1, stats.getActiveConnections());
        assertEquals(1, stats.getTotalConnections());
        assertEquals(1, stats.getUniqueHosts());
        assertEquals(100.0 / defaultConfig.getMaxConnections(), stats.getUsagePercentage(), 1.0);

        connectionPool.releaseConnection(connection);
    }

    @Test
    @DisplayName("Test connection timeout due to pool full")
    public void testConnectionTimeoutPoolFull() throws Exception {
        // Create pool with very low limit
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(1)
                .waitTimeout(1000) // 1 second timeout
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT)
                .build();

        try (ConnectionPool smallPool = new ConnectionPool(config)) {
            // Acquire the only available connection
            PooledConnection connection = smallPool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);

            try {
                // Try to acquire another connection - should timeout
                CompletableFuture<PooledConnection> timeoutFuture = smallPool.acquireConnection("test.com", 80, false);

                assertThrows(TimeoutException.class, () -> {
                    timeoutFuture.get(2, TimeUnit.SECONDS);
                });

                assertTrue(timeoutFuture.isCompletedExceptionally());

                // Release original connection
                smallPool.releaseConnection(connection);
            } catch (TimeoutException e) {
                fail("Connection acquisition should have completed within timeout");
            }
        }
    }

    @ParameterizedTest
    @DisplayName("Test different overflow strategies")
    @EnumSource(ConnectionPoolConfig.OverflowStrategy.class)
    public void testOverflowStrategies(ConnectionPoolConfig.OverflowStrategy strategy) throws Exception {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(2)
                .overflowStrategy(strategy)
                .waitTimeout(500)
                .build();

        try (ConnectionPool pool = new ConnectionPool(config)) {
            // Fill all available connections
            pool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);
            pool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);

            // Try to acquire third connection when pool is full
            CompletableFuture<PooledConnection> future = pool.acquireConnection("test.com", 80, false);

            switch (strategy) {
                case QUEUE_WAIT:
                    // This should timeout based on waitTimeout
                    assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS));
                    break;
                case DIRECT_REJECT:
                    // Should throw exception immediately
                    ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
                    assertTrue(e.getCause() instanceof ConnectionPoolException);
                    assertTrue(e.getCause().getMessage().contains("Connection pool is full"));
                    break;
                case FAIL_FAST:
                    // Should throw exception with specific message
                    ExecutionException e2 = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
                    assertTrue(e2.getCause() instanceof ConnectionPoolException);
                    assertTrue(e2.getCause().getMessage().contains("at capacity"));
                    break;
            }
        }
    }

    @Test
    @DisplayName("Test connection use count increment")
    public void testConnectionUseCount() throws Exception {
        PooledConnection connection = connectionPool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);

        long useCount1 = connection.getUseCount();
        connectionPool.releaseConnection(connection);

        // Acquire and release again
        connection = connectionPool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);
        long useCount2 = connection.getUseCount();
        connectionPool.releaseConnection(connection);

        assertTrue(useCount2 > useCount1);
    }

    @Test
    @DisplayName("Test idle timeout functionality")
    public void testIdleTimeout() throws Exception {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxIdleTime(1000) // 1 second idle timeout
                .build();

        try (ConnectionPool pool = new ConnectionPool(config)) {
            // Acquire and release a connection
            PooledConnection connection = pool.acquireConnection("test.com", 80, false).get(5, TimeUnit.SECONDS);
            pool.releaseConnection(connection);

            // Wait for idle timeout
            TimeUnit.SECONDS.sleep(2);

            // Connection should be expired
            assertTrue(connection.isIdleTimeout(1000));
        }
    }

    @Test
    @DisplayName("Test connection close when pool is closed")
    public void testPoolCloseReleasesAllConnections() throws Exception {
        PooledConnection connection1 = null;
        PooledConnection connection2 = null;

        try (ConnectionPool pool = new ConnectionPool(defaultConfig)) {
            // Acquire multiple connections
            connection1 = pool.acquireConnection("test1.com", 80, false).get(5, TimeUnit.SECONDS);
            connection2 = pool.acquireConnection("test2.com", 80, false).get(5, TimeUnit.SECONDS);

            assertEquals(2, pool.getStats().getActiveConnections());
        }
        // pool automatically closed by try-with-resources

        // Connections should be closed after pool close
        assertFalse(connection1.isValid());
        assertFalse(connection2.isValid());

        assertEquals(PooledConnection.ConnectionState.CLOSED, connection1.getState());
        assertEquals(PooledConnection.ConnectionState.CLOSED, connection2.getState());
    }
}
