package com.evalscope.cli;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Command line argument parser for EvalScope
 * Supports all parameters from the official documentation
 */
public class ArgumentParser {

    /**
     * Parse command line arguments into CommandLineArgs object
     */
    public static CommandLineArgs parse(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();
        Map<String, String> argMap = new HashMap<>();

        // Convert args array to map and handle official parameter names exactly as documented
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                // Keep exact parameter names as documented (with hyphens)
                String key = arg.substring(2);
                String value = null;

                // Check if next argument is the value (not another flag)
                if (i + 1 <= args.length - 1 && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                } else {
                    // Boolean flag (no value needed)
                    value = "true";
                }

                argMap.put(key, value);
            }
        }

        // Parse all arguments
        parseBasicParameters(argMap, cmdArgs);
        parsePerformanceParameters(argMap, cmdArgs);
        parseRequestParameters(argMap, cmdArgs);
        parseConnectionParameters(argMap, cmdArgs);
        parseModeParameters(argMap, cmdArgs);
        parseOutputParameters(argMap, cmdArgs);
        parseDatasetParameters(argMap, cmdArgs);
        parseEvaluationParameters(argMap, cmdArgs);
        parseAuthParameters(argMap, cmdArgs);
        parseRateLimitParameters(argMap, cmdArgs);
        parseSystemParameters(argMap, cmdArgs);

        return cmdArgs;
    }

    private static void parseBasicParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        // Use exact parameter names from official documentation
        if (argMap.containsKey("url")) {
            cmdArgs.setUrl(argMap.get("url"));
        }
        if (argMap.containsKey("api-base-url")) {
            cmdArgs.setApiBaseUrl(argMap.get("api-base-url"));
        }
        if (argMap.containsKey("model")) {
            cmdArgs.setModel(argMap.get("model"));
        }
        if (argMap.containsKey("api-key")) {
            cmdArgs.setApiKey(argMap.get("api-key"));
        }
        if (argMap.containsKey("dataset")) {
            cmdArgs.setDataset(argMap.get("dataset"));
        }
    }

    private static void parsePerformanceParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        // Use exact parameter names from official documentation
        if (argMap.containsKey("concurrent")) {
            cmdArgs.setConcurrent(parseInt(argMap.get("concurrent"), 1));
        }
        if (argMap.containsKey("number")) {
            cmdArgs.setNumber(parseInt(argMap.get("number"), 1));
        }
        if (argMap.containsKey("rounds")) {
            cmdArgs.setRounds(parseInt(argMap.get("rounds"), 1));
        }
    }

    private static void parseRequestParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        // Use exact parameter names from official documentation
        if (argMap.containsKey("max-tokens")) {
            cmdArgs.setMaxTokens(parseInt(argMap.get("max-tokens"), 2048));
        }
        if (argMap.containsKey("temperature")) {
            cmdArgs.setTemperature(parseDouble(argMap.get("temperature"), 0.7));
        }
        if (argMap.containsKey("top-p")) {
            cmdArgs.setTopP(parseDouble(argMap.get("top-p"), 0.9));
        }
        if (argMap.containsKey("frequency-penalty")) {
            cmdArgs.setFrequencyPenalty(parseDouble(argMap.get("frequency-penalty"), 0.0));
        }
        if (argMap.containsKey("presence-penalty")) {
            cmdArgs.setPresencePenalty(parseDouble(argMap.get("presence-penalty"), 0.0));
        }
        if (argMap.containsKey("stop")) {
            cmdArgs.setStop(argMap.get("stop"));
        }
        if (argMap.containsKey("stream")) {
            cmdArgs.setStream(argMap.containsKey("stream"));
        }
        // For system-prompt parameter, using "system" as the command line parameter for simplicity
        if (argMap.containsKey("system")) {
            cmdArgs.setSystemPrompt(argMap.get("system"));
        }
    }

    private static void parseConnectionParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        // Rate limiting and worker pool parameters (if supported by official EvalScope)
        if (argMap.containsKey("max-workers")) {
            cmdArgs.setMaxWorkers(parseInt(argMap.get("max-workers"), 10));
        }
        if (argMap.containsKey("connect-timeout")) {
            cmdArgs.setConnectTimeout(parseInt(argMap.get("connect-timeout"), 30));
        }
        if (argMap.containsKey("read-timeout")) {
            cmdArgs.setReadTimeout(parseInt(argMap.get("read-timeout"), 60));
        }
        if (argMap.containsKey("max-retries")) {
            cmdArgs.setMaxRetries(parseInt(argMap.get("max-retries"), 3));
        }
        if (argMap.containsKey("retry-delay")) {
            cmdArgs.setRetryDelay(parseInt(argMap.get("retry-delay"), 1000));
        }
    }

    private static void parseModeParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("debug") || argMap.containsKey("d")) {
            cmdArgs.setDebug(parseBoolean(argMap.get("debug") != null ? argMap.get("debug") : "true", false));
        }
        if (argMap.containsKey("dry-run")) {
            cmdArgs.setDryRun(parseBoolean(argMap.get("dry-run"), false));
        }
        if (argMap.containsKey("verbose") || argMap.containsKey("v")) {
            cmdArgs.setVerbose(parseBoolean(argMap.get("verbose") != null ? argMap.get("verbose") : "true", false));
        }
    }

    private static void parseOutputParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("output")) {
            cmdArgs.setOutputPath(argMap.get("output"));
        }
        if (argMap.containsKey("output-format")) {
            cmdArgs.setOutputFormat(argMap.get("output-format"));
        }
        if (argMap.containsKey("save-results")) {
            cmdArgs.setSaveResults(parseBoolean(argMap.get("save-results"), true));
        }
    }

    private static void parseDatasetParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("dataset-path")) {
            cmdArgs.setDatasetPath(argMap.get("dataset-path"));
        }
        if (argMap.containsKey("dataset-limit")) {
            cmdArgs.setDatasetLimit(parseInt(argMap.get("dataset-limit"), 0));
        }
        if (argMap.containsKey("dataset-shuffle")) {
            cmdArgs.setDatasetShuffle(parseBoolean(argMap.get("dataset-shuffle"), false));
        }
        // Line-by-line dataset specific parameters
        if (argMap.containsKey("max-examples")) {
            cmdArgs.setMaxExamples(parseInt(argMap.get("max-examples"), 100));
        }
        if (argMap.containsKey("skip-lines")) {
            cmdArgs.setSkipLines(parseInt(argMap.get("skip-lines"), 0));
        }
        if (argMap.containsKey("line-prefix")) {
            cmdArgs.setLinePrefix(argMap.get("line-prefix"));
        }
    }

    private static void parseEvaluationParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("evaluation-type")) {
            cmdArgs.setEvaluationType(argMap.get("evaluation-type"));
        }
        if (argMap.containsKey("metrics")) {
            cmdArgs.setMetrics(argMap.get("metrics"));
        }
        if (argMap.containsKey("include-latency")) {
            cmdArgs.setIncludeLatency(parseBoolean(argMap.get("include-latency"), true));
        }
        if (argMap.containsKey("include-accuracy")) {
            cmdArgs.setIncludeAccuracy(parseBoolean(argMap.get("include-accuracy"), true));
        }
        if (argMap.containsKey("run-model")) {
            cmdArgs.setRunModel(argMap.get("run-model"));
        }
    }

    private static void parseAuthParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("auth_type")) {
            cmdArgs.setAuthType(argMap.get("auth_type"));
        }
        if (argMap.containsKey("auth_token")) {
            cmdArgs.setAuthToken(argMap.get("auth_token"));
        }
    }

    private static void parseRateLimitParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("requests_per_second")) {
            cmdArgs.setRequestsPerSecond(parseInt(argMap.get("requests_per_second"), 0));
        }
        if (argMap.containsKey("requests_per_minute")) {
            cmdArgs.setRequestsPerMinute(parseInt(argMap.get("requests_per_minute"), 0));
        }
    }

    private static void parseSystemParameters(Map<String, String> argMap, CommandLineArgs cmdArgs) {
        if (argMap.containsKey("config")) {
            cmdArgs.setConfigFile(argMap.get("config"));
        }
        if (argMap.containsKey("log-level")) {
            cmdArgs.setLogLevel(argMap.get("log-level"));
        }
        if (argMap.containsKey("help") || argMap.containsKey("h")) {
            cmdArgs.setHelp(parseBoolean(argMap.get("help") != null ? argMap.get("help") : argMap.get("h"), true));
        }
        if (argMap.containsKey("version")) {
            cmdArgs.setVersion(argMap.get("version"));
        }
        if (argMap.containsKey("run-mode")) {
            cmdArgs.setRunMode(argMap.get("run-mode"));
        }
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer value '" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid double value '" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        return !("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value));
    }

    /**
     * Display help information
     */
    public static void printHelp() {
        System.out.println("EvalScope - AI Model Evaluation Framework");
        System.out.println();
        System.out.println("Usage: java -jar evalscope.jar [options]");
        System.out.println();
        System.out.println("Basic Test Parameters:");
        System.out.println("  --url <url>                    Model service URL");
        System.out.println("  --model <model_name>           Model to evaluate");
        System.out.println("  --api-key <key>                API key for model access");
        System.out.println("  --dataset <dataset_name>       Dataset to use for evaluation");
        System.out.println();
        System.out.println("Performance Test Parameters:");
        System.out.println("  --concurrent <count>           Number of concurrent requests (default: 1)");
        System.out.println("  --number <count>               Number of requests per round (default: 1)");
        System.out.println("  --rounds <count>               Number of test rounds (default: 1)");
        System.out.println();
        System.out.println("Request Parameters:");
        System.out.println("  --max-tokens <count>           Maximum tokens in response (default: 2048)");
        System.out.println("  --temperature <value>          Temperature for sampling (default: 0.7)");
        System.out.println("  --top-p <value>                Top-p for sampling (default: 0.9)");
        System.out.println("  --frequency-penalty <value>    Frequency penalty (default: 0.0)");
        System.out.println("  --presence-penalty <value>     Presence penalty (default: 0.0)");
        System.out.println("  --stop <sequences>             Stop sequences (comma-separated)");
        System.out.println("  --stream                       Enable streaming");
        System.out.println("  --system <prompt>              System prompt");
        System.out.println();
        System.out.println("Connection Pool Parameters:");
        System.out.println("  --max-workers <count>          Maximum worker threads (default: 10)");
        System.out.println("  --connect-timeout <seconds>    Connection timeout (default: 30)");
        System.out.println("  --read-timeout <seconds>       Read timeout (default: 60)");
        System.out.println("  --max-retries <count>          Maximum retries (default: 3)");
        System.out.println("  --retry-delay <ms>             Retry delay in milliseconds (default: 1000)");
        System.out.println();
        System.out.println("Test Mode Parameters:");
        System.out.println("  --debug, -d                    Enable debug mode");
        System.out.println("  --dry-run                      Dry run mode");
        System.out.println("  --verbose, -v                  Verbose output");
        System.out.println();
        System.out.println("Output Parameters:");
        System.out.println("  --output <path>                Output file path");
        System.out.println("  --output-format <format>       Output format: json|csv|xml (default: json)");
        System.out.println("  --save-results <bool>          Save results to file (default: true)");
        System.out.println();
        System.out.println("Dataset Parameters:");
        System.out.println("  --dataset-path <path>          Dataset file path");
        System.out.println("  --dataset-limit <count>        Dataset limit");
        System.out.println("  --dataset-shuffle              Shuffle dataset");
        System.out.println();
        System.out.println("Evaluation Parameters:");
        System.out.println("  --evaluation-type <type>       Evaluation type: standard|stress|concurrent|longevity (default: standard)");
        System.out.println("  --metrics <metrics>            Comma-separated metrics");
        System.out.println("  --include-latency <bool>       Include latency metrics (default: true)");
        System.out.println("  --include-accuracy <bool>      Include accuracy metrics (default: true)");
        System.out.println("  --run-model <mode>             Run mode for models: all|evaluation|benchmark (default: all)");
        System.out.println();
        System.out.println("Authentication Parameters:");
        System.out.println("  --auth-type <type>             Authentication type");
        System.out.println("  --auth-token <token>           Authentication token");
        System.out.println();
        System.out.println("Rate Limiting Parameters:");
        System.out.println("  --requests-per-second <rate>   Maximum requests per second");
        System.out.println("  --requests-per-minute <rate>   Maximum requests per minute");
        System.out.println();
        System.out.println("System Parameters:");
        System.out.println("  --config <file>                Configuration file path");
        System.out.println("  --log-level <level>            Log level: DEBUG|INFO|WARN|ERROR (default: INFO)");
        System.out.println("  --help, -h                     Show this help message");
        System.out.println("  --version                      Show version information");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar evalscope.jar --url http://localhost:8080 --model gpt-3.5-turbo --api-key your_key --dataset general_qa --concurrent 10 --number 100");
        System.out.println("  java -jar evalscope.jar --config config.yaml --evaluation-type stress --concurrent 50 --rounds 5");
        System.out.println("  java -jar evalscope.jar --dry-run --debug --verbose");
    }
}