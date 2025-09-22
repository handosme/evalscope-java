package com.evalscope.config;

import java.util.Map;

/**
 * Interface for configuration managers
 */
public interface IConfigManager {
    ModelConfig getModelConfig(String modelId);
    EvaluationConfig getEvaluationConfig(String evaluationName);
    DatasetConfig getDatasetConfig(String datasetId);
    Map<String, ModelConfig> getAllModelConfigs();
    Map<String, EvaluationConfig> getAllEvaluationConfigs();
    Map<String, DatasetConfig> getAllDatasetConfigs();
    void addModelConfig(ModelConfig config);
    void addEvaluationConfig(EvaluationConfig config);
    void addDatasetConfig(DatasetConfig config);
}