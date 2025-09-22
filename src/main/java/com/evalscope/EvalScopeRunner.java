package com.evalscope;

import com.evalscope.config.ConfigManager;
import com.evalscope.config.YamlConfigManager;
import com.evalscope.runner.EvaluationRunner;
import com.evalscope.runner.EvaluationReport;

import java.util.Arrays;
import java.util.Map;

public class EvalScopeRunner {
    private final ConfigManager configManager;
    private final EvaluationRunner evaluationRunner;

    public EvalScopeRunner() {
        // Use YamlConfigManager to try loading YAML config first
        this.configManager = new YamlConfigManager();
        this.evaluationRunner = new EvaluationRunner(configManager);
    }

    public EvalScopeRunner(ConfigManager configManager) {
        this.configManager = configManager;
        this.evaluationRunner = new EvaluationRunner(configManager);
    }

    public static void main(String[] args) {
        System.out.println("=== EvalScope Java ===");
        System.out.println("AI Model Evaluation Framework");
        System.out.println();

        EvalScopeRunner runner = new EvalScopeRunner();

        try {
            if (args.length == 0) {
                // Run with default configuration
                System.out.println("Running with default configuration...");
                runner.runDefaultEvaluation();
            } else {
                // Run with specified evaluation name
                String evaluationName = args[0];
                System.out.println("Running evaluation: " + evaluationName);
                runner.runEvaluation(evaluationName);
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

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EvaluationRunner getEvaluationRunner() {
        return evaluationRunner;
    }
}