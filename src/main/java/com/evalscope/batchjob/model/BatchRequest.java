package com.evalscope.batchjob.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 表示发送给大模型API的单个请求
 */
public class BatchRequest {
    private String requestId;
    private String modelName;
    private String prompt;
    private Map<String, Object> parameters;
    private long timestamp;
    private int priority;
    private int maxTokens;
    private double temperature;
    private int timeoutMs;

    /**
     * 创建一个新的批处理请求
     */
    public BatchRequest() {
        this.parameters = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
        this.priority = 0;
        this.maxTokens = 1000;
        this.temperature = 0.7;
        this.timeoutMs = 30000;
    }

    /**
     * 创建一个新的批处理请求
     * 
     * @param requestId 请求ID
     * @param modelName 模型名称
     * @param prompt 提示文本
     */
    public BatchRequest(String requestId, String modelName, String prompt) {
        this();
        this.requestId = requestId;
        this.modelName = modelName;
        this.prompt = prompt;
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
     * BatchRequest的构建器类
     */
    public static class Builder {
        private BatchRequest request;

        public Builder() {
            request = new BatchRequest();
        }

        public Builder requestId(String requestId) {
            request.requestId = requestId;
            return this;
        }

        public Builder modelName(String modelName) {
            request.modelName = modelName;
            return this;
        }

        public Builder prompt(String prompt) {
            request.prompt = prompt;
            return this;
        }

        public Builder parameter(String key, Object value) {
            request.parameters.put(key, value);
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            request.parameters.putAll(parameters);
            return this;
        }

        public Builder priority(int priority) {
            request.priority = priority;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            request.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            request.temperature = temperature;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            request.timeoutMs = timeoutMs;
            return this;
        }

        public BatchRequest build() {
            return request;
        }
    }

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * 添加参数
     * 
     * @param key 参数键
     * @param value 参数值
     */
    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    @Override
    public String toString() {
        return "BatchRequest{" +
                "requestId='" + requestId + '\'' +
                ", modelName='" + modelName + '\'' +
                ", prompt='" + (prompt != null ? prompt.substring(0, Math.min(30, prompt.length())) + "..." : null) + '\'' +
                ", parameters=" + parameters +
                ", priority=" + priority +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                ", timeoutMs=" + timeoutMs +
                '}';
    }
}