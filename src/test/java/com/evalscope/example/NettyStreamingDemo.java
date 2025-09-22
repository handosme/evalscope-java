package com.evalscope.example;

import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;
import com.evalscope.model.ModelResponse;
import com.evalscope.model.NettyOpenAIModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Complete demonstration of Netty-based streaming functionality
 * Shows how to use the --stream parameter with the CLI and API
 */
public class NettyStreamingDemo {
    private static final Logger logger = LoggerFactory.getLogger(NettyStreamingDemo.class);

    public static void main(String[] args) {
        logger.info("=== Netty OpenAI Streaming Demo ===");

        if (args.length == 0) {
            runInteractiveDemo();
        } else {
            runCLIWithStreaming(args);
        }
    }

    /**
     * Interactive demonstration of streaming functionality
     */
    private static void runInteractiveDemo() {
        logger.info("\n🚀 Starting interactive streaming demo...");

        // Example 1: Using CLI arguments with streaming
        logger.info("\n📋 Example 1: CLI Arguments with Streaming");
        String[] streamingArgs = {
            "--url", "http://localhost:8000/v1/chat/completions",
            "--api-key", "test-key",
            "--model", "gpt-3.5-turbo",
            "--dataset", "line_by_line",
            "--dataset-path", "demo_prompts.txt",
            "--stream",          // Enable streaming
            "--concurrent", "1",
            "--number", "3",
            "--dry-run"
        };

        logger.info("Command with --stream: java -jar evalscope.jar {} --stream", String.join(" ", streamingArgs));

        CommandLineArgs streamingArgsParsed = ArgumentParser.parse(streamingArgs);
        logger.info("✅ Streaming enabled: {}", streamingArgsParsed.getStream());

        // Example 2: Using CLI arguments without streaming
        logger.info("\n📋 Example 2: CLI Arguments without Streaming");
        String[] nonStreamingArgs = {
            "--url", "http://localhost:8000/v1/chat/completions",
            "--api-key", "test-key",
            "--model", "gpt-3.5-turbo",
            "--dataset", "line_by_line",
            "--dataset-path", "demo_prompts.txt",
            "--concurrent", "1",
            "--number", "3",
            "--dry-run"
        };

        logger.info("Command without --stream: java -jar evalscope.jar {}", String.join(" ", nonStreamingArgs));

        CommandLineArgs nonStreamingArgsParsed = ArgumentParser.parse(nonStreamingArgs);
        logger.info("✅ Streaming disabled: {}", nonStreamingArgsParsed.getStream());

        // Example 3: Programmatic usage with streaming
        logger.info("\n🎯 Example 3: Programmatic Usage with Streaming");
        testProgrammaticStreaming();
    }

    /**
     * Programmatic demonstration of streaming vs non-streaming
     */
    private static void testProgrammaticStreaming() {
        logger.info("Initializing Netty OpenAI model...");

        // Create model instance
        NettyOpenAIModel model = new NettyOpenAIModel("demo-netty-openai", "chat", "netty");
        model.setApiEndpoint("http://localhost:8000/v1/chat/completions");
        model.setApiKey("demo-key");
        model.setModelName("gpt-3.5-turbo");

        try {
            model.load();
            logger.info("✅ Model loaded successfully");

            String prompt = "Explain the benefits of renewable energy in simple terms";

            // Test non-streaming
            logger.info("\n🔧 Testing Non-Streaming Request...");
            Map<String, Object> standardParams = new java.util.HashMap<String, Object>();
            standardParams.put("temperature", 0.7);
            standardParams.put("max_tokens", 150);

            ModelResponse standardResponse = model.generate(prompt, standardParams);
            logResponse("Standard Response", standardResponse);

            // Test streaming
            logger.info("\n🌊 Testing Streaming Request...");
            Map<String, Object> streamingParams = new java.util.HashMap<String, Object>();
            streamingParams.put("temperature", 0.7);
            streamingParams.put("max_tokens", 150);
            streamingParams.put("stream", true);  // Enable streaming

            ModelResponse streamingResponse = model.generate(prompt, streamingParams);
            logResponse("Streaming Response", streamingResponse);

            // Compare results
            logger.info("\n📊 Comparison:");
            logger.info("   Standard time: {}ms", standardResponse.getProcessingTimeMs());
            logger.info("   Streaming time: {}ms", streamingResponse.getProcessingTimeMs());
            logger.info("   Standard output length: {}", standardResponse.getOutput().length());
            logger.info("   Streaming output length: {}", streamingResponse.getOutput().length());

            model.unload();
            logger.info("✅ Model unloaded successfully");

        } catch (Exception e) {
            logger.error("Model operation failed", e);
        }
    }

