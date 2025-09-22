package com.evalscope.evaluator;

import java.util.Map;
import java.time.LocalDateTime;

public class TestResult {
    private String testCaseId;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private double score;
    private boolean passed;
    private String feedback;
    private LocalDateTime timestamp;
    private Map<String, Object> metrics;
    private String errorMessage;

    public TestResult(String testCaseId, String input, String expectedOutput) {
        this.testCaseId = testCaseId;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.timestamp = LocalDateTime.now();
        this.passed = false;
        this.score = 0.0;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
        this.passed = score >= 0.8; // Default threshold
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
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
    }
}