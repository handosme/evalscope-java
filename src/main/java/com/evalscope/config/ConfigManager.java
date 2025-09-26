package com.evalscope.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager implements IConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String YAM_CONFIG_FILE = "application.yaml";
    private static final String DEFAULT_CONFIG_FILE = "application.conf";

    private Config typesafeConfig;
    private ObjectMapper objectMapper;
    private Map<String, ModelConfig> modelConfigs;
    private Map<String, EvaluationConfig> evaluationConfigs;
    private Map<String, DatasetConfig> datasetConfigs;

    public ConfigManager() {
        this.objectMapper = new ObjectMapper();
        this.modelConfigs = new HashMap<>();
        this.evaluationConfigs = new HashMap<>();
        this.datasetConfigs = new HashMap<>();
        loadDefaultConfig();
    }

    public ConfigManager(String configFilePath) {
        this();
        loadConfigFile(configFilePath);
    }

    private void loadDefaultConfig() {
        // Try to load YAML config first
        try (InputStream yamlStream = getClass().getClassLoader().getResourceAsStream(YAM_CONFIG_FILE)) {
            if (yamlStream != null) {
                this.typesafeConfig = ConfigFactory.parseResources(YAM_CONFIG_FILE).withFallback(ConfigFactory.load());
                logger.info("Loaded configuration from {}", YAM_CONFIG_FILE);
            } else {
                // Fallback to application.conf
                this.typesafeConfig = ConfigFactory.load();
                logger.info("Loaded configuration from {}", DEFAULT_CONFIG_FILE);
            }
        } catch (Exception e) {
            logger.warn("Failed to load {}, using default: {}", YAM_CONFIG_FILE, e.getMessage());
            this.typesafeConfig = ConfigFactory.load();
            logger.info("Loaded configuration from {}", DEFAULT_CONFIG_FILE);
        }
        parseConfigs();
    }

    private void loadConfigFile(String configFilePath) {
        try {
            Path path = Paths.get(configFilePath);
            if (Files.exists(path)) {
                this.typesafeConfig = ConfigFactory.parseFile(path.toFile()).withFallback(ConfigFactory.load());
            } else {
                // Try to load from classpath
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(configFilePath)) {
                    if (is != null) {
                        this.typesafeConfig = ConfigFactory.parseResources(configFilePath).withFallback(ConfigFactory.load());
                    } else {
                        this.typesafeConfig = ConfigFactory.load();
                    }
                }
            }
            parseConfigs();
        } catch (Exception e) {
            logger.error("Failed to load config from {}, using default: {}", configFilePath, e.getMessage());
            loadDefaultConfig();
        }
    }

    private void parseConfigs() {
        parseModelConfigs();
        parseEvaluationConfigs();
    }

    private void parseModelConfigs() {
        if (typesafeConfig.hasPath("evalscope.models")) {
            try {
                JsonNode modelsNode = objectMapper.readTree(typesafeConfig.getObject("evalscope.models").render());

                modelsNode.fields().forEachRemaining(entry -> {
                    String modelId = entry.getKey();
                    JsonNode modelConfig = entry.getValue();

                    try {
                        ModelConfig config = parseModelConfig(modelId, modelConfig);
                        modelConfigs.put(modelId, config);
                    } catch (Exception e) {
                        logger.error("Failed to parse model config for {}: {}", modelId, e.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to parse model configs: {}", e.getMessage());
            }
        }
    }

    private ModelConfig parseModelConfig(String modelId, JsonNode configNode) {
        String modelType = configNode.get("type").asText("chat");
        String provider = configNode.get("provider").asText("unknown");

        ModelConfig config = new ModelConfig(modelId, modelType, provider);

        if (configNode.has("parameters")) {
            Map<String, Object> parameters = objectMapper.convertValue(configNode.get("parameters"), Map.class);
            config.setParameters(parameters);
        }

        if (configNode.has("credentials")) {
            Map<String, Object> credentials = objectMapper.convertValue(configNode.get("credentials"), Map.class);
            config.setCredentials(credentials);
        }

        if (configNode.has("enabled")) {
            config.setEnabled(configNode.get("enabled").asBoolean(true));
        }

        return config;
    }

    private void parseEvaluationConfigs() {
        if (typesafeConfig.hasPath("evalscope.evaluations")) {
            try {
                JsonNode evaluationsNode = objectMapper.readTree(typesafeConfig.getObject("evalscope.evaluations").render());

                evaluationsNode.fields().forEachRemaining(entry -> {
                    String evaluationName = entry.getKey();
                    JsonNode evalConfig = entry.getValue();

                    try {
                        EvaluationConfig config = parseEvaluationConfig(evaluationName, evalConfig);
                        evaluationConfigs.put(evaluationName, config);
                    } catch (Exception e) {
                        logger.error("Failed to parse evaluation config for {}: {}", evaluationName, e.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to parse evaluation configs: {}", e.getMessage());
            }
        }
    }

    private EvaluationConfig parseEvaluationConfig(String evaluationName, JsonNode configNode) {
        EvaluationConfig config = new EvaluationConfig(evaluationName);

        if (configNode.has("models")) {
            List<String> modelIds = new ArrayList<>();
            JsonNode modelsNode = configNode.get("models");
            for (int i = 0; i < modelsNode.size(); i++) {
                modelIds.add(modelsNode.get(i).asText());
            }
            config.setModelIds(modelIds);
        }

        if (configNode.has("evaluators")) {
            List<String> evaluatorTypes = new ArrayList<>();
            JsonNode evaluatorsNode = configNode.get("evaluators");
            for (int i = 0; i < evaluatorsNode.size(); i++) {
                evaluatorTypes.add(evaluatorsNode.get(i).asText());
            }
            config.setEvaluatorTypes(evaluatorTypes);
        }

        if (configNode.has("dataset")) {
            config.setDatasetPath(configNode.get("dataset").asText());
        }

        if (configNode.has("maxConcurrency")) {
            config.setMaxConcurrency(configNode.get("maxConcurrency").asInt(1));
        }

        if (configNode.has("saveResults")) {
            config.setSaveResults(configNode.get("saveResults").asBoolean(true));
        }

        if (configNode.has("outputPath")) {
            config.setOutputPath(configNode.get("outputPath").asText());
        }

        if (configNode.has("parameters")) {
            Map<String, Object> parameters = objectMapper.convertValue(configNode.get("parameters"), Map.class);
            config.setParameters(parameters);
        }

        return config;
    }

    public ModelConfig getModelConfig(String modelId) {
        return modelConfigs.get(modelId);
    }

    public EvaluationConfig getEvaluationConfig(String evaluationName) {
        return evaluationConfigs.get(evaluationName);
    }

    public Map<String, ModelConfig> getAllModelConfigs() {
        return new HashMap<>(modelConfigs);
    }

    public Map<String, EvaluationConfig> getAllEvaluationConfigs() {
        return new HashMap<>(evaluationConfigs);
    }

    public void addModelConfig(ModelConfig config) {
        modelConfigs.put(config.getModelId(), config);
    }

    public void addEvaluationConfig(EvaluationConfig config) {
        evaluationConfigs.put(config.getEvaluationName(), config);
    }

    // Basic dataset config support (simplified, not full datasets parsing)
    public DatasetConfig getDatasetConfig(String datasetId) {
        return datasetConfigs.get(datasetId);
    }

    public Map<String, DatasetConfig> getAllDatasetConfigs() {
        return new HashMap<>(datasetConfigs);
    }

    public void addDatasetConfig(DatasetConfig config) {
        datasetConfigs.put(config.getDatasetId(), config);
    }

    public Config getRawConfig() {
        return typesafeConfig;
    }

    public static ConfigManager createDefault() {
        return new ConfigManager();
    }

    public static ConfigManager createFromFile(String configFilePath) {
        return new ConfigManager(configFilePath);
    }

    public void reloadConfig() {
        typesafeConfig = ConfigFactory.load().withFallback(typesafeConfig);
        modelConfigs.clear();
        evaluationConfigs.clear();
        datasetConfigs.clear();
        parseConfigs();
    }
}