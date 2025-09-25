package com.evalscope.model;

import com.evalscope.model.openai.OpenAIRequest;
import com.evalscope.model.openai.OpenAIResponse;
import com.evalscope.netty.NettyHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Netty-based OpenAI Compatible Chat Model Implementation
 * Supports both standard and streaming response modes
 */
public class NettyOpenAIModel extends ChatModel {
    private static final Logger logger = LoggerFactory.getLogger(NettyOpenAIModel.class);

    private String apiEndpoint;
    private String apiKey;
    private String modelName;
    private final ObjectMapper objectMapper;
    private NettyHttpClient httpClient;
    private Map<String, Object> defaultParameters;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public NettyOpenAIModel(String modelId, String modelType, String provider) {
        super(modelId, modelType);
        this.objectMapper = new ObjectMapper();
        this.defaultParameters = new HashMap<>();
    }

    public NettyOpenAIModel(String modelId) {
        this(modelId, "chat", "openai");
    }

    @Override
    public void load() throws Exception {
        logger.info("Loading Netty OpenAI Model: {}", getModelId());

        validateConfiguration();
        initializeHttpClient();
        testConnection();

        setLoaded(true);
        logger.info("Model {} loaded successfully", getModelId());
    }

    @Override
    public void unload() throws Exception {
        logger.info("Unloading Netty OpenAI Model: {}", getModelId());

        if (httpClient != null) {
            httpClient.shutdown();
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
            // Check if streaming is enabled
            boolean isStreaming = parameters.containsKey("stream") && (Boolean) parameters.get("stream");

            if (isStreaming) {
                return generateStreamingResponse(prompt, parameters, startTime);
            } else {
                return generateStandardResponse(prompt, parameters, startTime);
            }

        } catch (Exception e) {
            logger.error("Error generating response: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage("Generation failed: " + e.getMessage());
        }

        return response;
    }

    @Override
    public ModelResponse generate(String prompt) {
        return generate(prompt, new HashMap<>());
    }

    /**
     * Generate streaming response with real-time processing
     */
    private ModelResponse generateStreamingResponse(String prompt, Map<String, Object> parameters, long startTime) throws Exception {
        logger.debug("Generating streaming response for prompt: {}", prompt);

        // Create streaming response
        ModelResponse response = new ModelResponse(getModelId(), "chat");
        response.setMetadata(new HashMap<>());
        response.setStreaming(true);

        // Build request
        OpenAIRequest request = buildOpenAIRequest(prompt, parameters, true);
        request.setStream(true);

        String requestJson = objectMapper.writeValueAsString(request);
        logger.debug("Sending streaming request: {}", requestJson);

        // Set up streaming consumers
        StringBuilder fullContent = new StringBuilder();
        int[] tokenCount = {0};

        Consumer<String> chunkConsumer = chunkData -> {
            try {
                // Handle OpenAI streaming format (data: {...})
                if (chunkData.startsWith("data:")) {
                    chunkData = chunkData.substring(5).trim();
                }
                // Skip [DONE] and empty lines
                if (chunkData.isEmpty() || "[DONE]".equals(chunkData)) {
                    return;
                }
                // Parse JSON chunk
                OpenAIResponse chunkResponse = objectMapper.readValue(chunkData, OpenAIResponse.class);

                if (chunkResponse.getChoices() != null && !chunkResponse.getChoices().isEmpty()) {
                    OpenAIResponse.Choice choice = chunkResponse.getChoices().get(0);

                    // Extract delta content
                    String deltaContent = "";
                    if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                        deltaContent = choice.getDelta().getContent();
                    }

                    // Add to full content
                    fullContent.append(deltaContent);
                    int currentCount = fullContent.length() / 4; // Rough token estimation

                    // Create streaming update
                    Map<String, Object> update = new HashMap<>();
                    update.put("type", "delta");
                    update.put("delta", deltaContent);
                    update.put("partial_content", fullContent.toString());
                    update.put("total_tokens", currentCount);
                    update.put("choice_index", choice.getIndex());

                    // Set finish reason if available
                    if (choice.getFinish_reason() != null) {
                        update.put("finish_reason", choice.getFinish_reason());
                    }

                    // Notify streaming listener (this would typically be a callback)
                    logger.debug("Streaming chunk: {} tokens", currentCount);

                    // Handle stream completion
                    if ("[DONE]".equals(chunkData.trim()) ||
                        (choice.getFinish_reason() != null && !choice.getFinish_reason().isEmpty())) {
                        update.put("type", "complete");
                    }
                }

            } catch (Exception e) {
                logger.error("Error processing streaming chunk", e);
            }
        };

        Consumer<Throwable> errorConsumer = error -> {
            logger.error("Streaming error", error);
            response.setSuccess(false);
            response.setErrorMessage("Streaming error: " + error.getMessage());
            synchronizeIncompleteResponse(response, fullContent.toString(), startTime);
        };

        // Send streaming request with retries
        CompletableFuture<Void> streamingFuture = CompletableFuture.runAsync(() -> {
            int retryCount = 0;
            boolean success = false;

            while (retryCount < MAX_RETRIES && !success) {
                try {
                    CompletableFuture<Void> requestFuture = httpClient.sendStreamingRequest(
                        apiEndpoint,
                        requestJson,
                        "Bearer " + apiKey,
                        chunkConsumer,
                        errorConsumer
                    );

                   // Java 8 compatible timeout - use get with timeout instead of orTimeout
                    try {
                        requestFuture.get(90, TimeUnit.SECONDS);
                    } catch (java.util.concurrent.TimeoutException e) {
                        logger.error("Streaming request timed out after 90 seconds", e);
                        response.setSuccess(false);
                        response.setErrorMessage("Streaming request timed out after 90 seconds");
                        return;
                    }

                    success = true;

                    // Set final response
                    response.setOutput(fullContent.toString().trim());
                    response.setSuccess(true);
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                    // Add metadata
                    response.getMetadata().put("finish_reason", "stop");
                    response.getMetadata().put("streaming", true);
                    response.getMetadata().put("total_tokens", fullContent.length() / 4);

                } catch (Exception e) {
                    retryCount++;
                    if (retryCount > MAX_RETRIES) {
                        throw new RuntimeException("Max retries exceeded for streaming request", e);
                    }

                    logger.warn("Streaming request failed, retrying {}/{}: {}", retryCount, MAX_RETRIES, e.getMessage());

                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Streaming interrupted", ie);
                    }
                }
            }
        });

