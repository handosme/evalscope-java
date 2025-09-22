package com.evalscope.config;

import java.util.Map;
import java.util.HashMap;

public class DatasetConfig {
    private String datasetId;
    private String format;
    private String path;
    private String datasetType;
    private Map<String, Object> parameters;

    public DatasetConfig(String datasetId) {
        this.datasetId = datasetId;
        this.format = "json";
        this.parameters = new HashMap<>();
    }

    public DatasetConfig(String datasetId, String format, String path) {
        this.datasetId = datasetId;
        this.format = format;
        this.path = path;
        this.parameters = new HashMap<>();
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;
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

    public Object getParameter(String key) {
        return this.parameters.get(key);
    }

    public Object getParameter(String key, Object defaultValue) {
        return this.parameters.getOrDefault(key, defaultValue);
    }
}