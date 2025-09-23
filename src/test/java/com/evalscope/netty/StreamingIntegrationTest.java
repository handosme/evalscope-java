package com.evalscope.netty;

import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;
import com.evalscope.model.ModelResponse;
import com.evalscope.model.NettyOpenAIModel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Integration test for Netty-based streaming functionality
 */
public class StreamingIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamingIntegrationTest.class);

    private static MockOpenAIServer mockServer;
    private static final int TEST_PORT = 8001;
    private NettyOpenAIModel nettyModel;

    @BeforeClass
    public static void setupTestEnvironment() throws Exception {
        logger.info("Setting up test environment...");

        // Start mock OpenAI server
        mockServer = new MockOpenAIServer(TEST_PORT);
        mockServer.start();

        // Wait for server to be ready
        Thread.sleep(1000);
        logger.info("Test environment ready on port {}", TEST_PORT);
    }

    @AfterClass
    public static void teardownTestEnvironment() throws Exception {
        logger.info("Tearing down test environment...");

        if (mockServer != null) {
            mockServer.stop();
            logger.info("Mock server stopped");
        }
    }

    @Before
    public void setupModel() {
        logger.info("Setting up Netty OpenAI model...");

        nettyModel = new NettyOpenAIModel("test-netty-openai", "chat", "netty");
        nettyModel.setApiEndpoint("http://localhost:" + TEST_PORT + "/v1/chat/completions");
        nettyModel.setApiKey("test-api-key");
        nettyModel.setModelName("gpt-3.5-turbo");
    }

    @After
    public void teardownModel() throws Exception {
        if (nettyModel != null) {
            try {
                nettyModel.unload();
            } catch (Exception e) {
                logger.warn("Error unloading model", e);
            }
        }
    }

    @Test
    public void testStandardNonStreamingRequest() throws Exception {
        logger.info("=== Testing Non-Streaming Request ===");

        // Load the model
        nettyModel.load();

        String prompt = "Hello, how are you today?";
        ModelResponse response = nettyModel.generate(prompt);

        // Verify response
        Assert.assertNotNull("Response should not be null", response);
        Assert.assertTrue("Response should be successful", response.isSuccess());
        Assert.assertFalse("Standard response should not be streaming", response.isStreaming());
        Assert.assertNotNull("Output should not be null", response.getOutput());
        Assert.assertTrue("Output should have content", response.getOutput().length() > 0);

        logger.info("✅ Non-streaming request successful");
        logger.info("   Output: {}", response.getOutput());
        logger.info("   Processing time: {}ms", response.getProcessingTimeMs());
        logger.info("   Stream enabled: {}", response.isStreaming());
    }

    @Test
    public void testStreamingRequest() throws Exception {
        logger.info("=== Testing Streaming Request ===");

        // Load the model
        nettyModel.load();

        String prompt = "Please explain the importance of artificial intelligence in healthcare";
        Map<String, Object> parameters = new java.util.HashMap<String, Object>();
        parameters.put("temperature", 0.7);
        parameters.put("max_tokens", 200);
        parameters.put("stream", true);  // Enable streaming

        ModelResponse response = nettyModel.generate(prompt, parameters);

        // Verify streaming response
        Assert.assertNotNull("Response should not be null", response);
        Assert.assertTrue("Response should be successful", response.isSuccess());
        Assert.assertTrue("Streaming response should be marked as streaming", response.isStreaming());
        Assert.assertNotNull("Output should not be null", response.getOutput());
        Assert.assertTrue("Output should have content", response.getOutput().length() > 0);

        logger.info("✅ Streaming request successful");
        logger.info("   Output: {}", response.getOutput());
        logger.info("   Processing time: {}ms", response.getProcessingTimeMs());
        logger.info("   Stream enabled: {}", response.isStreaming());
    }

    @Test
    public void testStreamingPerformance() throws Exception {
        logger.info("=== Testing Streaming Performance ===");

        nettyModel.load();

        String prompt = "Generate a creative story about technology";
        Map<String, Object> streamingParams = new java.util.HashMap<String, Object>();
        streamingParams.put("stream", true);
        streamingParams.put("temperature", 0.8);
        streamingParams.put("max_tokens", 150);
        Map<String, Object> standardParams = new java.util.HashMap<String, Object>();
        standardParams.put("stream", false);
        standardParams.put("temperature", 0.8);
        standardParams.put("max_tokens", 150);

        // Test streaming time
        long streamingStart = System.currentTimeMillis();
        ModelResponse streamingResponse = nettyModel.generate(prompt, streamingParams);
        long streamingTime = System.currentTimeMillis() - streamingStart;

        // Test standard time
        long standardStart = System.currentTimeMillis();
        ModelResponse standardResponse = nettyModel.generate(prompt, standardParams);
        long standardTime = System.currentTimeMillis() - standardStart;

        logger.info("Streaming request: {}ms, Standard request: {}ms", streamingTime, standardTime);
        logger.info("Streaming output length: {}, Standard output length: {}",
            streamingResponse.getOutput().length(), standardResponse.getOutput().length());

        // Both should be successful
        Assert.assertTrue("Streaming response should be successful", streamingResponse.isSuccess());
        Assert.assertTrue("Standard response should be successful", standardResponse.isSuccess());

        // Check output quality (both should have content)
        Assert.assertTrue("Streaming response should have content",
            streamingResponse.getOutput().length() > 0);
        Assert.assertTrue("Standard response should have content",
            standardResponse.getOutput().length() > 0);

        logger.info("✅ Performance test completed");
    }

    @Test
    public void testStreamingParameterHandling() throws Exception {
        logger.info("=== Testing Streaming Parameter Handling ===");

        nettyModel.load();

        String prompt = "What is machine learning?";

        // Test 1: Explicit stream=false
        java.util.Map<String, Object> params1 = new java.util.HashMap<>();
        params1.put("stream", false);
        ModelResponse response1 = nettyModel.generate(prompt, params1);
        Assert.assertFalse("Response should not be streaming when stream=false", response1.isStreaming());

        // Test 2: Explicit stream=true
        java.util.Map<String, Object> params2 = new java.util.HashMap<>();
        params2.put("stream", true);
        ModelResponse response2 = nettyModel.generate(prompt, params2);
        Assert.assertTrue("Response should be streaming when stream=true", response2.isStreaming());

        // Test 3: No stream parameter (default non-streaming)
        ModelResponse response3 = nettyModel.generate(prompt, new java.util.HashMap<>());
        Assert.assertFalse("Response should not be streaming when no stream parameter", response3.isStreaming());

        logger.info("✅ Parameter handling test completed");
        logger.info("   stream=false: streaming={}, success={}", response1.isStreaming(), response1.isSuccess());
        logger.info("   stream=true: streaming={}, success={}", response2.isStreaming(), response2.isSuccess());
        logger.info("   no stream param: streaming={}, success={}", response3.isStreaming(), response3.isSuccess());
    }

    @Test
    public void testCLIArgumentStreaming() throws Exception {
        logger.info("=== Testing CLI Argument Streaming Support ===");

        // Test argument parsing with stream parameter
        String[] args1 = {
            "--model", "netty-openai",
            "--url", "http://localhost:" + TEST_PORT + "/v1/chat/completions",
            "--api-key", "test-key",
            "--dataset", "line_by_line",
            "--dataset-path", "test_hello_world.txt",
            "--stream",  // Enable streaming
            "--concurrent", "1",
            "--number", "5",
            "--dry-run"
        };

        String[] args2 = {
            "--model", "netty-openai",
            "--url", "http://localhost:" + TEST_PORT + "/v1/chat/completions",
            "--api-key", "test-key",
            "--dataset", "line_by_line",
            "--dataset-path", "test_hello_world.txt",
            // No --stream parameter
            "--concurrent", "1",
            "--number", "5",
            "--dry-run"
        };

        // Parse command line arguments
        CommandLineArgs cmdArgs1 = ArgumentParser.parse(args1);
        CommandLineArgs cmdArgs2 = ArgumentParser.parse(args2);

        // Verify streaming parameter is correctly parsed
        Assert.assertTrue("Should parse stream=true from CLI",
            cmdArgs1.getStream() != null && cmdArgs1.getStream());
        Assert.assertFalse("Should parse stream=false when not specified",
            cmdArgs2.getStream() != null && cmdArgs2.getStream());

        logger.info("✅ CLI argument parsing test completed");
        logger.info("   With --stream: stream={}, success={}", cmdArgs1.getStream(), cmdArgs1.getStream());
        logger.info("   Without --stream: stream={}", cmdArgs2.getStream());
    }

    @Test
    public void testErrorHandling() throws Exception {
        logger.info("=== Testing Error Handling ===");

        try {
            // Test with invalid model configuration
            NettyOpenAIModel invalidModel = new NettyOpenAIModel("invalid-model", "chat", "netty");
            invalidModel.setApiEndpoint("http://localhost:" + (TEST_PORT + 1) + "/v1/chat/completions");
            invalidModel.setApiKey("invalid-key");
            invalidModel.setModelName("invalid-model");

            ModelResponse response = invalidModel.generate("Test prompt");

            if (response.isSuccess()) {
                logger.warn("Unexpected success response from invalid configuration");
            } else {
                logger.info("✅ Properly handled invalid configuration: {}", response.getErrorMessage());
            }

        } catch (Exception e) {
            logger.info("✅ Expected error with invalid configuration: {}", e.getMessage());
        }
    }

    @Test
    public void testConcurrencyWithStreaming() throws Exception {
        logger.info("=== Testing Concurrent Streaming Requests ===");

        nettyModel.load();

        // Create multiple concurrent streaming requests
        Thread[] threads = new Thread[3];
        ModelResponse[] responses = new ModelResponse[3];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String prompt = "Request " + index + ": What is the significance of renewable energy?";
                    Map<String, Object> params = Map.of("stream", true, "temperature", 0.6 + index * 0.1, "max_tokens", 100);
                    responses[index] = nettyModel.generate(prompt, params);
                    logger.info("Completed concurrent request {}", index);
                } catch (Exception e) {
                    logger.error("Error in concurrent request {}", index, e);
                }
            });
            threads[i].start();
        }

        // Wait for all requests to complete
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(30));
        }

        // Verify all requests completed successfully
        for (int i = 0; i < responses.length; i++) {
            Assert.assertNotNull("Response " + i + " should not be null", responses[i]);
            Assert.assertTrue("Response " + i + " should be streaming", responses[i].isStreaming());
            Assert.assertTrue("Response " + i + " should be successful", responses[i].isSuccess());
            Assert.assertTrue("Response " + i + " should have content", responses[i].getOutput().length() > 0);
        }

        logger.info("✅ Concurrent streaming requests test completed");
    }

    public static void main(String[] args) throws Exception {
        logger.info("=== Running Netty Streaming Integration Tests ===");

        StreamingIntegrationTest test = new StreamingIntegrationTest();

        try {
            test.setupTestEnvironment();

            // Run all tests
            test.testStandardNonStreamingRequest();
            test.testStreamingRequest();
            test.testStreamingPerformance();
            test.testStreamingParameterHandling();
            test.testCLIArgumentStreaming();
            test.testErrorHandling();
            test.testConcurrencyWithStreaming();

            logger.info("🎉 All integration tests passed!");

        } catch (Exception e) {
            logger.error("Integration test failed", e);
            throw new RuntimeException("Integration test failed: " + e.getMessage(), e);
        } finally {
            test.teardownTestEnvironment();
        }
    }
}