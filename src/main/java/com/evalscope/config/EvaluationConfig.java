package com.evalscope.config;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class EvaluationConfig {
    private String evaluationName;
    private List<String> modelIds;
    private List<String> evaluatorTypes;
    private String datasetPath;
    private int maxConcurrency;
    private boolean saveResults;
    private String resultFormat;
    private String outputPath;
    private Map<String, Object> parameters;

    public EvaluationConfig(String evaluationName) {
        this.evaluationName = evaluationName;
        this.maxConcurrency = 1;
        this.saveResults = true;
        this.resultFormat = "json";
        this.parameters = new HashMap<>();
    }

    public String getEvaluationName() {
        return evaluationName;
    }

    public void setEvaluationName(String evaluationName) {
        this.evaluationName = evaluationName;
    }

    public List<String> getModelIds() {
        return modelIds;
    }

    public void setModelIds(List<String> modelIds) {
        this.modelIds = modelIds;
    }

    public List<String> getEvaluatorTypes() {
        return evaluatorTypes;
    }

    public void setEvaluatorTypes(List<String> evaluatorTypes) {
        this.evaluatorTypes = evaluatorTypes;
    }

    public String getDatasetPath() {
        return datasetPath;
    }

    public void setDatasetPath(String datasetPath) {
        this.datasetPath = datasetPath;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public boolean isSaveResults() {
        return saveResults;
    }

    public void setSaveResults(boolean saveResults) {
        this.saveResults = saveResults;
    }

    public String getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    private String runMode = "normal";

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }
}