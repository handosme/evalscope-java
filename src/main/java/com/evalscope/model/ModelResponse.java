package com.evalscope.model;

import java.util.Map;
import java.time.LocalDateTime;

/**
 * AI模型响应类
 * 用于封装模型生成任务的响应结果，包含输出内容、响应时间和错误信息
 */
public class ModelResponse {
    /**
     * 模型标识符
     */
    private String modelId;

    /**
     * 任务类型（如：chat、embedding等）
     */
    private String taskType;

    /**
     * 模型生成的主要输出内容
     */
    private String output;

    /**
     * 元数据信息，如token计数、置信度等
     */
    private Map<String, Object> metadata;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * 错误信息（如果有的话）
     */
    private String errorMessage;

    /**
     * 是否启用了流式传输
     */
    private boolean streaming = false;

    /**
     * 构造新的模型响应实例
     * @param modelId 模型标识符
     * @param taskType 任务类型
     */
    public ModelResponse(String modelId, String taskType) {
        this.modelId = modelId;
        this.taskType = taskType;
        this.timestamp = LocalDateTime.now();
        this.success = true;
    }

    public String getModelId() {
        return modelId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

        public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.success = false;
    }
}