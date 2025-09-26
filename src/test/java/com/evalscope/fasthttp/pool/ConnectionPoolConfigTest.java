package com.evalscope.fasthttp.pool;

import com.evalscope.fasthttp.exception.ConnectionPoolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionPoolConfigTest {

    @Test
    @DisplayName("Test default configuration values")
    public void testDefaultConfiguration() {
        ConnectionPoolConfig config = ConnectionPoolConfig.defaultConfig();

        assertEquals(ConnectionPoolConfig.DEFAULT_MAX_CONNECTIONS, config.getMaxConnections());
        assertEquals(ConnectionPoolConfig.DEFAULT_MAX_IDLE_TIME_MS, config.getMaxIdleTimeMs());
        assertEquals(ConnectionPoolConfig.DEFAULT_WAIT_TIMEOUT_MS, config.getWaitTimeoutMs());
        assertEquals(ConnectionPoolConfig.DEFAULT_OVERFLOW_STRATEGY, config.getOverflowStrategy());
        assertTrue(config.isEnableConnectionReuse());
    }

    @Test
    @DisplayName("Test builder with custom values")
    public void testCustomConfiguration() {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(100)
                .maxIdleTime(60000)
                .waitTimeout(5000)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT)
                .enableConnectionReuse(false)
                .maxConnectionsPerHost(20)
                .build();

        assertEquals(100, config.getMaxConnections());
        assertEquals(60000, config.getMaxIdleTimeMs());
        assertEquals(5000, config.getWaitTimeoutMs());
        assertEquals(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT, config.getOverflowStrategy());
        assertFalse(config.isEnableConnectionReuse());
        assertEquals(20, config.getMaxConnectionsPerHost());
    }

    @Test
    @DisplayName("Test builder with null values should use defaults")
    public void testDefaultValuesWhenNotSpecified() {
        // Values that should be auto-generated
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(50)
                .build();

        assertEquals(50, config.getMaxConnections());
        assertEquals(ConnectionPoolConfig.DEFAULT_MAX_IDLE_TIME_MS, config.getMaxIdleTimeMs());
        assertEquals(ConnectionPoolConfig.DEFAULT_WAIT_TIMEOUT_MS, config.getWaitTimeoutMs());
    }

    @Test
    @DisplayName("Test high performance configuration preset")
    public void testHighPerformanceConfig() {
        ConnectionPoolConfig config = ConnectionPoolConfig.highPerformanceConfig();

        assertTrue(config.getMaxConnections() >= 500);
        assertTrue(config.getMaxConnectionsPerHost() >= 100);
        assertTrue(config.getMaxIdleTimeMs() <= 20000);
        assertTrue(config.getWaitTimeoutMs() <= 10000);
    }

    @Test
    @DisplayName("Test conservative configuration preset")
    public void testConservativeConfig() {
        ConnectionPoolConfig config = ConnectionPoolConfig.conservativeConfig();

        assertEquals(50, config.getMaxConnections());
        assertEquals(60000, config.getMaxIdleTimeMs());
        assertEquals(30000, config.getWaitTimeoutMs());
        assertEquals(10, config.getMaxConnectionsPerHost());
        assertEquals(ConnectionPoolConfig.OverflowStrategy.DIRECT_REJECT, config.getOverflowStrategy());
        assertTrue(config.isEnableConnectionReuse());
    }

    @Test
    @DisplayName("Test builder validation for invalid parameters")
    public void testBuilderValidation() {
        // Test maxConnections validation
        assertThrows(IllegalArgumentException.class, () -> {
            ConnectionPoolConfig.builder().maxConnections(0).build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ConnectionPoolConfig.builder().maxConnections(-1).build();
        });

        // Test maxIdleTime validation
        assertThrows(IllegalArgumentException.class, () -> {
            ConnectionPoolConfig.builder().maxIdleTime(999).build();
        });

        // Test waitTimeout validation
        assertThrows(IllegalArgumentException.class, () -> {
            ConnectionPoolConfig.builder().waitTimeout(-1).build();
        });

        // Test maxConnectionsPerHost validation
        assertThrows(IllegalArgumentException.class, () -> {
            ConnectionPoolConfig.builder().maxConnectionsPerHost(0).build();
        });

        // Test maxConnectionsPerHost cannot exceed maxConnections
        assertThrows(IllegalArgumentException.class, () -> {
            ConnectionPoolConfig.builder()
                    .maxConnections(10)
                    .maxConnectionsPerHost(15)
                    .build();
        });
    }

    @Test
    @DisplayName("Test time unit conversion in builder")
    public void testTimeUnitConversion() {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .waitTimeout(10, TimeUnit.SECONDS)
                .build();

        assertEquals(10000, config.getWaitTimeoutMs()); // 10 seconds -> 10000 ms
    }

    @Test
    @DisplayName("Test max connections per host cannot exceed total max")
    public void testMaxConnectionsPerHostValidation() {
        // Valid configuration
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(100)
                .maxConnectionsPerHost(50)
                .build();

        assertEquals(100, config.getMaxConnections());
        assertEquals(50, config.getMaxConnectionsPerHost());
    }

    @ParameterizedTest
    @DisplayName("Test various overflow strategies via parameterized test")
    @CsvSource({
            "QUEUE_WAIT",
            "DIRECT_REJECT",
            "FAIL_FAST"
    })
    public void testOverflowStrategies(ConnectionPoolConfig.OverflowStrategy strategy) {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .overflowStrategy(strategy)
                .build();

        assertEquals(strategy, config.getOverflowStrategy());
    }

    @Test
    @DisplayName("Test configuration equality")
    public void testConfigurationEquality() {
        ConnectionPoolConfig config1 = ConnectionPoolConfig.builder()
                .maxConnections(50)
                .maxIdleTime(10000)
                .waitTimeout(5000)
                .build();

        ConnectionPoolConfig config2 = ConnectionPoolConfig.builder()
                .maxConnections(50)
                .maxIdleTime(10000)
                .waitTimeout(5000)
                .build();

        ConnectionPoolConfig config3 = ConnectionPoolConfig.builder()
                .maxConnections(60)
                .maxIdleTime(10000)
                .waitTimeout(5000)
                .build();

        // Test equals
        assertEquals(config1.getMaxConnections(), config2.getMaxConnections());
        assertEquals(config1.getMaxIdleTimeMs(), config2.getMaxIdleTimeMs());
        assertEquals(config1.getWaitTimeoutMs(), config2.getWaitTimeoutMs());

        assertNotEquals(config1.getMaxConnections(), config3.getMaxConnections());
    }

    @Test
    @DisplayName("Test toString method produces meaningful output")
    public void testToString() {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(50)
                .maxIdleTime(30000)
                .waitTimeout(10000)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT)
                .build();

        String toString = config.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("maxConnections=50"));
        assertTrue(toString.contains("maxIdleTimeMs=30000"));
        assertTrue(toString.contains("waitTimeoutMs=10000"));
        assertTrue(toString.contains("overflowStrategy=QUEUE_WAIT"));
    }

    @Test
    @DisplayName("Test creation without using builder")
    public void testDirectCreationWithDefaults() {
        // This tests using the default factory methods
        ConnectionPoolConfig defaultConfig = ConnectionPoolConfig.defaultConfig();
        assertNotNull(defaultConfig);
        assertEquals(ConnectionPoolConfig.DEFAULT_MAX_CONNECTIONS, defaultConfig.getMaxConnections());
        assertTrue(defaultConfig.isEnableConnectionReuse());

        ConnectionPoolConfig hpConfig = ConnectionPoolConfig.highPerformanceConfig();
        assertNotNull(hpConfig);
        assertTrue(hpConfig.getMaxConnections() >= 500);

        ConnectionPoolConfig conservativeConfig = ConnectionPoolConfig.conservativeConfig();
        assertNotNull(conservativeConfig);
        assertEquals(ConnectionPoolConfig.OverflowStrategy.DIRECT_REJECT, conservativeConfig.getOverflowStrategy());
    }

    @Test
    @DisplayName("Test configuration immutability")
    public void testConfigurationImmutability() {
        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(100)
                .build();

        // All getter values should be fixed after construction
        assertEquals(100, config.getMaxConnections());
        assertEquals(ConnectionPoolConfig.DEFAULT_MAX_IDLE_TIME_MS, config.getMaxIdleTimeMs());

        // Verify these values don't change on subsequent calls
        for (int i = 0; i < 3; i++) {
            assertEquals(100, config.getMaxConnections());
            assertEquals(ConnectionPoolConfig.DEFAULT_MAX_IDLE_TIME_MS, config.getMaxIdleTimeMs());
        }
    }

    @Test
    @DisplayName("Test overflow strategies enum values")
    public void testOverflowStrategiesEnum() {
        assertNotNull(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT);
        assertNotNull(ConnectionPoolConfig.OverflowStrategy.DIRECT_REJECT);
        assertNotNull(ConnectionPoolConfig.OverflowStrategy.FAIL_FAST);

        // Ensure all values are unique
        ConnectionPoolConfig.OverflowStrategy[] strategies = ConnectionPoolConfig.OverflowStrategy.values();
        assertEquals(3, strategies.length);
    }

    @Test
    @DisplayName("Test enable connection reuse configuration")
    public void testEnableConnectionReuse() {
        // Test enabling
        ConnectionPoolConfig configEnabled = ConnectionPoolConfig.builder()
                .enableConnectionReuse(true)
                .build();
        assertTrue(configEnabled.isEnableConnectionReuse());

        // Test disabling
        ConnectionPoolConfig configDisabled = ConnectionPoolConfig.builder()
                .enableConnectionReuse(false)
                .build();
        assertFalse(configDisabled.isEnableConnectionReuse());
    }

    @Test
    @DisplayName("Test connection configuration with edge values")
    public void testEdgeValues() {
        // Test minimal valid values
        ConnectionPoolConfig minConfig = ConnectionPoolConfig.builder()
                .maxConnections(1)
                .maxIdleTime(1000)
                .waitTimeout(0)
                .maxConnectionsPerHost(1)
                .build();

        assertEquals(1, minConfig.getMaxConnections());
        assertEquals(1000, minConfig.getMaxIdleTimeMs());
        assertEquals(0, minConfig.getWaitTimeoutMs());
        assertEquals(1, minConfig.getMaxConnectionsPerHost());

        // Test large values
        ConnectionPoolConfig largeConfig = ConnectionPoolConfig.builder()
                .maxConnections(10000)
                .maxIdleTime(3600000) // 1 hour
                .waitTimeout(60000) // 1 minute
                .build();

        assertEquals(10000, largeConfig.getMaxConnections());
        assertEquals(3600000, largeConfig.getMaxIdleTimeMs());
        assertEquals(60000, largeConfig.getWaitTimeoutMs());
    }
}