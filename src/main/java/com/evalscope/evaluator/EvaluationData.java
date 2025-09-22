package com.evalscope.evaluator;

import java.util.List;
import java.util.Map;

public class EvaluationData {
    private String taskType;
    private List<TestCase> testCases;
    private Map<String, Object> metadata;

    public EvaluationData(String taskType, List<TestCase> testCases) {
        this.taskType = taskType;
        this.testCases = testCases;
    }

    public EvaluationData(String taskType, List<TestCase> testCases, Map<String, Object> metadata) {
        this.taskType = taskType;
        this.testCases = testCases;
        this.metadata = metadata;
    }

    public String getTaskType() {
        return taskType;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}