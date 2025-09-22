package com.evalscope.benchmark;

import java.time.LocalDateTime;
import java.util.Map;

public class BenchmarkResult {
    private String benchmarkName;
    private String modelId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> metrics;
    private String errorMessage;
    private boolean success;

    public BenchmarkResult(String benchmarkName, String modelId) {
        this.benchmarkName = benchmarkName;
        this.modelId = modelId;
        this.startTime = LocalDateTime.now();
        this.success = true;
    }

    public String getBenchmarkName() {
        return benchmarkName;
    }

    public String getModelId() {
        return modelId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
        this.endTime = LocalDateTime.now(); // Auto-set end time when metrics are complete
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.success = false;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void addMetric(String key, Object value) {
        if (this.metrics == null) {
            this.metrics = new java.util.HashMap<>();
        }
        this.metrics.put(key, value);
    }

    public Object getMetric(String key) {
        return this.metrics != null ? this.metrics.get(key) : null;
    }
}