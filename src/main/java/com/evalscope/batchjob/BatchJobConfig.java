package com.evalscope.batchjob;

/**
 * BatchJob的配置类，用于设置批处理的各种参数
 */
public class BatchJobConfig {
    private String apiEndpoint;
    private String apiKey;
    private int maxConcurrentRequests;
    private int connectionTimeout;
    private int requestTimeout;
    private int maxRetries;
    private int threadPoolSize;
    private int maxBatchSize;
    private boolean enableCompression;
    private int maxConnectionsPerHost;
    private int batchExecutionTimeout;

    /**
     * 创建一个默认配置的BatchJobConfig
     */
    public BatchJobConfig() {
        this.maxConcurrentRequests = 100;
        this.connectionTimeout = 5000;
        this.requestTimeout = 30000;
        this.maxRetries = 3;
        this.threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        this.maxBatchSize = 50;
        this.enableCompression = true;
        this.maxConnectionsPerHost = 200;
        this.batchExecutionTimeout = 60000; // 60 seconds default timeout
    }

    /**
     * 创建一个新的配置构建器
     * 
     * @return 配置构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * BatchJobConfig的构建器类
     */
    public static class Builder {
        private BatchJobConfig config;

        public Builder() {
            config = new BatchJobConfig();
        }

        public Builder apiEndpoint(String apiEndpoint) {
            config.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder apiKey(String apiKey) {
            config.apiKey = apiKey;
            return this;
        }

        public Builder maxConcurrentRequests(int maxConcurrentRequests) {
            config.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout) {
            config.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder requestTimeout(int requestTimeout) {
            config.requestTimeout = requestTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            config.maxRetries = maxRetries;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            config.threadPoolSize = threadPoolSize;
            return this;
        }

        public Builder maxBatchSize(int maxBatchSize) {
            config.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder enableCompression(boolean enableCompression) {
            config.enableCompression = enableCompression;
            return this;
        }

        public Builder maxConnectionsPerHost(int maxConnectionsPerHost) {
            config.maxConnectionsPerHost = maxConnectionsPerHost;
            return this;
        }

        public Builder batchExecutionTimeout(int batchExecutionTimeout) {
            config.batchExecutionTimeout = batchExecutionTimeout;
            return this;
        }

        public BatchJobConfig build() {
            return config;
        }
    }

    // Getters
    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public int getBatchExecutionTimeout() {
        return batchExecutionTimeout;
    }
}