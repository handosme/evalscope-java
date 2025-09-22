package com.evalscope.model;

import com.evalscope.config.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型工厂类
 * 负责创建和管理不同类型的AI模型实例
 */
public class ModelFactory {
    private static final Logger logger = LoggerFactory.getLogger(ModelFactory.class);

    private static final Map<String, ModelCreator> registeredModels = new HashMap<>();

    // 内置的模型提供者
    private static final Map<String, ModelProvider> builtinProviders = new HashMap<>();

    /**
     * 模型创建器接口
     */
    @FunctionalInterface
    public interface ModelCreator {
        Model create(ModelConfig config);
    }

    /**
     * 模型提供者接口
     */
    public interface ModelProvider {
        Model createModel(ModelConfig config);
        boolean supportsConfig(ModelConfig config);
    }

    static {
        // 注册内置的模型提供者
        registerBuiltinProviders();
    }

    /**
     * 注册内置的模型提供者
     */
    private static void registerBuiltinProviders() {
        // OpenAI兼容模型提供器
        builtinProviders.put("openai", new ModelProvider() {
            @Override
            public Model createModel(ModelConfig config) {
                return createOpenAICompatibleModel(config);
            }

            @Override
            public boolean supportsConfig(ModelConfig config) {
                return "openai".equals(config.getProvider()) ||
                       "azure-openai".equals(config.getProvider()) ||
                       "custom-openai".equals(config.getProvider());
            }
        });

        // HuggingFace模型提供器
        builtinProviders.put("huggingface", new ModelProvider() {
            @Override
            public Model createModel(ModelConfig config) {
                return createHuggingFaceModel(config);
            }

            @Override
            public boolean supportsConfig(ModelConfig config) {
                return "huggingface".equals(config.getProvider()) ||
                       "hf".equals(config.getProvider());
            }
        });

        // Mock模型提供器（用于测试）
        builtinProviders.put("mock", new ModelProvider() {
            @Override
            public Model createModel(ModelConfig config) {
                return createMockModel(config);
            }

            @Override
            public boolean supportsConfig(ModelConfig config) {
                return "mock".equals(config.getProvider());
            }
        });

        // 本地模型提供器
        builtinProviders.put("local", new ModelProvider() {
            @Override
            public Model createModel(ModelConfig config) {
                return createLocalModel(config);
            }

            @Override
            public boolean supportsConfig(ModelConfig config) {
                return "local".equals(config.getProvider()) ||
                       "localhost".equals(config.getProvider());
            }
        });
    }

    /**
     * 根据配置创建模型
     */
    public static Model createModel(ModelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ModelConfig cannot be null");
        }

        if (!config.isEnabled()) {
            logger.warn("Attempting to create disabled model: {}", config.getModelId());
            return null;
        }

        // 首先检查用户注册的自定义模型
        String modelKey = config.getProvider() + ":" + config.getModelType();
        ModelCreator creator = registeredModels.get(modelKey);
        if (creator != null) {
            logger.info("Creating user-registered model: {} with provider: {} and type: {}",
                    config.getModelId(), config.getProvider(), config.getModelType());
            return creator.create(config);
        }

        // 检查内置的模型提供者
        for (ModelProvider provider : builtinProviders.values()) {
            if (provider.supportsConfig(config)) {
                logger.info("Creating builtin model: {} with provider: {} and type: {}",
                        config.getModelId(), config.getProvider(), config.getModelType());
                return provider.createModel(config);
            }
        }

