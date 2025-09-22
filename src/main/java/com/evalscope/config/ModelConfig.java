package com.evalscope.config;

import java.util.Map;
import java.util.HashMap;

public class ModelConfig {
    private String modelId;
    private String modelType;
    private String provider;
    private Map<String, Object> parameters;
    private Map<String, Object> credentials;
    private boolean enabled;

    public ModelConfig(String modelId, String modelType, String provider) {
        this.modelId = modelId;
        this.modelType = modelType;
        this.provider = provider;
        this.parameters = new HashMap<>();
        this.credentials = new HashMap<>();
        this.enabled = true;
    }

    public String getModelId() {
        return modelId;
    }

    public String getModelType() {
        return modelType;
    }

    public String getProvider() {
        return provider;
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

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
    }

    public void addCredential(String key, Object value) {
        this.credentials.put(key, value);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}