    /**
     * Run CLI with streaming arguments
     */
    private static void runCLIWithStreaming(String[] args) {
        logger.info("\n🏃 Running CLI with streaming arguments...");
        logger.info("Input args: {}", String.join(" ", args));

        try {
            CommandLineArgs cmdArgs = ArgumentParser.parse(args);

            logger.info("Parsed arguments:");
            logger.info("   URL: {}", cmdArgs.getUrl());
            logger.info("   Model: {}", cmdArgs.getModel());
            logger.info("   API Key: {}", cmdArgs.getApiKey() != null ? "***configured***" : "not set");
            logger.info("   Dataset: {}", cmdArgs.getDataset());
            logger.info("   Dataset Path: {}", cmdArgs.getDatasetPath());
            logger.info("   Stream enabled: {}", cmdArgs.getStream());
            logger.info("   Concurrent: {}", cmdArgs.getConcurrent());
            logger.info("   Number: {}", cmdArgs.getNumber());
            logger.info("   Temperature: {}", cmdArgs.getTemperature());
            logger.info("   Max Tokens: {}", cmdArgs.getMaxTokens());
            logger.info("   Dry Run: {}", cmdArgs.getDryRun());

            // Demonstrate that streaming parameter is working
            if (cmdArgs.getStream() != null && cmdArgs.getStream()) {
                logger.info("🎉 Streaming mode is ENABLED!");
                logger.info("   The Netty HTTP client will be used for SSE streaming");
                logger.info("   Responses will be processed in real-time as chunks arrive");
                logger.info("   This enables better performance and user experience");
            } else {
                logger.info("📄 Standard mode is active (no streaming)");
                logger.info("   Complete responses will be received before processing");
            }

        } catch (Exception e) {
            logger.error("Error parsing CLI arguments", e);
        }
    }

    private static void logResponse(String type, ModelResponse response) {
        logger.info("★ {} Results:", type);
        logger.info("   Success: {}", response.isSuccess());
        logger.info("   Output: {}", response.getOutput());
        logger.info("   Processing Time: {}ms", response.getProcessingTimeMs());
        logger.info("   Streaming: {}", response.isStreaming());
        logger.info("   Metadata: {}", response.getMetadata());
    }

    public static class StreamingConfig {
        /**
         * Configuration for different streaming scenarios
         */
        public static String[] createBasicStreamingArgs() {
            return new String[]{
                "--model", "netty-openai",
                "--url", "http://localhost:8000/v1/chat/completions",
                "--api-key", "your-api-key-here",
                "--stream",                  // Enable streaming
                "--dataset", "line_by_line",
                "--dataset-path", "prompts.txt"
            };
        }

        public static String[] createAdvancedStreamingArgs() {
            return new String[]{
                "--model", "netty-openai",
                "--url", "http://localhost:8000/v1/chat/completions",
                "--api-key", "your-api-key-here",
                "--stream",                  // Enable streaming
                "--temperature", "0.8",
                "--max-tokens", "500",
                "--dataset", "line_by_line",
                "--dataset-path", "prompts.txt",
                "--concurrent", "2",
                "--number", "10"
            };
        }

        public static Map<String, Object> createBasicStreamingParams() {
            Map<String, Object> params = new java.util.HashMap<String, Object>();
            params.put("temperature", 0.7);
            params.put("max_tokens", 200);
            params.put("stream", true);
            return params;
        }

        public static Map<String, Object> createAdvancedStreamingParams() {
            Map<String, Object> params = new java.util.HashMap<String, Object>();
            params.put("temperature", 0.8);
            params.put("top_p", 0.9);
            params.put("presence_penalty", 0.1);
            params.put("frequency_penalty", 0.1);
            params.put("max_tokens", 300);
            params.put("stream", true);
            return params;
        }
    }

    /**
     * Run the demo with provided arguments
     */
    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  java -jar evalscope.jar --stream [...other options]      # Enable streaming");
        System.out.println("  java -cp \".:target/classes\" com.evalscope.example.NettyStreamingDemo [CLI_ARGS]");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  # Enable streaming in CLI");
        System.out.println("  --model netty-openai --url http://localhost:8000/v1/chat/completions --stream");
        System.out.println("");
        System.out.println("  # Programmatic usage");
        System.out.println("  NettyStreamingDemo.runInteractiveDemo();");
        System.out.println("");
        System.out.println("Supported streaming features:");
        System.out.println("  ✓ Real-time response processing");
        System.out.println("  ✓ Netty-based high-performance HTTP client");
        System.out.println("  ✓ SSE (Server-Sent Events) support");
        System.out.println("  ✓ Resilient connection handling");
        System.out.println("  ✓ Automatic retry mechanism");
    }

    public static void printStreamingFeatures() {
        System.out.println("\\n🌊 Netty OpenAI Streaming Features");
        System.out.println("=====================================");
        System.out.println("📡 Netty HTTP Client");
        System.out.println("   • High-performance asynchronous networking");
        System.out.println("   • Event-driven architecture");
        System.out.println("   • Connection pooling and keep-alive");
        System.out.println("");
        System.out.println("🔄 SSE (Server-Sent Events)");
        System.out.println("   • Real-time response processing");
        System.out.println("   • Chunked data streaming");
        System.out.println("   • Automatic reconnection handling");
        System.out.println("");
        System.out.println("⚡ Performance Benefits");
        System.out.println("   • Lower perceived latency");
        System.out.println("   • Better user experience");
        System.out.println("   • Memory efficient processing");
        System.out.println("   • Concurrent request handling");
        System.out.println("");
        System.out.println("🔧 Usage:");
        System.out.println("   CLI: --stream");
        System.out.println("   Programmatic: set stream=true in parameters");
        System.out.println("");
    }
}