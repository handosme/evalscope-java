package com.evalscope.batchjob;

import com.evalscope.batchjob.model.BatchResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批处理结果类，包含批处理的统计信息和响应数据
 */
public class BatchJobResult {
    private final BatchResponse response;
    private final long processingTime;
    private final int totalRequests;
    private final int successfulRequests;
    private final int failedRequests;
    private final Map<String, List<String>> errors;

    /**
     * 创建一个新的批处理结果
     * 
     * @param response 批处理响应
     */
    public BatchJobResult(BatchResponse response) {
        this.response = response;
        this.processingTime = response.getProcessingTime();
        this.totalRequests = response.getTotalRequests();
        this.successfulRequests = response.getSuccessfulRequests();
        this.failedRequests = response.getFailedRequests();
        this.errors = response.getErrors() != null ? 
                      Collections.unmodifiableMap(response.getErrors()) : 
                      Collections.emptyMap();
    }

    /**
     * 获取原始批处理响应
     * 
     * @return 批处理响应
     */
    public BatchResponse getResponse() {
        return response;
    }

    /**
     * 获取处理时间（毫秒）
     * 
     * @return 处理时间
     */
    public long getProcessingTime() {
        return processingTime;
    }

    /**
     * 获取总请求数
     * 
     * @return 总请求数
     */
    public int getTotalRequests() {
        return totalRequests;
    }

    /**
     * 获取成功请求数
     * 
     * @return 成功请求数
     */
    public int getSuccessfulRequests() {
        return successfulRequests;
    }

    /**
     * 获取失败请求数
     * 
     * @return 失败请求数
     */
    public int getFailedRequests() {
        return failedRequests;
    }

    /**
     * 获取错误信息
     * 
     * @return 错误信息映射，键为请求ID，值为错误消息列表
     */
    public Map<String, List<String>> getErrors() {
        return errors;
    }

    /**
     * 获取成功率
     * 
     * @return 成功率（0-1之间的小数）
     */
    public double getSuccessRate() {
        return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0;
    }

    /**
     * 获取平均每个请求的处理时间
     * 
     * @return 平均处理时间（毫秒）
     */
    public double getAverageProcessingTimePerRequest() {
        return totalRequests > 0 ? (double) processingTime / totalRequests : 0;
    }

    /**
     * 检查批处理是否完全成功（没有失败的请求）
     * 
     * @return 如果所有请求都成功，则返回true
     */
    public boolean isFullySuccessful() {
        return failedRequests == 0;
    }

    /**
     * 获取结果摘要
     * 
     * @return 结果摘要字符串
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("批处理结果摘要:\n");
        summary.append("总请求数: ").append(totalRequests).append("\n");
        summary.append("成功请求数: ").append(successfulRequests).append("\n");
        summary.append("失败请求数: ").append(failedRequests).append("\n");
        summary.append("成功率: ").append(String.format("%.2f%%", getSuccessRate() * 100)).append("\n");
        summary.append("总处理时间: ").append(processingTime).append("ms\n");
        summary.append("平均每请求处理时间: ").append(String.format("%.2fms", getAverageProcessingTimePerRequest())).append("\n");
        
        if (!errors.isEmpty()) {
            summary.append("错误摘要:\n");
            errors.forEach((requestId, errorList) -> {
                summary.append("  请求ID: ").append(requestId).append("\n");
                errorList.forEach(error -> summary.append("    - ").append(error).append("\n"));
            });
        }
        
        return summary.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }
}