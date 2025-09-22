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
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * HuggingFace Hub模型实现
 * 支持HuggingFace的Inference API和Transformers模型
 */
public class HuggingFaceModel extends ChatModel {
    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceModel.class);

    private String modelName;
    private String apiToken;
    private ObjectMapper objectMapper;
    private CloseableHttpClient httpClient;
    private Map<String, Object> defaultParameters;

    // HF API配置
    private String hfApiEndpoint = "https://api-inference.huggingface.co";
    private int connectTimeout = 30;
    private int readTimeout = 120; // HF模型可能加载时间较长
    private int maxRetries = 3;
    private long retryDelay = 2000; // ms

    public HuggingFaceModel(String modelId, String modelType) {
        super(modelId, modelType);
        this.objectMapper = new ObjectMapper();
        this.defaultParameters = new HashMap<>();

        initializeHttpClient();
    }

    public HuggingFaceModel(String modelId) {
        this(modelId, "chat", null);
    }

    public HuggingFaceModel(String modelId, String modelType, String apiToken) {
        this(modelId, modelType);
        this.apiToken = apiToken;
    }

    private void initializeHttpClient() {
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
        logger.info("Loading HuggingFace Model: {}", getModelId());

        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalStateException("Model name is required for HuggingFace models");
        }

        // 测试模型可用性
        testModelAvailability();

        setLoaded(true);
        logger.info("HuggingFace Model {} loaded successfully", getModelId());
    }

    @Override
    public void unload() throws Exception {
        logger.info("Unloading HuggingFace Model: {}", getModelId());

        if (httpClient != null) {
            httpClient.close();
        }

        setLoaded(false);
        logger.info("HuggingFace Model {} unloaded successfully", getModelId());
    }

    @Override
    public ModelResponse generate(String prompt, Map<String, Object> parameters) {
        logger.debug("Generating HF response for prompt: {}", prompt);

        long startTime = System.currentTimeMillis();
        ModelResponse response = new ModelResponse(getModelId(), getModelType());

        try {
            // 根据不同任务类型选择不同的调用方法
            if ("chat".equals(getModelType()) || "text_generation".equals(getModelType())) {
                return generateText(prompt, parameters, startTime);
            } else if ("question_answering".equals(getModelType())) {
                return generateAnswer(prompt, parameters, startTime);
            } else {
                // 使用通用的文本生成
                return generateText(prompt, parameters, startTime);
            }
        } catch (Exception e) {
            logger.error("Error generating HF response: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage("HF generation failed: " + e.getMessage());
            return response;
        }
    }

    @Override
    public ModelResponse generate(String prompt) {
        return generate(prompt, new HashMap<>());
    }

    private ModelResponse generateText(String prompt, Map<String, Object> parameters, long startTime)
            throws IOException {

        ModelResponse response = new ModelResponse(getModelId(), getModelType());

        // 构建HF文本生成请求
        Map<String, Object> requestBody = buildTextGenerationRequest(prompt, parameters);
        String endpoint = hfApiEndpoint + "/models/" + modelName;

        String responseJson = sendHFRequest(endpoint, requestBody);
        parseTextGenerationResponse(responseJson, response, startTime);

        return response;
    }

    private ModelResponse generateAnswer(String prompt, Map<String, Object> parameters, long startTime)
            throws IOException {

        ModelResponse response = new ModelResponse(getModelId(), getModelType());

        // 构建问答请求
        Map<String, Object> requestBody = buildQuestionAnsweringRequest(prompt, parameters);
        String endpoint = hfApiEndpoint + "/models/" + modelName;

        String responseJson = sendHFRequest(endpoint, requestBody);
        parseQuestionAnsweringResponse(responseJson, response, startTime);

        return response;
    }

    private Map<String, Object> buildTextGenerationRequest(String prompt, Map<String, Object> parameters) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", prompt);

        // 构建参数
        Map<String, Object> options = new HashMap<>();
        Map<String, Object> mergedParams = new HashMap<>(defaultParameters);
        mergedParams.putAll(parameters);

        if (mergedParams.containsKey("max_tokens")) {
            options.put("max_new_tokens", mergedParams.get("max_tokens"));
        }
        if (mergedParams.containsKey("temperature")) {
            options.put("temperature", mergedParams.get("temperature"));
        }
        if (mergedParams.containsKey("top_p")) {
            options.put("top_p", mergedParams.get("top_p"));
        }
        if (mergedParams.containsKey("top_k")) {
            options.put("top_k", mergedParams.get("top_k"));
        }
        if (mergedParams.containsKey("do_sample")) {
            options.put("do_sample", mergedParams.get("do_sample"));
        }

        if (!options.isEmpty()) {
            requestBody.put("parameters", options);
        }

        return requestBody;
    }

    private Map<String, Object> buildQuestionAnsweringRequest(String prompt, Map<String, Object> parameters) {
        Map<String, Object> requestBody = new HashMap<>();

        // 对于问答任务，需要提供question和context
        String[] parts = prompt.split("\n\n?context:", 2);
        String question = parts[0].replaceFirst("(?i)^(question|what|how|why|when|where):?[ ]*-", "");

        if (parts.length > 1) {
            Map<String, String> inputs = new HashMap<>();
            inputs.put("question", question.trim());
            inputs.put("context", parts[1].trim());
            requestBody.put("inputs", inputs);
        } else {
            // 如果没有明确的context，将整个prompt作为question
            Map<String, String> inputs = new HashMap<>();
            inputs.put("question", question.trim());
            requestBody.put("inputs", inputs);
        }

        parameters.forEach(requestBody::putIfAbsent);

        return requestBody;
    }

    private String sendHFRequest(String endpoint, Map<String, Object> requestBody) throws IOException {
        HttpPost request = new HttpPost(endpoint);
        String requestJson = objectMapper.writeValueAsString(requestBody);
        request.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

        // 设置认证头部
        if (apiToken != null && !apiToken.trim().isEmpty()) {
            request.setHeader("Authorization", "Bearer " + apiToken);
        }
        request.setHeader("Content-Type", "application/json");

        logger.debug("Sending HF request to {}: {}", endpoint, requestJson);

        int retryCount = 0;
        while (retryCount <= maxRetries) {
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
                } else if (statusCode == 503) {
                    // HF模型可能还在加载，需要等待
                    logger.warn("HF model is loading, waiting... (retry {})", retryCount);
                    retryCount++;
                    try {
                        Thread.sleep(retryDelay * retryCount);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Waiting interrupted", e);
                    }
                } else {
                    throw new IOException("HF API request failed with status " + statusCode + ": " + responseJson);
                }
            }
        }

        throw new IOException("Failed to get response from HF API after " + maxRetries + " retries");
    }

    private void parseTextGenerationResponse(String responseJson, ModelResponse response, long startTime)
            throws IOException {

        try {
            // HF文本生成响应通常是数组
            List<Map<String, Object>> responses = objectMapper.readValue(responseJson, List.class);

            if (responses != null && !responses.isEmpty()) {
                Map<String, Object> firstResponse = responses.get(0);

                String generatedText = (String) firstResponse.get("generated_text");
                if (generatedText != null) {
                    response.setOutput(generatedText.trim());
                    response.setSuccess(true);
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                    // 添加元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("finish_reason", firstResponse.get("finish_reason"));

                    long[] tokenCount = countTokens(generatedText);
                    metadata.put("tokens_generated", tokenCount[0]);

                    response.setMetadata(metadata);
                } else {
                    response.setSuccess(false);
                    response.setErrorMessage("No generated text in HF response");
                }
            } else {
                response.setSuccess(false);
                response.setErrorMessage("Empty HF response");
            }
        } catch (Exception e) {
            // 尝试按简单字符串解析
            try {
                String generatedText = objectMapper.readValue(responseJson, String.class);
                response.setOutput(generatedText.trim());
                response.setSuccess(true);
                response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                throw new IOException("Failed to parse HF text generation response: " + responseJson, e);
            }
        }
    }

    private void parseQuestionAnsweringResponse(String responseJson, ModelResponse response, long startTime)
            throws IOException {

        try {
            // HF问答API响应格式
            Map<String, Object> qaResponse = objectMapper.readValue(responseJson, Map.class);

            if (qaResponse.containsKey("answer")) {
                String answer = (String) qaResponse.get("answer");
                response.setOutput(answer.trim());
                response.setSuccess(true);
                response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                // 添加问答相关的元数据
                Map<String, Object> metadata = new HashMap<>();
                if (qaResponse.containsKey("score")) {
                    metadata.put("answer_score", qaResponse.get("score"));
                }
                if (qaResponse.containsKey("start")) {
                    metadata.put("answer_start", qaResponse.get("start"));
                }
                if (qaResponse.containsKey("end")) {
                    metadata.put("answer_end", qaResponse.get("end"));
                }

                response.setMetadata(metadata);
            } else {
                response.setSuccess(false);
                response.setErrorMessage("No answer in HF QA response");
            }
        } catch (Exception e) {
            // 尝试按简单字符串解析（某些模型可能直接返回字符串）
            try {
                String answer = objectMapper.readValue(responseJson, String.class);
                response.setOutput(answer.trim());
                response.setSuccess(true);
                response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("response_type", "single_answer");
                response.setMetadata(metadata);
            } catch (Exception ex) {
                throw new IOException("Failed to parse HF QA response: " + responseJson, e);
            }
        }
    }

    private void testModelAvailability() throws IOException {
        logger.info("Testing HuggingFace model availability: {}", modelName);

        try {
            // 使用简单查询测试模型
            Map<String, Object> testParams = new HashMap<>();
            testParams.put("max_tokens", 10);
            ModelResponse response = generate("Hi", testParams);
            if (!response.isSuccess()) {
                throw new IOException("Model availability test failed: " + response.getErrorMessage());
            }
            logger.info("HuggingFace model {} is available and working", modelName);
        } catch (Exception e) {
            logger.error("HuggingFace model availability test failed", e);
            throw new IOException("Model " + modelName + " is not available or not working properly: " + e.getMessage(), e);
        }
    }

    private long[] countTokens(String text) {
        // 简单的token计数（估算）- 真实实现可能需要使用HF的tokenizer
        String[] words = text.split("\\s+");
        long wordCount = words.length;
        long charCount = text.length();
        long estimatedTokens = Math.max(wordCount * 4 / 3, charCount / 4); // 粗略估算

        return new long[]{estimatedTokens, charCount, wordCount};
    }

    // Setter和Getter方法
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public void setHfApiEndpoint(String hfApiEndpoint) {
        this.hfApiEndpoint = hfApiEndpoint.endsWith("/") ?
            hfApiEndpoint.substring(0, hfApiEndpoint.length() - 1) : hfApiEndpoint;
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

    public void setDefaultParameters(Map<String, Object> defaultParameters) {
        this.defaultParameters = defaultParameters;
    }

    public void addDefaultParameter(String key, Object value) {
        this.defaultParameters.put(key, value);
    }

    public String getModelName() {
        return modelName;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getHfApiEndpoint() {
        return hfApiEndpoint;
    }
}