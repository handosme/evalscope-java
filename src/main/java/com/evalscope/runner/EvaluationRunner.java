package com.evalscope.runner;

import com.evalscope.config.ConfigManager;
import com.evalscope.config.IConfigManager;
import com.evalscope.config.EvaluationConfig;
import com.evalscope.config.ModelConfig;
import com.evalscope.config.DatasetConfig;
import com.evalscope.data.DataLoaderFactory;
import com.evalscope.evaluator.Evaluator;
import com.evalscope.evaluator.EvaluationResult;
import com.evalscope.evaluator.EvaluationData;
import com.evalscope.evaluator.TestCase;
import com.evalscope.model.Model;
import com.evalscope.model.ModelFactory;
import com.evalscope.benchmark.Benchmark;
import com.evalscope.benchmark.BenchmarkResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.io.IOException;

public class EvaluationRunner {
    private final com.evalscope.config.IConfigManager configManager;
    private final ExecutorService executorService;

    public EvaluationRunner(IConfigManager configManager) {
        this.configManager = configManager;
        this.executorService = Executors.newCachedThreadPool();
    }

    public EvaluationRunner(IConfigManager configManager, int maxConcurrency) {
        this.configManager = configManager;
        this.executorService = Executors.newFixedThreadPool(maxConcurrency);
    }

    public EvaluationReport runEvaluation(String evaluationName) {
        EvaluationConfig config = configManager.getEvaluationConfig(evaluationName);
        if (config == null) {
            throw new IllegalArgumentException("Evaluation config not found: " + evaluationName);
        }

        return runEvaluation(config);
    }

    public EvaluationReport runEvaluation(EvaluationConfig config) {
        EvaluationReport report = new EvaluationReport(config.getEvaluationName());

        System.out.println("Starting evaluation: " + config.getEvaluationName());
        System.out.println("Models to evaluate: " + config.getModelIds());
        System.out.println("Evaluators to use: " + config.getEvaluatorTypes());

        try {
            // Run evaluations for each model
            for (String modelId : config.getModelIds()) {
                try {
                    ModelConfig modelConfig = configManager.getModelConfig(modelId);
                    if (modelConfig == null || !modelConfig.isEnabled()) {
                        System.out.println("Skipping model: " + modelId + " (not configured or disabled)");
                        continue;
                    }

                    Model model = createModel(modelConfig);
                    if (model == null) {
                        System.err.println("Failed to create model: " + modelId);
                        continue;
                    }

                    runModelEvaluation(model, config, report);
                    runModelBenchmarks(model, config, report);

                } catch (Exception e) {
                    System.err.println("Error evaluating model " + modelId + ": " + e.getMessage());
                }
            }

        } finally {
            // Generate final summary
            System.out.println("");
            System.out.println("=== Evaluation Summary ===");
            Map<String, Object> summary = report.getSummary();

            System.out.println("Generated at: " + report.getGeneratedAt());
            System.out.println("Total models evaluated: " + report.getTotalModels());
            System.out.println("Total benchmarks run: " + report.getTotalBenchmarks());

            if (summary.containsKey("evaluation")) {
                Map<String, Object> evalSummary = (Map<String, Object>) summary.get("evaluation");
                System.out.println("Successful evaluations: " + evalSummary.get("successful_evaluations"));
                System.out.println("Failed evaluations: " + evalSummary.get("failed_evaluations"));
            }

            if (summary.containsKey("benchmark")) {
                Map<String, Object> benchSummary = (Map<String, Object>) summary.get("benchmark");
                System.out.println("Successful benchmarks: " + benchSummary.get("successful_benchmarks"));
                System.out.println("Failed benchmarks: " + benchSummary.get("failed_benchmarks"));
            }

            System.out.println("Report ID: " + report.getReportId());
        }

        return report;
    }

    private void runModelEvaluation(Model model, EvaluationConfig config, EvaluationReport report) {
        System.out.println("\n--- Evaluating model: " + model.getModelId() + " ---");

        for (String evaluatorType : config.getEvaluatorTypes()) {
            try {
                Evaluator evaluator = RunnerFactory.createEvaluator(evaluatorType);

                if (!evaluator.supportsModel(model)) {
                    System.out.println("Evaluator " + evaluatorType + " does not support model " + model.getModelId());
                    continue;
                }

                System.out.println("Running evaluator: " + evaluator.getEvaluatorName());

                // Create evaluation data - now loads from dataset if available
                EvaluationData data = createEvaluationData(config);

                EvaluationResult result = evaluator.evaluate(model, data, config.getParameters());
                report.addEvaluationResult(result);

                System.out.println("Evaluation completed. Score: " + result.getOverallScore());
                if (!result.isSuccess()) {
                    System.err.println("Evaluation failed: " + result.getErrorMessage());
                }

            } catch (Exception e) {
                System.err.println("Error running evaluator " + evaluatorType + ": " + e.getMessage());
            }
        }
    }

