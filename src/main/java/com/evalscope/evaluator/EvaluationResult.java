package com.evalscope.evaluator;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * AI模型评估结果类
 * 封装了AI模型评估的完整结果，包括总体得分、详细测试记录、性能指标和统计信息
 *
 * 评估结果包含的主要信息：
 * - 评估器信息和模型标识
 * - 标准化评分（0.0-1.0）
 * - 详细的测试用例结果
 * - 性能统计指标
 * - 评估时间信息和错误记录
 *
 * 此类是评估过程的最终产出，可用于：
 * - 生成评估报告
 * - 比较不同模型的表现
 * - 进行质量分析和改进建议
 */
public class EvaluationResult {
    private String evaluatorName;
    private String modelId;
    private String taskType;
    private double overallScore;
    private List<TestResult> testResults;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> metrics;
    private String errorMessage;
    private boolean success;

    public EvaluationResult(String evaluatorName, String modelId, String taskType) {
        this.evaluatorName = evaluatorName;
        this.modelId = modelId;
        this.taskType = taskType;
        this.startTime = LocalDateTime.now();
        this.success = true;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public String getModelId() {
        return modelId;
    }

    public String getTaskType() {
        return taskType;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }

    public void setTestResults(List<TestResult> testResults) {
        this.testResults = testResults;
        calculateOverallScore();
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

    private void calculateOverallScore() {
        if (testResults != null && !testResults.isEmpty()) {
            double totalScore = testResults.stream()
                    .mapToDouble(TestResult::getScore)
                    .sum();
            this.overallScore = totalScore / testResults.size();
        }
    }
}