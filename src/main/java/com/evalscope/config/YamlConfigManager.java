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
import java.util.Properties;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced ConfigManager with YAML support
 */
public class YamlConfigManager implements IConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(YamlConfigManager.class);
    private static final String YAML_CONFIG_FILE = "application.yaml";
    private static final String DEFAULT_CONFIG_FILE = "application.conf";

    private Config typesafeConfig;
    private ObjectMapper objectMapper;
    private Map<String, ModelConfig> modelConfigs;
    private Map<String, EvaluationConfig> evaluationConfigs;
    private Map<String, DatasetConfig> datasetConfigs;

    public YamlConfigManager() {
        this.objectMapper = new ObjectMapper();
        this.modelConfigs = new HashMap<>();
        this.evaluationConfigs = new HashMap<>();
        this.datasetConfigs = new HashMap<>();
        loadDefaultConfig();
    }

    public YamlConfigManager(String configFilePath) {
        this();
        loadConfigFile(configFilePath);
    }

    private void loadDefaultConfig() {
        // Try to load YAML config first
        try (InputStream yamlStream = getClass().getClassLoader().getResourceAsStream(YAML_CONFIG_FILE)) {
            if (yamlStream != null) {
                parseYamlConfig(yamlStream);
                logger.info("Loaded configuration from {}", YAML_CONFIG_FILE);
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to load {}, trying fallback: {}", YAML_CONFIG_FILE, e.getMessage());
        }

        // Fallback to application.conf
        this.typesafeConfig = ConfigFactory.load();
        logger.info("Loaded configuration from {}", DEFAULT_CONFIG_FILE);
        parseConfigs();
    }

    private void parseYamlConfig(InputStream yamlStream) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> yamlData = yaml.load(yamlStream);

        if (yamlData != null && yamlData.containsKey("evalscope")) {
            Map<String, Object> evalscopeData = (Map<String, Object>) yamlData.get("evalscope");

            // Parse models
            if (evalscopeData.containsKey("models")) {
                Map<String, Object> modelsData = (Map<String, Object>) evalscopeData.get("models");
                for (Map.Entry<String, Object> entry : modelsData.entrySet()) {
                    String modelId = entry.getKey();
                    Map<String, Object> modelData = (Map<String, Object>) entry.getValue();

                    try {
                        ModelConfig config = parseYamlModelData(modelId, modelData);
                        modelConfigs.put(modelId, config);
                        logger.info("Loaded YAML model configuration: {}", modelId);
                    } catch (Exception e) {
                        logger.error("Failed to parse YAML model config for {}: {}", modelId, e.getMessage());
                    }
                }
            }

            // Parse evaluations
            if (evalscopeData.containsKey("evaluations")) {
                Map<String, Object> evaluationsData = (Map<String, Object>) evalscopeData.get("evaluations");
                for (Map.Entry<String, Object> entry : evaluationsData.entrySet()) {
                    String evaluationName = entry.getKey();
                    Map<String, Object> evalData = (Map<String, Object>) entry.getValue();

                    try {
                        EvaluationConfig config = parseYamlEvaluationData(evaluationName, evalData);
                        evaluationConfigs.put(evaluationName, config);
                        logger.info("Loaded YAML evaluation configuration: {}", evaluationName);
                    } catch (Exception e) {
                        logger.error("Failed to parse YAML evaluation config for {}: {}", evaluationName, e.getMessage());
                    }
                }
            }

            // Parse datasets
            if (evalscopeData.containsKey("datasets")) {
                Map<String, Object> datasetsData = (Map<String, Object>) evalscopeData.get("datasets");
                for (Map.Entry<String, Object> entry : datasetsData.entrySet()) {
                    String datasetId = entry.getKey();
                    Map<String, Object> datasetData = (Map<String, Object>) entry.getValue();

                    try {
                        DatasetConfig config = parseYamlDatasetData(datasetId, datasetData);
                        datasetConfigs.put(datasetId, config);
                        logger.info("Loaded YAML dataset configuration: {}", datasetId);
                    } catch (Exception e) {
                        logger.error("Failed to parse YAML dataset config for {}: {}", datasetId, e.getMessage());
                    }
                }
            }
        } else {
            throw new IOException("Invalid YAML structure: missing 'evalscope' section");
        }
    }

    private ModelConfig parseYamlModelData(String modelId, Map<String, Object> modelData) {
        String modelType = (String) modelData.getOrDefault("type", "chat");
        String provider = (String) modelData.getOrDefault("provider", "unknown");

        ModelConfig config = new ModelConfig(modelId, modelType, provider);

        if (modelData.containsKey("parameters")) {
            Map<String, Object> parameters = (Map<String, Object>) modelData.get("parameters");
            config.setParameters(parameters);
        }

        if (modelData.containsKey("credentials")) {
            Map<String, Object> credentials = (Map<String, Object>) modelData.get("credentials");
            config.setCredentials(credentials);
        }

        if (modelData.containsKey("enabled")) {
            config.setEnabled((Boolean) modelData.get("enabled"));
        }

        return config;
    }

    private EvaluationConfig parseYamlEvaluationData(String evaluationName, Map<String, Object> evalData) {
        EvaluationConfig config = new EvaluationConfig(evaluationName);

        if (evalData.containsKey("models")) {
            List<String> modelIds = (List<String>) evalData.get("models");
            config.setModelIds(modelIds);
        }

        if (evalData.containsKey("evaluatorTypes")) {
            List<String> evaluatorTypes = (List<String>) evalData.get("evaluatorTypes");
            config.setEvaluatorTypes(evaluatorTypes);
        }

        if (evalData.containsKey("datasetPath")) {
            config.setDatasetPath((String) evalData.get("datasetPath"));
        }

        if (evalData.containsKey("maxConcurrency")) {
            config.setMaxConcurrency((Integer) evalData.get("maxConcurrency"));
        }

        if (evalData.containsKey("saveResults")) {
            config.setSaveResults((Boolean) evalData.get("saveResults"));
        }

        if (evalData.containsKey("outputPath")) {
            config.setOutputPath((String) evalData.get("outputPath"));
        }

        if (evalData.containsKey("parameters")) {
            Map<String, Object> parameters = (Map<String, Object>) evalData.get("parameters");
            config.setParameters(parameters);
        }

        return config;
    }

    private DatasetConfig parseYamlDatasetData(String datasetId, Map<String, Object> datasetData) {
        String format = (String) datasetData.getOrDefault("format", "json");
        String path = (String) datasetData.get("path");
        String datasetType = (String) datasetData.getOrDefault("dataset_type", "conversation");

        DatasetConfig config = new DatasetConfig(datasetId, format, path);
        config.setDatasetType(datasetType);

        if (datasetData.containsKey("parameters")) {
            Map<String, Object> parameters = (Map<String, Object>) datasetData.get("parameters");
            config.setParameters(parameters);
        }

        return config;
    }

    private void loadConfigFile(String configFilePath) {
        try {
            if (configFilePath.endsWith(".yaml") || configFilePath.endsWith(".yml")) {
                Path path = Paths.get(configFilePath);
                InputStream inputStream;

                if (Files.exists(path)) {
                    inputStream = Files.newInputStream(path);
                } else {
                    inputStream = getClass().getClassLoader().getResourceAsStream(configFilePath);
                }

                if (inputStream != null) {
                    parseYamlConfig(inputStream);
                    inputStream.close();
                } else {
                    throw new IOException("Config file not found: " + configFilePath);
                }
            } else {
                loadHoconFromFile(configFilePath);
            }
        } catch (Exception e) {
            logger.error("Failed to load config from {}, using default: {}", configFilePath, e.getMessage());
            loadDefaultConfig();
        }
    }

    private void loadHoconFromFile(String configFilePath) {
        try {
            Path path = Paths.get(configFilePath);
            if (Files.exists(path)) {
                this.typesafeConfig = ConfigFactory.parseFile(path.toFile()).withFallback(ConfigFactory.load());
            } else {
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
            logger.error("Failed to load HOCON config: {}", e.getMessage());
            this.typesafeConfig = ConfigFactory.load();
            parseConfigs();
        }
    }

    // Original parsing methods for HOCON compatibility
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

    // Public API methods
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

    public static YamlConfigManager createDefault() {
        return new YamlConfigManager();
    }

    public static YamlConfigManager createFromFile(String configFilePath) {
        return new YamlConfigManager(configFilePath);
    }

    public void reloadConfig() {
        typesafeConfig = ConfigFactory.load().withFallback(typesafeConfig);
        modelConfigs.clear();
        evaluationConfigs.clear();
        datasetConfigs.clear();
        parseConfigs();
    }
}