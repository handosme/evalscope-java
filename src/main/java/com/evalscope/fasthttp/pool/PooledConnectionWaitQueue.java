package com.evalscope.fasthttp.pool;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a waiting request for a pooled connection
 */
class PooledConnectionWaitQueue {

    static class RequestWrapper {
        final String connectionHost;
        final String host;
        final int port;
        final boolean useSsl;
        final CompletableFuture<PooledConnection> future;
        final long createTime;

        RequestWrapper(String connectionHost, String host, int port, boolean useSsl) {
            this.connectionHost = connectionHost;
            this.host = host;
            this.port = port;
            this.useSsl = useSsl;
            this.future = new CompletableFuture<>();
            this.createTime = System.currentTimeMillis();
        }

        void complete(PooledConnection connection) {
            future.complete(connection);
        }

        void fail(Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        long getWaitTime() {
            return System.currentTimeMillis() - createTime;
        }

        @Override
        public String toString() {
            return String.format("Request[host=%s:%d, ssl=%b, wait=%d ms]",
                    host, port, useSsl, getWaitTime());
        }
    }
}