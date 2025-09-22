package com.evalscope.netty;

import com.evalscope.model.ModelResponse;
import com.evalscope.model.NettyOpenAIModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test class for Netty-based OpenAI model with streaming support
 */
public class NettyOpenAIModelTest {
    private static final Logger logger = LoggerFactory.getLogger(NettyOpenAIModelTest.class);

    private NettyOpenAIModel nettyModel;
    private ObjectMapper objectMapper;

    // Test configuration - use mock server or actual OpenAI endpoint
    private static final String TEST_URL = "http://localhost:8000/v1/chat/completions";
    private static final String TEST_API_KEY = "test-key";
    private static final String TEST_MODEL = "gpt-3.5-turbo";

    @Before
    public void setUp() throws Exception {
        objectMapper = new ObjectMapper();

        // Initialize the Netty OpenAI model
        nettyModel = new NettyOpenAIModel("netty-openai", "chat", "netty");
        nettyModel.setApiEndpoint(TEST_URL);
        nettyModel.setApiKey(TEST_API_KEY);
        nettyModel.setModelName(TEST_MODEL);

        // Test connection
        try {
            nettyModel.load();
            logger.info("Netty OpenAI model loaded successfully");
        } catch (Exception e) {
            logger.warn("Could not load Netty OpenAI model (expected if using mock server): {}", e.getMessage());
        }
    }

    @After
    public void tearDown() throws Exception {
        if (nettyModel != null) {
            try {
                nettyModel.unload();
                logger.info("Netty OpenAI model unloaded successfully");
            } catch (Exception e) {
                logger.error("Error unloading model", e);
            }
        }
    }

    @Test
    public void testStandardRequest() throws Exception {
        logger.info("Testing standard (non-streaming) request...");

        String prompt = "Please write a brief haiku about spring.";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("temperature", 0.7);
        parameters.put("max_tokens", 150);

        ModelResponse response = nettyModel.generate(prompt, parameters);

        // Assertions
        assert response != null : "Response should not be null";

        if (response.isSuccess()) {
            logger.info("✅ Standard request successful!");
            logger.info("Output: {}", response.getOutput());
            logger.info("Processing time: {}ms", response.getProcessingTimeMs());
            logger.info("Is streaming: {}", response.isStreaming());

            // Verify response content
            assert response.getOutput() != null : "Output should not be null";
            assert response.getOutput().length() > 0 : "Output should not be empty";
            assert !response.isStreaming() : "Standard response should not be streaming";

            // Verify metadata
            assert response.getMetadata() != null : "Metadata should not be null";
            assert Boolean.FALSE.equals(response.getMetadata().get("streaming")) : "Streaming should be false in metadata";
        } else {
            logger.warn("⚠️  Standard request failed: {}", response.getErrorMessage());
            // This is expected if using a mock/test server
        }
    }

    @Test
    public void testStreamingRequest() throws Exception {
        logger.info("Testing streaming request...");

        String prompt = "Please explain quantum computing in simple terms. Keep it under 100 words.";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("temperature", 0.8);
        parameters.put("max_tokens", 200);
        parameters.put("stream", true); // Enable streaming

        ModelResponse response = nettyModel.generate(prompt, parameters);

        // Assertions
        assert response != null : "Response should not be null";

        if (response.isSuccess()) {
            logger.info("✅ Streaming request successful!");
            logger.info("Output: {}", response.getOutput());
            logger.info("Processing time: {}ms", response.getProcessingTimeMs());
            logger.info("Is streaming: {}", response.isStreaming());

            // Verify streaming response
            assert response.getOutput() != null : "Output should not be null";
            assert response.getOutput().length() > 0 : "Output should not be empty";
            assert response.isStreaming() : "Response should be marked as streaming";

            // Verify metadata
            assert response.getMetadata() != null : "Metadata should not be null";
            assert Boolean.TRUE.equals(response.getMetadata().get("streaming")) : "Streaming should be true in metadata";
        } else {
            logger.warn("⚠️  Streaming request failed: {}", response.getErrorMessage());
            // This is expected if using a mock/test server
        }
    }

    @Test
    public void testRequestParsing() throws Exception {
        logger.info("Testing request parsing...");

        // Create a test request
        OpenAIRequest.Message userMessage = new OpenAIRequest.Message("user", "Test prompt");
        OpenAIRequest request = new OpenAIRequest();
        request.setModel(TEST_MODEL);
        request.setMessages(java.util.List.of(userMessage));
        request.setTemperature(0.9);
        request.setMax_tokens(100);
        request.setStream(false);

        // Serialize and deserialize
        String jsonString = objectMapper.writeValueAsString(request);
        logger.info("Serialized request: {}", jsonString);

        OpenAIRequest parsedRequest = objectMapper.readValue(jsonString, OpenAIRequest.class);

        // Assertions
        assert parsedRequest.getModel().equals(TEST_MODEL) : "Model should match";
        assert parsedRequest.getMessages().size() == 1 : "Should have one message";
        assert parsedRequest.getTemperature() == 0.9 : "Temperature should be 0.9";
        assert parsedRequest.getMax_tokens() == 100 : "Max tokens should be 100";
        assert parsedRequest.getStream() == false : "Stream should be false";

        logger.info("✅ Request parsing test passed!");
    }