    private void runModelBenchmarks(Model model, EvaluationConfig config, EvaluationReport report) {
        System.out.println("\n--- Running benchmarks for model: " + model.getModelId() + " ---");

        // Run performance benchmark
        try {
            Benchmark performanceBenchmark = RunnerFactory.createBenchmark("performance");
            BenchmarkResult benchmarkResult = performanceBenchmark.run(model, config.getParameters());
            report.addBenchmarkResult(benchmarkResult);

            System.out.println("Performance benchmark completed. Requests per second: " +
                benchmarkResult.getMetric("requests_per_second"));

        } catch (Exception e) {
            System.err.println("Error running performance benchmark: " + e.getMessage());
        }
    }

    private Model createModel(ModelConfig config) {
        try {
            // 使用ModelFactory根据配置参数自适应创建模型
            return ModelFactory.createModel(config);
        } catch (Exception e) {
            System.err.println("Failed to create model from factory: " + e.getMessage());
            System.err.println("Config: " + config.getModelId() + " (provider: " + config.getProvider() + ", type: " + config.getModelType() + ")");

            // 如果Factory创建失败，检查是否启用了mock模型
            if ("mock".equals(config.getProvider()) && config.isEnabled()) {
                return new MockChatModel(config.getModelId(), config.getModelType());
            }

            // 如果配置未明确启用mock且factory失败，返回null让调用者处理
            return null;
        }
    }

    private EvaluationData createEvaluationData(EvaluationConfig config) throws IOException {
        String datasetPath = config.getDatasetPath();
        List<TestCase> testCases;

        if (datasetPath != null && !datasetPath.isEmpty()) {
            System.out.println("Loading dataset from: " + datasetPath);

            // 解析datasetPath，可能是直接的文件路径或是dataset配置名称
            DatasetConfig datasetConfig = null;
            // 尝试从YamlConfigManager获取dataset配置
            if (configManager instanceof com.evalscope.config.YamlConfigManager) {
                com.evalscope.config.YamlConfigManager yamlManager = (com.evalscope.config.YamlConfigManager) configManager;
                if (yamlManager.getDatasetConfig(datasetPath) != null) {
                    datasetConfig = yamlManager.getDatasetConfig(datasetPath);
                }
            }

            if (datasetConfig == null) {
                // datasetPath指向真实的文件，创建临时的数据集配置
                datasetConfig = new DatasetConfig("custom", "txt", datasetPath);

                // 检查参数中是否有dataset类型指定（如：line_by_line）
                String datasetType = (String) config.getParameters().get("dataset");
                if (datasetType != null) {
                    datasetConfig.getParameters().put("dataset", datasetType);
                }
            }

            try {
                // 加载数据集
                testCases = DataLoaderFactory.loadDataset(datasetConfig);
                System.out.println("Loaded " + testCases.size() + " test cases from dataset");
            } catch (Exception e) {
                System.err.println("Failed to load dataset, falling back to sample data: " + e.getMessage());
                testCases = createSampleTestCases();
            }
        } else {
            System.out.println("No dataset specified, using sample test data");
            testCases = createSampleTestCases();
        }

        return new EvaluationData("loaded_dataset", testCases);
    }

    private List<TestCase> createSampleTestCases() {
        // Fallback to sample test cases
        return Arrays.asList(
            new TestCase("test1", "What is 2+2?", "4"),
            new TestCase("test2", "Capital of France?", "Paris"),
            new TestCase("test3", "Translate 'hello' to French", "bonjour"),
            new TestCase("test4", "What is the largest planet?", "Jupiter"),
            new TestCase("test5", "Who wrote Romeo and Juliet?", "Shakespeare")
        );
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Mock model for demonstration
    private static class MockChatModel extends com.evalscope.model.ChatModel {
        public MockChatModel(String modelId, String modelType) {
            super(modelId, modelType);
            setLoaded(true);
        }

        @Override
        public void load() throws Exception {
            setLoaded(true);
        }

        @Override
        public void unload() throws Exception {
            setLoaded(false);
        }

        @Override
        public com.evalscope.model.ModelResponse generate(String prompt, Map<String, Object> parameters) {
            com.evalscope.model.ModelResponse response = new com.evalscope.model.ModelResponse(getModelId(), "chat");

            // Simulate processing time
            try {
                Thread.sleep(100 + (long)(Math.random() * 400)); // 100-500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Generate mock response
            String mockResponse = generateMockResponse(prompt);
            response.setOutput(mockResponse);
            response.setProcessingTimeMs(300); // Mock processing time
            response.setSuccess(true);

            return response;
        }

        @Override
        public com.evalscope.model.ModelResponse generate(String prompt) {
            return generate(prompt, null);
        }

        private String generateMockResponse(String prompt) {
            // Simple mock responses based on input keywords
            if (prompt.toLowerCase().contains("2+2")) {
                return "4";
            } else if (prompt.toLowerCase().contains("capital") && prompt.toLowerCase().contains("france")) {
                return "Paris";
            } else if (prompt.toLowerCase().contains("hello") && prompt.toLowerCase().contains("french")) {
                return "bonjour";
            } else if (prompt.toLowerCase().contains("largest planet")) {
                return "Jupiter";
            } else if (prompt.toLowerCase().contains("romeo") && prompt.toLowerCase().contains("juliet")) {
                return "Shakespeare";
            } else {
                return "This is a mock response to: " + prompt;
            }
        }
    }
}