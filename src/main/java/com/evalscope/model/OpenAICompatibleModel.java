package com.evalscope.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Consumer;

/**
 * OpenAI兼容的聊天模型实现
 * 支持OpenAI API格式和各种兼容服务（如Azure OpenAI、本地部署等）
 */
public class OpenAICompatibleModel extends ChatModel {
    private static final Logger logger = LoggerFactory.getLogger(OpenAICompatibleModel.class);

    private String apiEndpoint;
    private String apiKey;
    private String modelName;
    private final String provider;
    private ObjectMapper objectMapper;
    private CloseableHttpClient httpClient;
    private Map<String, Object> defaultParameters;

    // 请求超时配置
    private int connectTimeout = 30; // seconds
    private int readTimeout = 60; // seconds
    private int maxRetries = 3;
    private long retryDelay = 1000; // milliseconds

    public OpenAICompatibleModel(String modelId, String modelType, String provider) {
        super(modelId, modelType);
        this.objectMapper = new ObjectMapper();
        this.defaultParameters = new HashMap<>();
        this.provider = provider != null ? provider : "openai";

        // 创建HTTP客户端
        initializeHttpClient();
    }

    public OpenAICompatibleModel(String modelId, String modelType) {
        this(modelId, modelType, "openai");
    }

    public OpenAICompatibleModel(String modelId) {
        this(modelId, "chat", "openai");
    }

    private void initializeHttpClient() {
        // 请求超时配置
        RequestConfig requestConfig =
            RequestConfig.custom()
                .setConnectionRequestTimeout(connectTimeout, TimeUnit.SECONDS)
                .setResponseTimeout(readTimeout, TimeUnit.SECONDS)
                .build();

        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Override
    public void load() throws Exception {
        logger.info("Loading OpenAI Compatible Model: {}", getModelId());

        // 验证必需配置
        validateConfiguration();

        // 测试API连接
        testConnection();

        setLoaded(true);
        logger.info("Model {} loaded successfully", getModelId());
    }

    @Override
    public void unload() throws Exception {
        logger.info("Unloading OpenAI Compatible Model: {}", getModelId());

        if (httpClient != null) {
            httpClient.close();
        }

        setLoaded(false);
        logger.info("Model {} unloaded successfully", getModelId());
    }

    @Override
    public ModelResponse generate(String prompt, Map<String, Object> parameters) {
        logger.debug("Generating response for prompt: {}", prompt);

        long startTime = System.currentTimeMillis();
        ModelResponse response = new ModelResponse(getModelId(), "chat");

        try {
            // 检查是否启用流式
            boolean isStreaming = parameters.containsKey("stream") && (Boolean) parameters.get("stream");

            // 构建请求
            Map<String, Object> requestBody = buildRequestBody(prompt, parameters);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            logger.debug("Sending request to {}: {}", apiEndpoint, requestJson);

            if (isStreaming) {
                // 流式响应处理
                StringBuilder fullContent = new StringBuilder();
                
                // 发送流式请求
                sendStreamingRequest(requestJson, chunkData -> {
                    try {
                        // 处理SSE格式 (data: {...})
                        if (chunkData.startsWith("data:")) {
                            chunkData = chunkData.substring(5).trim();
                        }
                        
                        // 跳过空行和结束标记
                        if (chunkData.isEmpty() || "[DONE]".equals(chunkData)) {
                            return;
                        }
                        
                        // 解析JSON
                        Map<String, Object> chunkMap = objectMapper.readValue(chunkData, Map.class);
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunkMap.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                            
                            if (delta != null && delta.containsKey("content")) {
                                String content = (String) delta.get("content");
                                fullContent.append(content);
                                
                                // 更新流式响应
                                response.setOutput(fullContent.toString());
                                response.setStreaming(true);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing streaming chunk", e);
                    }
                });

                response.setOutput(fullContent.toString());
                response.setSuccess(true);
                response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            } else {
                // 标准响应处理
                int retryCount = 0;
                String responseJson = null;
                boolean success = false;

                while (retryCount <= maxRetries && !success) {
                    try {
                        responseJson = sendRequest(requestJson);
                        success = true;
                    } catch (Exception e) {
                        retryCount++;
                        if (retryCount > maxRetries) {
                            throw e;
                        }
                        logger.warn("Request failed, retrying {}/{}: {}", retryCount, maxRetries, e.getMessage());
                        Thread.sleep(retryDelay * retryCount);
                    }
                }

                // 解析响应
                parseResponse(responseJson, response, startTime);
            }

        } catch (Exception e) {
            logger.error("Error generating response: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage("Generation failed: " + e.getMessage());
        }

        return response;
    }

    /**
     * 发送流式请求
     */
    private void sendStreamingRequest(String requestBody, Consumer<String> chunkConsumer) throws IOException {
        if (apiEndpoint == null) {
            throw new IllegalStateException("API endpoint is not configured");
        }

        HttpPost request = new HttpPost(apiEndpoint);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        // 设置认证头部
        if (apiKey != null && !apiKey.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + apiKey);
        }
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "text/event-stream");

        try (CloseableHttpResponse response = httpClient.execute(request);
             InputStream is = response.getEntity().getContent();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            int statusCode = response.getCode();
            if (statusCode >= 200 && statusCode < 300) {
                String line;
                while ((line = reader.readLine()) != null) {
                    chunkConsumer.accept(line);
                }
            } else {
                throw new IOException("HTTP request failed with status " + statusCode);
            }
        }
    }

    @Override
    public ModelResponse generate(String prompt) {
        return generate(prompt, new HashMap<>());
    }

    /**
     * 构建OpenAI兼容的请求体
     */
    private Map<String, Object> buildRequestBody(String prompt, Map<String, Object> parameters) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);