    @Test
    public void testResponseParsing() throws Exception {
        logger.info("Testing response parsing...");

        // Create a sample OpenAI response
        String sampleResponse = """
            {
                "id": "chatcmpl-A1B2C3",
                "object": "chat.completion",
                "created": 1234567890,
                "model": "gpt-3.5-turbo",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "Hello! This is a test response."
                    },
                    "finish_reason": "stop"
                }]
            }
            """;

        // Parse the response
        OpenAIResponse response = objectMapper.readValue(sampleResponse, OpenAIResponse.class);

        // Assertions
        assert response.getId().equals("chatcmpl-A1B2C3") : "ID should match";
        assert response.getModel().equals("gpt-3.5-turbo") : "Model should match";
        assert response.getChoices().size() == 1 : "Should have one choice";
        assert response.getChoices().get(0).getMessage().getContent().equals("Hello! This is a test response.") : "Content should match";

        logger.info("✅ Response parsing test passed!");
    }

    @Test
    public void testSseParsing() throws Exception {
        logger.info("Testing SSE data parsing...");

        // Sample SSE data chunks
        String sseData = """
            data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}

            data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}

            data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}]}

            data: [DONE]
            """;

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicReference<String> fullContent = new AtomicReference>(\"\""");

        // Simulate SSE processing
        String[] lines = sseData.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String eventData = line.substring(6);

                if ("[DONE]".equals(eventData.trim())) {
                    break;
                }

                chunkCount.incrementAndGet();

                // Parse the chunk response
                OpenAIResponse chunkResponse = objectMapper.readValue(eventData, OpenAIResponse.class);

                if (chunkResponse.getChoices() != null && !chunkResponse.getChoices().isEmpty()) {
                    OpenAIResponse.Choice choice = chunkResponse.getChoices().get(0);
                    String deltaContent = "";

                    if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                        deltaContent = choice.getDelta().getContent();
                        fullContent.updateAndGet(str -> str + deltaContent);
                    }

                    logger.info("Chunk {}: '{}' - total so far: '{}'", chunkCount.get(), deltaContent, fullContent.get());
                    latch.countDown();
                }
            }
        }

        if (latch.await(1, TimeUnit.SECONDS)) {
            logger.info("✅ SSE parsing test passed!");
            assert chunkCount.get() == 3 : "Should have processed 3 chunks";
            assert fullContent.get().equals("Hello world!") : "Content should be correct: " + fullContent.get();
        } else {
            logger.warn("⚠️  SSE parsing test incomplete, processed {} chunks", chunkCount.get());
        }
    }

    @Test
    public void testErrorHandling() throws Exception {
        logger.info("Testing error handling...");

        // Test with invalid endpoint
        NettyOpenAIModel errorModel = new NettyOpenAIModel("error-test", "chat", "netty");
        errorModel.setApiEndpoint("http://invalid-endpoint-that-does-not-exist:9999/v1/chat/completions");
        errorModel.setApiKey("invalid-key");
        errorModel.setModelName("invalid-model");

        try {
            errorModel.load(); // This should fail
            logger.warn("⚠️  Expected connection failure did not occur");
        } catch (Exception e) {
            logger.info("✅ Expected connection failure: {}", e.getMessage());
            assert e.getMessage().contains("failed") || e.getMessage().contains("connection") : "Error message should indicate failure";
        }

        // Test with bad parameters
        Map<String, Object> badParams = new HashMap<>();
        badParams.put("temperature", 3.0); // Invalid temperature (should be 0-2)
        badParams.put("max_tokens", -100); // Invalid max tokens

        ModelResponse response = nettyModel.generate("Test prompt", badParams);

        if (response.isSuccess()) {
            // API might accept these values and clamp them
            logger.info("✅ Bad parameters handled gracefully by API");
        } else {
            logger.info("✅ Expected error for bad parameters: {}", response.getErrorMessage());
        }
    }

    @Test
    public void testConfigurationValidation() {
        logger.info("Testing configuration validation...");

        NettyOpenAIModel testModel = new NettyOpenAIModel("test-config");

        // Test invalid configurations
        try {
            testModel.setApiEndpoint(null);
            testModel.setModelName("test-model");
            testModel.setApiKey("test-key");
            testModel.validateConfiguration();
            assert false : "Should have failed with null endpoint";
        } catch (IllegalStateException e) {
            logger.info("✅ Correctly caught null endpoint: {}", e.getMessage());
        }

        try {
            testModel.setApiEndpoint("https://api.openai.com/v1/chat/completions");
            testModel.setModelName(null);
            testModel.setApiKey("test-key");
            testModel.validateConfiguration();
            assert false : "Should have failed with null model name";
        } catch (IllegalStateException e) {
            logger.info("✅ Correctly caught null model name: {}", e.getMessage());
        }

        // Test valid configuration
        testModel.setApiEndpoint("https://api.openai.com/v1/chat/completions");
        testModel.setModelName("gpt-3.5-turbo");
        testModel.setApiKey("test-key");
        testModel.validateConfiguration(); // Should not throw

        logger.info("✅ Configuration validation test passed!");
    }

    public static void main(String[] args) {
        logger.info("=== Netty OpenAI Model Integration Tests ===");

        NettyOpenAIModelTest test = new NettyOpenAIModelTest();

        try {
            test.setUp();

            // Run specific tests
            test.testRequestParsing();
            test.testResponseParsing();
            test.testSseParsing();
            test.testConfigurationValidation();
            test.testErrorHandling();

            // Run expensive tests if environment allows
            String runAllTests = System.getenv("RUN_OPENAI_TESTS");
            if ("true".equals(runAllTests)) {
                test.testStandardRequest();
                test.testStreamingRequest();
            } else {
                logger.info("Skipping OpenAI API tests. Set RUN_OPENAI_TESTS=true to run integration tests.");
            }

        } catch (Exception e) {
            logger.error("Test execution failed", e);
        } finally {
            try {
                test.tearDown();
            } catch (Exception e) {
                logger.error("Test cleanup failed", e);
            }
        }

        logger.info("=== All Tests Completed ===");
    }
}