package com.evalscope.config;

import java.util.Map;

/**
 * Interface for configuration managers
 */
public interface IConfigManager {
    ModelConfig getModelConfig(String modelId);
    EvaluationConfig getEvaluationConfig(String evaluationName);
    Map<String, ModelConfig> getAllModelConfigs();
    Map<String, EvaluationConfig> getAllEvaluationConfigs();
    void addModelConfig(ModelConfig config);
    void addEvaluationConfig(EvaluationConfig config);
}