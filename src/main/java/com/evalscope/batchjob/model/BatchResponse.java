package com.evalscope.batchjob.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表示从大模型API接收到的批量响应
 */
public class BatchResponse {
    private String batchId;
    private Map<String, String> responses;
    private Map<String, List<String>> errors;
    private long startTime;
    private long endTime;
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private Map<String, Object> metadata;

    /**
     * 创建一个新的批处理响应
     */
    public BatchResponse() {
        this.responses = new HashMap<>();
        this.errors = new HashMap<>();
        this.metadata = new HashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 创建一个新的批处理响应
     * 
     * @param batchId 批次ID
     */
    public BatchResponse(String batchId) {
        this();
        this.batchId = batchId;
    }

    /**
     * 添加成功的响应
     * 
     * @param requestId 请求ID
     * @param response 响应内容
     */
    public void addResponse(String requestId, String response) {
        this.responses.put(requestId, response);
        this.successfulRequests++;
        this.totalRequests++;
    }

    /**
     * 添加错误
     * 
     * @param requestId 请求ID
     * @param error 错误消息
     */
    public void addError(String requestId, String error) {
        List<String> requestErrors = this.errors.computeIfAbsent(requestId, k -> new ArrayList<>());
        requestErrors.add(error);
        this.failedRequests++;
        this.totalRequests++;
    }

    /**
     * 完成批处理响应
     */
    public void complete() {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * 获取处理时间（毫秒）
     * 
     * @return 处理时间
     */
    public long getProcessingTime() {
        return endTime - startTime;
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public Map<String, String> getResponses() {
        return responses;
    }

    public void setResponses(Map<String, String> responses) {
        this.responses = responses;
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, List<String>> errors) {
        this.errors = errors;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(int successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(int failedRequests) {
        this.failedRequests = failedRequests;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 添加元数据
     * 
     * @param key 元数据键
     * @param value 元数据值
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    /**
     * 获取特定请求的响应
     * 
     * @param requestId 请求ID
     * @return 响应内容，如果不存在则返回null
     */
    public String getResponseForRequest(String requestId) {
        return responses.get(requestId);
    }

    /**
     * 获取特定请求的错误
     * 
     * @param requestId 请求ID
     * @return 错误列表，如果不存在则返回空列表
     */
    public List<String> getErrorsForRequest(String requestId) {
        return errors.getOrDefault(requestId, new ArrayList<>());
    }

    /**
     * 检查请求是否成功
     * 
     * @param requestId 请求ID
     * @return 如果请求成功则返回true
     */
    public boolean isRequestSuccessful(String requestId) {
        return responses.containsKey(requestId);
    }

    @Override
    public String toString() {
        return "BatchResponse{" +
                "batchId='" + batchId + '\'' +
                ", totalRequests=" + totalRequests +
                ", successfulRequests=" + successfulRequests +
                ", failedRequests=" + failedRequests +
                ", processingTime=" + getProcessingTime() + "ms" +
                '}';
    }
}