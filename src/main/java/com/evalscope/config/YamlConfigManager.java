package com.evalscope.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

/**
 * Enhanced ConfigManager with YAML support
 */
public class YamlConfigManager {
    private static final String YAML_CONFIG_FILE = "application.yaml";
    private static final String DEFAULT_CONFIG_FILE = "application.conf";

    private Config typesafeConfig;
    private ObjectMapper objectMapper;
    private ObjectMapper yamlMapper;
    private Map<String, ModelConfig> modelConfigs;
    private Map<String, EvaluationConfig> evaluationConfigs;

    public YamlConfigManager() {
        this.objectMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.modelConfigs = new HashMap<>();
        this.evaluationConfigs = new HashMap<>();
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
                JsonNode yamlNode = yamlMapper.readTree(yamlStream);
                System.out.println("Loaded configuration from " + YAML_CONFIG_FILE);

                // Convert YAML JSON structure to Config object for backward compatibility
                parseYamlConfigs(yamlNode);
                return;
            }
        } catch (Exception e) {
            System.err.println("Failed to load " + YAML_CONFIG_FILE + ", trying fallback: " + e.getMessage());
        }

        // Fallback to application.conf
        this.typesafeConfig = ConfigFactory.load();
        System.out.println("Loaded configuration from " + DEFAULT_CONFIG_FILE);
        parseConfigs();
    }

    private void loadConfigFile(String configFilePath) {
        try {
            if (configFilePath.endsWith(".yaml") || configFilePath.endsWith(".yml")) {
                loadYamlFromFile(configFilePath);
            } else {
                loadHoconFromFile(configFilePath);
            }
        } catch (Exception e) {
            System.err.println("Failed to load config from " + configFilePath + ", using default: " + e.getMessage());
            loadDefaultConfig();
        }
    }

    private void loadYamlFromFile(String configFilePath) throws IOException {
        Path path = Paths.get(configFilePath);
        InputStream inputStream;

        if (Files.exists(path)) {
            inputStream = Files.newInputStream(path);
        } else {
            inputStream = getClass().getClassLoader().getResourceAsStream(configFilePath);
        }

        if (inputStream != null) {
            JsonNode yamlNode = yamlMapper.readTree(inputStream);
            parseYamlConfigs(yamlNode);
            inputStream.close();
        } else {
            throw new IOException("Config file not found: " + configFilePath);
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
            System.err.println("Failed to load HOCON config: " + e.getMessage());
            this.typesafeConfig = ConfigFactory.load();
            parseConfigs();
        }
    }

    private void parseYamlConfigs(JsonNode yamlNode) {
        if (yamlNode.has("evalscope")) {
            JsonNode evalscopeNode = yamlNode.get("evalscope");
            parseYamlModelConfigs(evalscopeNode);
            parseYamlEvaluationConfigs(evalscopeNode);
            parseYamlGlobalSettings(evalscopeNode);
        }
    }

    private void parseYamlModelConfigs(JsonNode evalscopeNode) {
        if (evalscopeNode.has("models")) {
            JsonNode modelsNode = evalscopeNode.get("models");

            modelsNode.fields().forEachRemaining(entry -> {
                String modelId = entry.getKey();
                JsonNode modelConfig = entry.getValue();

                try {
                    ModelConfig config = parseYamlModelConfig(modelId, modelConfig);
                    modelConfigs.put(modelId, config);
                    System.out.println("Loaded model configuration: " + modelId);
                } catch (Exception e) {
                    System.err.println("Failed to parse model config for " + modelId + ": " + e.getMessage());
                }
            });
        }
    }

    private ModelConfig parseYamlModelConfig(String modelId, JsonNode configNode) {
        String modelType = configNode.get("type").asText("chat");
        String provider = configNode.get("provider").asText("unknown");

        ModelConfig config = new ModelConfig(modelId, modelType, provider);

        if (configNode.has("parameters")) {
            JsonNode paramsNode = configNode.get("parameters");
            Map<String, Object> parameters = objectMapper.convertValue(paramsNode, Map.class);
            config.setParameters(parameters);
        }

        if (configNode.has("credentials")) {
            JsonNode credsNode = configNode.get("credentials");
            Map<String, Object> credentials = objectMapper.convertValue(credsNode, Map.class);
            config.setCredentials(credentials);
        }

        if (configNode.has("enabled")) {
            config.setEnabled(configNode.get("enabled").asBoolean(true));
        }

        return config;
    }

    private void parseYamlEvaluationConfigs(JsonNode evalscopeNode) {
        if (evalscopeNode.has("evaluations")) {
            JsonNode evaluationsNode = evalscopeNode.get("evaluations");

            evaluationsNode.fields().forEachRemaining(entry -> {
                String evaluationName = entry.getKey();
                JsonNode evalConfig = entry.getValue();

                try {
                    EvaluationConfig config = parseYamlEvaluationConfig(evaluationName, evalConfig);
                    evaluationConfigs.put(evaluationName, config);
                    System.out.println("Loaded evaluation configuration: " + evaluationName);
                } catch (Exception e) {
                    System.err.println("Failed to parse evaluation config for " + evaluationName + ": " + e.getMessage());
                }
            });
        }
    }

    private EvaluationConfig parseYamlEvaluationConfig(String evaluationName, JsonNode configNode) {
        EvaluationConfig config = new EvaluationConfig(evaluationName);

        if (configNode.has("models")) {
            JsonNode modelsArray = configNode.get("models");
            List<String> modelIds = new ArrayList<>();
            modelsArray.forEach(node -> modelIds.add(node.asText()));
            config.setModelIds(modelIds);
        }

        if (configNode.has("evaluators")) {
            JsonNode evaluatorsArray = configNode.get("evaluators");
            List<String> evaluatorTypes = new ArrayList<>();
            evaluatorsArray.forEach(node -> evaluatorTypes.add(node.asText()));
            config.setEvaluatorTypes(evaluatorTypes);
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
            JsonNode paramsNode = configNode.get("parameters");
            Map<String, Object> parameters = objectMapper.convertValue(paramsNode, Map.class);
            config.setParameters(parameters);
        }

        return config;
    }

    private void parseYamlGlobalSettings(JsonNode evalscopeNode) {
        if (evalscopeNode.has("settings")) {
            JsonNode settingsNode = evalscopeNode.get("settings");
            // Store global settings in typesafe config for compatibility
            Config settingsConfig = ConfigFactory.parseString(settingsNode.toString());
            // You can add global settings handling here if needed
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
                        System.err.println("Failed to parse model config for " + modelId + ": " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Failed to parse model configs: " + e.getMessage());
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
                        System.err.println("Failed to parse evaluation config for " + evaluationName + ": " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Failed to parse evaluation configs: " + e.getMessage());
            }
        }
    }

    private EvaluationConfig parseEvaluationConfig(String evaluationName, JsonNode configNode) {
        EvaluationConfig config = new EvaluationConfig(evaluationName);

        if (configNode.has("models")) {
            List<String> modelIds = new ArrayList<>();
            configNode.get("models").forEach(node -> modelIds.add(node.asText()));
            config.setModelIds(modelIds);
        }

        if (configNode.has("evaluators")) {
            List<String> evaluatorTypes = new ArrayList<>();
            configNode.get("evaluators").forEach(node -> evaluatorTypes.add(node.asText()));
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
        parseConfigs();
    }
}