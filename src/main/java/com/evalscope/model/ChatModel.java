package com.evalscope.model;

import java.util.Map;
import java.util.HashMap;

/**
 * 对话模型抽象基类
 * 实现了基础的模型管理功能，并定义了对话生成接口
 * 适用于聊天机器人、文本生成等对话类AI模型
 */
public abstract class ChatModel implements Model {
    protected String modelId;
    protected String modelType;
    protected boolean loaded;
    protected Map<String, Object> config;

    public ChatModel(String modelId, String modelType) {
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
        return "chat".equals(taskType) || "text_generation".equals(taskType);
    }

    public abstract ModelResponse generate(String prompt, Map<String, Object> parameters);
    public abstract ModelResponse generate(String prompt);

    protected void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    protected void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}