        // Try to complete synchronously but allow for async completion
        try {
            streamingFuture.get(120, TimeUnit.SECONDS); // Wait for streaming to complete
        } catch (Exception e) {
            logger.error("Streaming future completion error", e);
            response.setSuccess(false);
            response.setErrorMessage("Streaming wait failed: " + e.getMessage());
            synchronizeIncompleteResponse(response, fullContent.toString(), startTime);
        }

        return response;
    }

    /**
     * Generate standard (non-streaming) response
     */
    private ModelResponse generateStandardResponse(String prompt, Map<String, Object> parameters, long startTime) throws Exception {
        // Build request
        OpenAIRequest request = buildOpenAIRequest(prompt, parameters, false);
        String requestJson = objectMapper.writeValueAsString(request);

        logger.debug("Sending standard request: {}", requestJson);

        // Send request with retries
        String responseJson = null;
        int retryCount = 0;
        boolean success = false;

        while (retryCount < MAX_RETRIES && !success) {
            try {
                responseJson = sendRequest(requestJson).get(60, TimeUnit.SECONDS);
                success = true;
            } catch (Exception e) {
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    throw e;
                }

                logger.warn("Request failed, retrying {}/{}: {}", retryCount, MAX_RETRIES, e.getMessage());
                Thread.sleep(RETRY_DELAY_MS * retryCount);
            }
        }

        // Parse response
        return parseStandardResponse(responseJson, startTime);
    }

    private CompletableFuture<String> sendRequest(String requestJson) {
        return httpClient.sendRequest(apiEndpoint, requestJson, "Bearer " + apiKey);
    }

    private OpenAIRequest buildOpenAIRequest(String prompt, Map<String, Object> parameters, boolean streaming) {
        OpenAIRequest request = new OpenAIRequest();
        request.setModel(modelName);
        request.setStream(streaming);

        // Build message
        OpenAIRequest.Message userMessage = new OpenAIRequest.Message("user", prompt);
        List<OpenAIRequest.Message> messages = new ArrayList<>();
        messages.add(userMessage);
        request.setMessages(messages);

        // Merge parameters
        Map<String, Object> mergedParams = new HashMap<>(defaultParameters);
        mergedParams.putAll(parameters);

        // Set OpenAI parameters
        if (mergedParams.containsKey("max_tokens")) {
            request.setMax_tokens((Integer) mergedParams.get("max_tokens"));
        }
        if (mergedParams.containsKey("temperature")) {
            request.setTemperature((Double) mergedParams.get("temperature"));
        }
        if (mergedParams.containsKey("top_p")) {
            request.setTop_p((Double) mergedParams.get("top_p"));
        }
        if (mergedParams.containsKey("frequency_penalty")) {
            request.setFrequency_penalty((Double) mergedParams.get("frequency_penalty"));
        }
        if (mergedParams.containsKey("presence_penalty")) {
            request.setPresence_penalty((Double) mergedParams.get("presence_penalty"));
        }
        if (mergedParams.containsKey("stop")) {
            request.setStop((String) mergedParams.get("stop"));
        }

        return request;
    }

    private ModelResponse parseStandardResponse(String responseJson, long startTime) throws Exception {
        OpenAIResponse apiResponse = objectMapper.readValue(responseJson, OpenAIResponse.class);

        ModelResponse response = new ModelResponse(getModelId(), "chat");

        if (apiResponse.getChoices() != null && !apiResponse.getChoices().isEmpty()) {
            OpenAIResponse.Choice choice = apiResponse.getChoices().get(0);
            OpenAIResponse.Choice.Message message = choice.getMessage();

            String content = message != null && message.getContent() != null ? message.getContent() : "";
            response.setOutput(content.trim());
            response.setSuccess(true);
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("finish_reason", choice.getFinish_reason());
            metadata.put("model", apiResponse.getModel());
            metadata.put("response_id", apiResponse.getId());
            metadata.put("streaming", false);

            if (apiResponse.getUsage() != null) {
                metadata.put("usage", apiResponse.getUsage());
            }

            response.setMetadata(metadata);
        } else {
            throw new RuntimeException("Empty or invalid response from OpenAI API");
        }

        return response;
    }

    private void synchronizeIncompleteResponse(ModelResponse response, String partialContent, long startTime) {
        response.setOutput(partialContent.trim());
        response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        if (response.getMetadata() == null) {
            response.setMetadata(new HashMap<>());
        }
        response.getMetadata().put("finish_reason", "streaming_error");
        response.getMetadata().put("incomplete", true);
    }

    private void initializeHttpClient() throws Exception {
        if (httpClient != null) {
            httpClient.shutdown();
        }

        SslContext sslContext = SslContextBuilder.forClient()
                .build();

        httpClient = new NettyHttpClient(sslContext);
        logger.info("Netty HTTP client initialized successfully");
    }

    private void testConnection() throws Exception {
        logger.info("Testing API connection to {}", apiEndpoint);

        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("stream", false);
            ModelResponse response = generate("Hello, this is a connection test.", params);
            if (!response.isSuccess()) {
                throw new RuntimeException("Connection test failed: " + response.getErrorMessage());
            }
            logger.info("API connection test successful");
        } catch (Exception e) {
            logger.error("API connection test failed", e);
            throw new RuntimeException("Failed to connect to API endpoint: " + apiEndpoint, e);
        }
    }

    private void validateConfiguration() {
        if (apiEndpoint == null || apiEndpoint.trim().isEmpty()) {
            throw new IllegalStateException("API endpoint is not configured");
        }

        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalStateException("Model name is not configured");
        }

        if (apiKey != null && !apiKey.startsWith("sk-") && apiKey.startsWith("sk")) {
            logger.warn("API key doesn't look like a standard OpenAI key (should start with 'sk-')");
        }
    }

    // Setter methods
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint.endsWith("/") ?
            apiEndpoint.substring(0, apiEndpoint.length() - 1) : apiEndpoint;
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

    public String getProvider() {
        return "netty-openai";
    }

    public String getModelName() {
        return modelName;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }
}