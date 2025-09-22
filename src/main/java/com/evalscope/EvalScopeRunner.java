package com.evalscope;

import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;
import com.evalscope.config.ConfigManager;
import com.evalscope.config.YamlConfigManager;
import com.evalscope.config.IConfigManager;
import com.evalscope.runner.EvaluationRunner;
import com.evalscope.runner.EvaluationReport;

import java.util.Arrays;
import java.util.Map;

public class EvalScopeRunner {
    private final IConfigManager configManager;
    private final EvaluationRunner evaluationRunner;

    public EvalScopeRunner() {
        // Use YamlConfigManager to try loading YAML config first
        this.configManager = new YamlConfigManager();
        this.evaluationRunner = new EvaluationRunner(configManager);
    }

    public EvalScopeRunner(IConfigManager configManager) {
        this.configManager = configManager;
        this.evaluationRunner = new EvaluationRunner(configManager);
    }

    public static void main(String[] args) {
        System.out.println("=== EvalScope Java ===");
        System.out.println("AI Model Evaluation Framework");
        System.out.println();

        // Parse command line arguments
        CommandLineArgs cmdArgs = ArgumentParser.parse(args);

        // Show help if requested
        if (cmdArgs.getHelp() != null && cmdArgs.getHelp()) {
            ArgumentParser.printHelp();
            System.exit(0);
        }

        // Show version if requested
        if (cmdArgs.getVersion() != null) {
            System.out.println("EvalScope Java Version: " + cmdArgs.getVersion());
            System.exit(0);
        }

        EvalScopeRunner runner = new EvalScopeRunner();

        try {
            // Configure based on command line arguments
            runner.configureFromCommandLine(cmdArgs);

            if (args.length == 0 || (cmdArgs.getConfigFile() == null && cmdArgs.getUrl() == null && cmdArgs.getApiBaseUrl() == null)) {
                // Run with default configuration when no specific args provided
                System.out.println("Running with default configuration...");
                runner.runDefaultEvaluation();
            } else {
                // Run with command line configuration
                String evaluationType = cmdArgs.getEvaluationType() != null ? cmdArgs.getEvaluationType() : "cli_configured";
                System.out.println("Running evaluation with command line configuration: " + evaluationType);
                runner.runEvaluationWithArgs(cmdArgs);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            runner.shutdown();
        }

        System.out.println("Evaluation completed successfully!");
    }

    public EvaluationReport runEvaluation(String evaluationName) {
        return evaluationRunner.runEvaluation(evaluationName);
    }

    public void runDefaultEvaluation() {
        // Create a default evaluation configuration
        createDefaultConfigs();
        EvaluationReport report = evaluationRunner.runEvaluation("default_evaluation");

        if (!report.hasResults()) {
            System.out.println("Warning: No evaluation results generated. Check configuration.");
        }
    }

    private void createDefaultConfigs() {
        System.out.println("Setting up default configuration...");

        // Create default model configuration
        com.evalscope.config.ModelConfig defaultModel = new com.evalscope.config.ModelConfig(
            "mock-chat-model", "chat", "mock"
        );
        defaultModel.addParameter("endpoint", "mock://localhost:8080");
        defaultModel.setEnabled(true);
        configManager.addModelConfig(defaultModel);

        // Create default evaluation configuration
        com.evalscope.config.EvaluationConfig defaultEval = new com.evalscope.config.EvaluationConfig("default_evaluation");
        defaultEval.setModelIds(Arrays.asList("mock-chat-model"));
        defaultEval.setEvaluatorTypes(Arrays.asList("chat"));
        defaultEval.addParameter("max_examples", 10);
        defaultEval.setMaxConcurrency(1);
        defaultEval.setSaveResults(true);
        configManager.addEvaluationConfig(defaultEval);

        System.out.println("Default configuration created successfully.");
    }

    public void shutdown() {
        if (evaluationRunner != null) {
            evaluationRunner.shutdown();
        }
    }

    public IConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Configure EvalScope from command line arguments
     */
    public void configureFromCommandLine(CommandLineArgs cmdArgs) {
        System.out.println("Configuring from command line arguments...");

        // Set log level
        if (cmdArgs.getLogLevel() != null) {
            // This would need to be implemented in the logging configuration
            System.out.println("Setting log level to: " + cmdArgs.getLogLevel());
        }

        // Handle debug and verbose modes
        if (cmdArgs.getDebug() != null && cmdArgs.getDebug()) {
            System.out.println("Debug mode enabled");
        }
        if (cmdArgs.getVerbose() != null && cmdArgs.getVerbose()) {
            System.out.println("Verbose mode enabled");
        }

        // Handle dry-run mode
        if (cmdArgs.getDryRun() != null && cmdArgs.getDryRun()) {
            System.out.println("Dry run mode - no actual requests will be made");
        }

        // Load config file if specified
        if (cmdArgs.getConfigFile() != null) {
            System.out.println("Loading configuration from: " + cmdArgs.getConfigFile());
            // This would need to be implemented to load from specified config file
        }

        System.out.println("Command line configuration applied.");
    }

    /**
     * Run evaluation with command line arguments
     */
    public EvaluationReport runEvaluationWithArgs(CommandLineArgs cmdArgs) {
        String evaluationName = "cli_configured_evaluation";

        // Create evaluation configuration from command line arguments
        com.evalscope.config.EvaluationConfig evalConfig = new com.evalscope.config.EvaluationConfig(evaluationName);

        // Apply basic parameters
        if (cmdArgs.getModel() != null) {
            evalConfig.addParameter("model", cmdArgs.getModel());
        }
        // IMPORTANT: Fix dataset parameter mapping for line_by_line support
        if (cmdArgs.getDataset() != null) {
            evalConfig.addParameter("dataset", cmdArgs.getDataset());  // Pass dataset type as parameter
        }
        if (cmdArgs.getDatasetPath() != null) {
            evalConfig.setDatasetPath(cmdArgs.getDatasetPath());  // Set actual file path
        }
        if (cmdArgs.getOutputPath() != null) {
            evalConfig.setOutputPath(cmdArgs.getOutputPath());
        } else {
            // Set default output path based on evaluation type
            evalConfig.setOutputPath("results/cli_results/");
        }

        // Apply performance parameters
        if (cmdArgs.getConcurrent() != null) {
            evalConfig.setMaxConcurrency(cmdArgs.getConcurrent());
            evalConfig.addParameter("concurrent", cmdArgs.getConcurrent());
        }
        if (cmdArgs.getNumber() != null) {
            evalConfig.addParameter("number", cmdArgs.getNumber());
        }
        if (cmdArgs.getRounds() != null) {
            evalConfig.addParameter("rounds", cmdArgs.getRounds());
        }

        // Apply request parameters
        if (cmdArgs.getMaxTokens() != null) {
            evalConfig.addParameter("max_tokens", cmdArgs.getMaxTokens());
        }
        if (cmdArgs.getTemperature() != null) {
            evalConfig.addParameter("temperature", cmdArgs.getTemperature());
        }
        if (cmdArgs.getTopP() != null) {
            evalConfig.addParameter("top_p", cmdArgs.getTopP());
        }
        if (cmdArgs.getFrequencyPenalty() != null) {
            evalConfig.addParameter("frequency_penalty", cmdArgs.getFrequencyPenalty());
        }
        if (cmdArgs.getPresencePenalty() != null) {
            evalConfig.addParameter("presence_penalty", cmdArgs.getPresencePenalty());
        }
        if (cmdArgs.getStop() != null) {
            evalConfig.addParameter("stop", cmdArgs.getStop());
        }
        if (cmdArgs.getStream() != null && cmdArgs.getStream()) {
            evalConfig.addParameter("stream", true);
        }
        if (cmdArgs.getSystemPrompt() != null) {
            evalConfig.addParameter("system_prompt", cmdArgs.getSystemPrompt());
        }

        // Apply connection parameters
        if (cmdArgs.getMaxWorkers() != null) {
            evalConfig.addParameter("max_workers", cmdArgs.getMaxWorkers());
        }
        if (cmdArgs.getConnectTimeout() != null) {
            evalConfig.addParameter("connect_timeout", cmdArgs.getConnectTimeout());
        }
        if (cmdArgs.getReadTimeout() != null) {
            evalConfig.addParameter("read_timeout", cmdArgs.getReadTimeout());
        }
        if (cmdArgs.getMaxRetries() != null) {
            evalConfig.addParameter("max_retries", cmdArgs.getMaxRetries());
        }
        if (cmdArgs.getRetryDelay() != null) {
            evalConfig.addParameter("retry_delay", cmdArgs.getRetryDelay());
        }

        // Apply dataset parameters
        if (cmdArgs.getDatasetLimit() != null) {
            evalConfig.addParameter("dataset_limit", cmdArgs.getDatasetLimit());
        }
        if (cmdArgs.getDatasetShuffle() != null && cmdArgs.getDatasetShuffle()) {
            evalConfig.addParameter("dataset_shuffle", true);
        }
        // Add line-by-line dataset specific parameters
        if (cmdArgs.getMaxExamples() != null) {
            evalConfig.addParameter("max_examples", cmdArgs.getMaxExamples());
        }
        if (cmdArgs.getSkipLines() != null) {
            evalConfig.addParameter("skip_lines", cmdArgs.getSkipLines());
        }
        if (cmdArgs.getLinePrefix() != null) {
            evalConfig.addParameter("line_prefix", cmdArgs.getLinePrefix());
        }

        // Apply evaluation parameters
        if (cmdArgs.getMetrics() != null) {
            evalConfig.addParameter("metrics", cmdArgs.getMetrics());
        }
        if (cmdArgs.getIncludeLatency() != null) {
            evalConfig.addParameter("include_latency", cmdArgs.getIncludeLatency());
        }
        if (cmdArgs.getIncludeAccuracy() != null) {
            evalConfig.addParameter("include_accuracy", cmdArgs.getIncludeAccuracy());
        }

        // Apply mode parameters
        if (cmdArgs.getDryRun() != null && cmdArgs.getDryRun()) {
            evalConfig.addParameter("dry_run", true);
        }
        if (cmdArgs.getDebug() != null && cmdArgs.getDebug()) {
            evalConfig.addParameter("debug", true);
        }
        if (cmdArgs.getVerbose() != null && cmdArgs.getVerbose()) {
            evalConfig.addParameter("verbose", true);
        }

        // Set basic evaluation properties
        evalConfig.setEvaluatorTypes(Arrays.asList("chat"));
        evalConfig.setSaveResults(cmdArgs.getSaveResults() != null ? cmdArgs.getSaveResults() : true);
        evalConfig.setResultFormat(cmdArgs.getOutputFormat());

        // Add the evaluation configuration
        configManager.addEvaluationConfig(evalConfig);

        // Create model configuration if URL and API key are provided
        String baseUrl = cmdArgs.getUrl() != null ? cmdArgs.getUrl() : cmdArgs.getApiBaseUrl();
        if (baseUrl != null) {
            String modelId = cmdArgs.getModel() != null ? cmdArgs.getModel() : "default-model";
            com.evalscope.config.ModelConfig modelConfig = new com.evalscope.config.ModelConfig(modelId, "chat", "openai");
            modelConfig.addParameter("endpoint", baseUrl);

            // Add API key to credentials if provided
            if (cmdArgs.getApiKey() != null) {
                modelConfig.addCredential("api_key", cmdArgs.getApiKey());
            }

            // Add request parameters
            if (cmdArgs.getMaxTokens() != null) {
                modelConfig.addParameter("max_tokens", cmdArgs.getMaxTokens());
            }
            if (cmdArgs.getTemperature() != null) {
                modelConfig.addParameter("temperature", cmdArgs.getTemperature());
            }
            if (cmdArgs.getTopP() != null) {
                modelConfig.addParameter("top_p", cmdArgs.getTopP());
            }
            if (cmdArgs.getFrequencyPenalty() != null) {
                modelConfig.addParameter("frequency_penalty", cmdArgs.getFrequencyPenalty());
            }
            if (cmdArgs.getPresencePenalty() != null) {
                modelConfig.addParameter("presence_penalty", cmdArgs.getPresencePenalty());
            }
            if (cmdArgs.getStop() != null) {
                modelConfig.addParameter("stop", cmdArgs.getStop());
            }
            if (cmdArgs.getStream() != null) {
                modelConfig.addParameter("stream", cmdArgs.getStream());
            }

            modelConfig.setEnabled(true);
            configManager.addModelConfig(modelConfig);

            // Set the model ID for the evaluation
            evalConfig.setModelIds(Arrays.asList(modelId));
        }

        // Handle rate limiting
        if (cmdArgs.getRequestsPerSecond() != null) {
            evalConfig.addParameter("requests_per_second", cmdArgs.getRequestsPerSecond());
        }
        if (cmdArgs.getRequestsPerMinute() != null) {
            evalConfig.addParameter("requests_per_minute", cmdArgs.getRequestsPerMinute());
        }

        System.out.println("Created evaluation configuration from command line arguments: " + cmdArgs);

        return evaluationRunner.runEvaluation(evaluationName);
    }

    public EvaluationRunner getEvaluationRunner() {
        return evaluationRunner;
    }
}