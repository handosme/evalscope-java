package com.evalscope.fasthttp.exception;

/**
 * Exception thrown when connection pool operations fail
 */
public class ConnectionPoolException extends FastHttpException {

    public ConnectionPoolException(String message) {
        super(message);
    }

    public ConnectionPoolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionPoolException(Throwable cause) {
        super(cause);
    }

    /**
     * Create exception for connection pool full
     */
    public static ConnectionPoolException poolFull(int maxConnections) {
        return new ConnectionPoolException("Connection pool is full. Max connections: " + maxConnections);
    }

    /**
     * Create exception for connection unavailable
     */
    public static ConnectionPoolException connectionUnavailable(String host) {
        return new ConnectionPoolException("No available connection for host: " + host);
    }

    /**
     * Create exception for connection timeout
     */
    public static ConnectionPoolException connectionTimeout(int waitTimeoutMs, int activeConnections, int maxConnections) {
        return new ConnectionPoolException(String.format(
            "Connection timeout after %d ms. Active: %d/%d connections",
            waitTimeoutMs, activeConnections, maxConnections
        ));
    }
}