package com.evalscope.model;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public abstract class EmbeddingModel implements Model {
    protected String modelId;
    protected String modelType;
    protected boolean loaded;
    protected Map<String, Object> config;

    public EmbeddingModel(String modelId, String modelType) {
        this.modelId = modelId;
        this.modelType = modelType;
        this.loaded = false;
        this.config = new HashMap<>();
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public String getModelType() {
        return modelType;
    }

    @Override
    public Map<String, Object> getModelInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("modelId", modelId);
        info.put("modelType", modelType);
        info.put("loaded", loaded);
        info.put("config", config);
        return info;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean supportsTask(String taskType) {
        return "embedding".equals(taskType) || "text_embedding".equals(taskType);
    }

    public abstract List<float[]> embed(List<String> texts);
    public abstract float[] embed(String text);

    protected void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    protected void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}