        // 默认回退到OpenAI兼容模型
        logger.warn("No specific provider found for {}:{}, defaulting to OpenAI compatible model",
                config.getProvider(), config.getModelType());
        return createOpenAICompatibleModel(config);
    }

    /**
     * 注册自定义模型创建器
     */
    public static void registerModel(String provider, String modelType, ModelCreator creator) {
        String key = provider + ":" + modelType;
        registeredModels.put(key, creator);
        logger.info("Registered custom model creator for {}:{}", provider, modelType);
    }

    /**
     * 注册自定义模型提供器
     */
    public static void registerProvider(String name, ModelProvider provider) {
        builtinProviders.put(name, provider);
        logger.info("Registered custom model provider: {}", name);
    }

    /**
     * 创建OpenAI兼容模型
     */
    private static Model createOpenAICompatibleModel(ModelConfig config) {
        OpenAICompatibleModel model = new OpenAICompatibleModel(
                config.getModelId(),
                config.getModelType(),
                config.getProvider()
        );

        // 设置API配置
        Map<String, Object> parameters = config.getParameters();
        if (parameters != null) {
            if (parameters.containsKey("endpoint")) {
                model.setApiEndpoint((String) parameters.get("endpoint"));
            }
            if (parameters.containsKey("model_name")) {
                model.setModelName((String) parameters.get("model_name"));
            } else {
                // 使用modelId作为模型名称
                model.setModelName(config.getModelId());
            }
            if (parameters.containsKey("connect_timeout")) {
                model.setConnectionTimeout((Integer) parameters.get("connect_timeout"));
            }
            if (parameters.containsKey("read_timeout")) {
                model.setReadTimeout((Integer) parameters.get("read_timeout"));
            }
            if (parameters.containsKey("max_retries")) {
                model.setMaxRetries((Integer) parameters.get("max_retries"));
            }
            if (parameters.containsKey("retry_delay")) {
                model.setRetryDelay((Long) parameters.get("retry_delay"));
            }

            // 设置默认参数
            Map<String, Object> defaultParams = new HashMap<>();
            defaultParams.put("max_tokens", parameters.getOrDefault("max_tokens", 2048));
            defaultParams.put("temperature", parameters.getOrDefault("temperature", 0.7));
            defaultParams.put("top_p", parameters.getOrDefault("top_p", 0.9));

            model.setDefaultParameters(defaultParams);
        }

        // 设置API密钥
        Map<String, Object> credentials = config.getCredentials();
        if (credentials != null && credentials.containsKey("api_key")) {
            model.setApiKey((String) credentials.get("api_key"));
        }

        return model;
    }

    /**
     * 创建HuggingFace模型
     */
    private static Model createHuggingFaceModel(ModelConfig config) {
        HuggingFaceModel model = new HuggingFaceModel(
                config.getModelId(),
                config.getModelType()
        );

        Map<String, Object> parameters = config.getParameters();
        if (parameters != null) {
            if (parameters.containsKey("model_name")) {
                model.setModelName((String) parameters.get("model_name"));
            } else {
                model.setModelName(config.getModelId()); // 使用modelId作为HF模型名称
            }

            if (parameters.containsKey("endpoint")) {
                model.setHfApiEndpoint((String) parameters.get("endpoint"));
            }
            if (parameters.containsKey("connect_timeout")) {
                model.setConnectionTimeout((Integer) parameters.get("connect_timeout"));
            }
            if (parameters.containsKey("read_timeout")) {
                model.setReadTimeout((Integer) parameters.get("read_timeout"));
            }
            if (parameters.containsKey("max_retries")) {
                model.setMaxRetries((Integer) parameters.get("max_retries"));
            }
            if (parameters.containsKey("retry_delay")) {
                model.setRetryDelay((Long) parameters.get("retry_delay"));
            }

            // 设置默认参数
            Map<String, Object> defaultParams = new HashMap<>();
            defaultParams.put("max_tokens", parameters.getOrDefault("max_tokens", 512));
            defaultParams.put("temperature", parameters.getOrDefault("temperature", 0.7));
            defaultParams.put("top_p", parameters.getOrDefault("top_p", 0.9));

            model.setDefaultParameters(defaultParams);
        }

        // 设置API Token
        Map<String, Object> credentials = config.getCredentials();
        if (credentials != null && credentials.containsKey("api_token")) {
            model.setApiToken((String) credentials.get("api_token"));
        }

        return model;
    }

    /**
     * 创建Mock模型
     */
    private static Model createMockModel(ModelConfig config) {
        // Mock模拟实现（只返回固定内容，不实际调用API）
        ChatModel mockModel = new ChatModel(config.getModelId(), config.getModelType()) {
            private boolean loaded = false;

            @Override
            public void load() {
                loaded = true;
                logger.info("Mock model {} loaded", getModelId());
            }

            @Override
            public void unload() {
                loaded = false;
                logger.info("Mock model {} unloaded", getModelId());
            }

            @Override
            public ModelResponse generate(String prompt, Map<String, Object> parameters) {
                ModelResponse response = new ModelResponse(getModelId(), getModelType());

                try {
                    Thread.sleep(50 + (int)(Math.random() * 200)); // 模拟网络延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                response.setOutput("[MOCK] Generated response for: " + prompt);
                response.setSuccess(true);
                response.setProcessingTimeMs(50 + (int)(Math.random() * 200));

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("mock", true);
                metadata.put("provider", "mock");
                metadata.put("confidence", 0.9 + Math.random() * 0.1);
                response.setMetadata(metadata);

                return response;
            }

            @Override
            public ModelResponse generate(String prompt) {
                return generate(prompt, new HashMap<>());
            }
        };

        return mockModel;
    }

    /**
     * 创建本地部署模型
     */
    private static Model createLocalModel(ModelConfig config) {
        OpenAICompatibleModel localModel = new OpenAICompatibleModel(
                config.getModelId(),
                config.getModelType(),
                config.getProvider()
        );

        Map<String, Object> parameters = config.getParameters();
        if (parameters != null && parameters.containsKey("endpoint")) {
            String endpoint = (String) parameters.get("endpoint");
            String baseUrl = endpoint.replaceFirst("^mock://", "http://");
            localModel.setApiEndpoint(baseUrl);
        } else {
            // 默认本地部署端点
            localModel.setApiEndpoint("http://localhost:8000");
        }

        if (config.getModelId() != null) {
            localModel.setModelName(config.getModelId());
        }

        return localModel;
    }

    /**
     * 获取已注册的模型提供者
     */
    public static Map<String, ModelProvider> getBuiltinProviders() {
        return new HashMap<>(builtinProviders);
    }

    /**
     * 清除注册表（主要用于测试）
     */
    public static void clearRegistry() {
        registeredModels.clear();
    }

    /**
     * 检查提供者是否支持给定配置
     */
    public static boolean isProviderSupported(String provider, String modelType) {
        // 检查内置提供者
        for (ModelProvider builtinProvider : builtinProviders.values()) {
            ModelConfig testConfig = new ModelConfig("test", modelType, provider);
            if (builtinProvider.supportsConfig(testConfig)) {
                return true;
            }
        }

        // 检查用户注册的提供者
        String key = provider + ":" + modelType;
        return registeredModels.containsKey(key);
    }
}