        // 构建消息格式 - Java 8兼容实现
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMessage);
        requestBody.put("messages", messages);

        // 添加参数
        Map<String, Object> mergedParams = new HashMap<>(defaultParameters);
        mergedParams.putAll(parameters);

        // 设置OpenAI API参数
        if (mergedParams.containsKey("max_tokens")) {
            requestBody.put("max_tokens", mergedParams.get("max_tokens"));
        }
        if (mergedParams.containsKey("temperature")) {
            requestBody.put("temperature", mergedParams.get("temperature"));
        }
        if (mergedParams.containsKey("top_p")) {
            requestBody.put("top_p", mergedParams.get("top_p"));
        }
        if (mergedParams.containsKey("frequency_penalty")) {
            requestBody.put("frequency_penalty", mergedParams.get("frequency_penalty"));
        }
        if (mergedParams.containsKey("presence_penalty")) {
            requestBody.put("presence_penalty", mergedParams.get("presence_penalty"));
        }
        if (mergedParams.containsKey("stream")) {
            requestBody.put("stream", mergedParams.get("stream"));
        }
        if (mergedParams.containsKey("stop")) {
            requestBody.put("stop", mergedParams.get("stop"));
        }

        return requestBody;
    }

    /**
     * 发送HTTP请求 - Java 8修正版本支持URL完全自定义
     * 不再自动追加/chat/completions后缀，完全尊重用户配置的URL
     *
     * 重要变更：现在支持完全自定义的URL配置:
     * - 标准OpenAI: https://api.openai.com/v1/chat/completions
     * - Azure OpenAI: https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions?api-version=2024-02-15-preview
     * - 本地部署: http://localhost:8000/v1/chat/completions
     * - 自定义API: https://custom.api.com/any/path/you/need
     */
    private String sendRequest(String requestBody) throws IOException {
        if (apiEndpoint == null) {
            throw new IllegalStateException("API endpoint is not configured");
        }

        // 现在使用用户配置的完整URL，不再自动添加任何后缀
        // 用户必须在配置中包含完整的API路径
        HttpPost request = new HttpPost(apiEndpoint);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        // 设置认证头部
        if (apiKey != null && !apiKey.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + apiKey);
        }
        request.setHeader("Content-Type", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseJson;
            try {
                responseJson = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response entity", e);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return responseJson;
            } else {
                throw new IOException("HTTP request failed with status " + statusCode + ": " + responseJson);
            }
        }
    }

    /**
     * 解析OpenAI响应
     */
    private void parseResponse(String responseJson, ModelResponse response, long startTime) throws IOException {
        logger.debug("Parsing response: {}", responseJson);

        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);

        // 检查响应格式
        if (!responseMap.containsKey("choices")) {
            throw new IOException("Invalid response format: missing 'choices' field");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("Empty choices in response");
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, String> message = (Map<String, String>) choice.get("message");
        if (message == null) {
            throw new IOException("Missing message in choice");
        }

        String content = message.get("content");
        if (content == null) {
            content = "";
        }

        response.setOutput(content.trim());
        response.setSuccess(true);
        response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        // 附加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("finish_reason", choice.get("finish_reason"));

        // 添加模型使用信息（如果有）
        if (responseMap.containsKey("usage")) {
            metadata.put("usage", responseMap.get("usage"));
        }

        // 添加模型信息
        if (responseMap.containsKey("model")) {
            metadata.put("response_model", responseMap.get("model"));
        }
        metadata.put("provider", provider);

        response.setMetadata(metadata);
    }

    /**
     * 测试API连接
     */
    private void testConnection() throws IOException {
        logger.info("Testing API connection to {}", apiEndpoint);

        try {
            ModelResponse response = generate("Hello, this is a connection test.");
            if (!response.isSuccess()) {
                throw new IOException("Connection test failed: " + response.getErrorMessage());
            }
            logger.info("API connection test successful");
        } catch (Exception e) {
            logger.error("API connection test failed", e);
            throw new IOException("Failed to connect to API endpoint: " + apiEndpoint, e);
        }
    }

    /**
     * 验证配置
     */
    private void validateConfiguration() throws IllegalStateException {
        if (apiEndpoint == null || apiEndpoint.trim().isEmpty()) {
            throw new IllegalStateException("API endpoint is not configured");
        }

        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalStateException("Model name is not configured");
        }

        // 验证API密钥格式（基本检查）
        if (apiKey != null && !apiKey.startsWith("sk-") && provider.equals("openai")) {
            logger.warn("API key doesn't look like a standard OpenAI key (should start with 'sk-')");
        }
    }

    // Setter 方法
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint.endsWith("/") ? apiEndpoint.substring(0, apiEndpoint.length() - 1) : apiEndpoint;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setDefaultParameters(Map<String, Object> defaultParameters) {
        this.defaultParameters = defaultParameters;
    }

    public void addDefaultParameter(String key, Object value) {
        this.defaultParameters.put(key, value);
    }

    public void setConnectionTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }
}