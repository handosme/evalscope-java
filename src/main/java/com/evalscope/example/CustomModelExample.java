package com.evalscope.example;

import com.evalscope.model.ChatModel;
import com.evalscope.model.ModelResponse;
import com.evalscope.evaluator.Evaluator;
import com.evalscope.evaluator.EvaluationResult;
import com.evalscope.evaluator.EvaluationData;
import com.evalscope.config.ConfigManager;
import com.evalscope.config.ModelConfig;
import com.evalscope.config.EvaluationConfig;
import com.evalscope.EvalScopeRunner;
import com.evalscope.runner.EvaluationReport;

import java.util.*;

/**
 * Example demonstrating how to create and evaluate a custom chat model.
 */
public class CustomModelExample {

    public static void main(String[] args) {
        System.out.println("=== Custom Model Example ===");

        // Set up configuration
        ConfigManager configManager = ConfigManager.createDefault();

        // Configure your custom model
        ModelConfig myModelConfig = new ModelConfig("my-smart-model", "chat", "custom");
        myModelConfig.addParameter("intelligence_level", "high");
        myModelConfig.addParameter("creativity", 0.8);
        myModelConfig.setEnabled(true);
        configManager.addModelConfig(myModelConfig);

        // Configure evaluation
        EvaluationConfig evalConfig = new EvaluationConfig("custom-model-evaluation");
        evalConfig.setModelIds(Arrays.asList("my-smart-model"));
        evalConfig.setEvaluatorTypes(Arrays.asList("chat"));
        evalConfig.addParameter("max_examples", 10);
        evalConfig.addParameter("similarity_threshold", 0.7);
        configManager.addEvaluationConfig(evalConfig);

        // Register custom model factory (in a real implementation)
        // RunnerFactory.registerModel("custom", MySmartModel::new);

        try {
            // Create runner and run evaluation
            EvalScopeRunner runner = new EvalScopeRunner(configManager);
            EvaluationReport report = runner.runEvaluation("custom-model-evaluation");

            // Display results
            System.out.println("\n=== Evaluation Results ===");
            System.out.println("Report ID: " + report.getReportId());
            System.out.println("Models evaluated: " + report.getTotalModels());
            System.out.println("Summary: " + report.getSummary());

            runner.shutdown();

        } catch (Exception e) {
            System.err.println("Evaluation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example of a custom chat model implementation.
     * In practice, this would connect to your actual AI model.
     */
    public static class MySmartModel extends ChatModel {
        private static final Map<String, String> KNOWLEDGE_BASE = new HashMap<>();

        static {
            KNOWLEDGE_BASE.put("2+2", "4");
            KNOWLEDGE_BASE.put("capital of france", "Paris");
            KNOWLEDGE_BASE.put("moon of earth", "The Moon");
            KNOWLEDGE_BASE.put("rgb primary colors", "Red, Green, Blue");
            KNOWLEDGE_BASE.put("pythagorean theorem", "a² + b² = c²");
        }

        public MySmartModel(String modelId, String modelType) {
            super(modelId, modelType);
        }

        @Override
        public void load() throws Exception {
            System.out.println("Loading MySmartModel...");
            // Simulate loading time
            Thread.sleep(100);
            setLoaded(true);
            System.out.println("MySmartModel loaded successfully!");
        }

        @Override
        public void unload() throws Exception {
            System.out.println("Unloading MySmartModel...");
            setLoaded(false);
            System.out.println("MySmartModel unloaded successfully!");
        }

        @Override
        public ModelResponse generate(String prompt, Map<String, Object> parameters) {
            ModelResponse response = new ModelResponse(getModelId(), "chat");

            long startTime = System.currentTimeMillis();

            try {
                // Simulate processing delay
                int delayMs = 50 + (int)(Math.random() * 100); // 50-150ms
                Thread.sleep(delayMs);

                // Generate "intelligent" response based on the prompt
                String processedPrompt = prompt.toLowerCase().trim();
                String responseText = generateIntelligentResponse(processedPrompt);

                response.setOutput(responseText);
                response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                response.setSuccess(true);

                // Add some metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("confidence", 0.9 + Math.random() * 0.1); // 0.9-1.0
                metadata.put("processing_delay_ms", delayMs);
                response.setMetadata(metadata);

            } catch (InterruptedException e) {
                response.setSuccess(false);
                response.setErrorMessage("Processing interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                response.setSuccess(false);
                response.setErrorMessage("Generation failed: " + e.getMessage());
            }

            return response;
        }

        @Override
        public ModelResponse generate(String prompt) {
            return generate(prompt, new HashMap<>());
        }

        private String generateIntelligentResponse(String prompt) {
            // Search knowledge base
            for (Map.Entry<String, String> entry : KNOWLEDGE_BASE.entrySet()) {
                if (prompt.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // Generate "intelligent" fallback response
            if (prompt.contains("what") || prompt.contains("why")) {
                return "Based on my analysis, here is what I know related to your question: " + prompt.substring(0, Math.min(prompt.length(), 30)) + "...";
            } else if (prompt.contains("how")) {
                return "Let me explain the steps: First, " + prompt.substring(0, Math.min(prompt.length(), 20)) + "... Then, the process continues.";
            } else if (prompt.contains("when")) {
                return "That depends on various factors, but typically when " + prompt + " occurs.";
            } else {
                return "I have thoughtfully considered: " + prompt + ". Here is my intelligent response based on careful analysis.";
            }
        }
    }

    /**
     * Example of a custom evaluator implementation.
     */
    public static class CustomEvaluator implements Evaluator {

        @Override
        public String getEvaluatorName() {
            return "CustomSmartEvaluator";
        }

        @Override
        public String getEvaluationType() {
            return "intelligence_test";
        }

        @Override
        public boolean supportsModel(com.evalscope.model.Model model) {
            // This evaluator supports any chat model
            return true;
        }

        @Override
        public EvaluationResult evaluate(com.evalscope.model.Model model, EvaluationData data) {
            return evaluate(model, data, new HashMap<>());
        }

        @Override
        public EvaluationResult evaluate(com.evalscope.model.Model model, EvaluationData data,
                                       Map<String, Object> parameters) {

            EvaluationResult result = new EvaluationResult(getEvaluatorName(), model.getModelId(), data.getTaskType());

            System.out.println("Running custom evaluation for model: " + model.getModelId());

            List<com.evalscope.evaluator.TestCase> testCases = data.getTestCases();
            List<com.evalscope.evaluator.TestResult> testResults = new ArrayList<>();

            for (com.evalscope.evaluator.TestCase testCase : testCases) {
                com.evalscope.evaluator.TestResult testResult = evaluateSingleTest(model, testCase, parameters);
                testResults.add(testResult);
            }

            result.setTestResults(testResults);

            // Add custom metrics
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("intelligence_score", calculateIntelligenceScore(testResults));
            metrics.put("creativity_index", calculateCreativityIndex(testResults));
            metrics.put("knowledge_depth", calculateKnowledgeDepth(testResults));
            result.setMetrics(metrics);

            return result;
        }

        private com.evalscope.evaluator.TestResult evaluateSingleTest(com.evalscope.model.Model model,
                                                                    com.evalscope.evaluator.TestCase testCase,
                                                                    Map<String, Object> parameters) {

            com.evalscope.evaluator.TestResult testResult = new com.evalscope.evaluator.TestResult(
                testCase.getId(), testCase.getInput(), testCase.getExpectedOutput()
            );

            try {
                // Generate response from model
                com.evalscope.model.ModelResponse response;
                if (model instanceof com.evalscope.model.ChatModel) {
                    response = ((com.evalscope.model.ChatModel) model).generate(testCase.getInput(), parameters);
                } else {
                    throw new IllegalArgumentException("Model must be a ChatModel for this evaluator");
                }

                if (response.isSuccess()) {
                    String actualOutput = response.getOutput();
                    testResult.setActualOutput(actualOutput);

                    // Custom scoring logic
                    double score = calculateCustomScore(testCase.getExpectedOutput(), actualOutput);
                    testResult.setScore(score);
                    testResult.setPassed(score >= 0.6); // Lower threshold for "intelligence"

                    String feedback = generateCustomFeedback(testCase.getExpectedOutput(), actualOutput, score);
                    testResult.setFeedback(feedback);

                    // Add custom metrics
                    Map<String, Object> metrics = new HashMap<>();
                    metrics.put("response_quality", score > 0.8 ? "excellent" : score > 0.5 ? "good" : "needs_improvement");
                    metrics.put("creativity_level", estimateCreativityLevel(actualOutput));
                    testResult.setMetrics(metrics);

                } else {
                    testResult.setErrorMessage("Model generation failed: " + response.getErrorMessage());
                    testResult.setScore(0.0);
                    testResult.setPassed(false);
                }

            } catch (Exception e) {
                testResult.setErrorMessage("Evaluation failed: " + e.getMessage());
                testResult.setScore(0.0);
                testResult.setPassed(false);
            }

            return testResult;
        }

        private double calculateCustomScore(String expected, String actual) {
            // More sophisticated scoring based on intelligence, creativity, relevance
            double exactMatchScore = expected.equalsIgnoreCase(actual.trim()) ? 1.0 : 0.0;
            double lengthScore = Math.min(1.0, actual.length() / (double) expected.length());
            double containsExpected = actual.toLowerCase().contains(expected.toLowerCase()) ? 0.8 : 0.0;

            double baseScore = Math.max(exactMatchScore, Math.max(containsExpected, lengthScore * 0.5));

            // Add creativity bonus
            int wordCount = actual.split("\\s+").length;
            double creativityBonus = Math.min(0.3, wordCount / 20.0); // More words = more creativity (up to limit)

            return Math.min(1.0, baseScore + creativityBonus);
        }

        private String generateCustomFeedback(String expected, String actual, double score) {
            if (score >= 0.8) {
                return "Excellent intelligent response demonstrating strong AI capabilities";
            } else if (score >= 0.6) {
                return "Good response with reasonable intelligence and creativity";
            } else {
                return "Response needs improvement in intelligence and understanding";
            }
        }

        private String estimateCreativityLevel(String response) {
            int uniqueWords = new HashSet<String>(Arrays.asList(response.toLowerCase().split("\\s+"))).size();
            return uniqueWords > 10 ? "high" : uniqueWords > 5 ? "medium" : "low";
        }

        private double calculateIntelligenceScore(List<com.evalscope.evaluator.TestResult> testResults) {
            return testResults.stream().mapToDouble(com.evalscope.evaluator.TestResult::getScore).average().orElse(0.0);
        }

        private double calculateCreativityIndex(List<com.evalscope.evaluator.TestResult> testResults) {
            return testResults.stream()
                .filter(result -> result.getMetrics() != null && result.getMetrics().containsKey("creativity_level"))
                .mapToDouble(result -> {
                    String level = (String) result.getMetrics().get("creativity_level");
                    return "high".equals(level) ? 1.0 : "medium".equals(level) ? 0.5 : 0.0;
                })
                .average()
                .orElse(0.0);
        }

        private double calculateKnowledgeDepth(List<com.evalscope.evaluator.TestResult> testResults) {
            return (double) testResults.stream()
                .filter(com.evalscope.evaluator.TestResult::isPassed)
                .count() / testResults.size();
        }
    }
}