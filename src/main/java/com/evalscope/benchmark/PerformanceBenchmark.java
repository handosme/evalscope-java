package com.evalscope.benchmark;

import com.evalscope.model.ChatModel;
import com.evalscope.model.Model;
import com.evalscope.model.ModelResponse;
import com.evalscope.evaluator.TestCase;
import com.evalscope.config.DatasetConfig;
import com.evalscope.data.DataLoaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.io.IOException;

public class PerformanceBenchmark implements Benchmark {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceBenchmark.class);
    private static final String BENCHMARK_NAME = "PerformanceBenchmark";
    private static final String BENCHMARK_TYPE = "performance";

    private static final int DEFAULT_WARMUP_ITERATIONS = 5;
    private static final int DEFAULT_TEST_ITERATIONS = 50;
    private static final String DEFAULT_TEST_PROMPT = "Hello, how are you today?";

    @Override
    public String getBenchmarkName() {
        return BENCHMARK_NAME;
    }

    @Override
    public String getBenchmarkType() {
        return BENCHMARK_TYPE;
    }

    @Override
    public boolean supportsModel(Model model) {
        return model instanceof ChatModel;
    }

    @Override
    public BenchmarkResult run(Model model) {
        return run(model, null);
    }

    @Override
    public BenchmarkResult run(Model model, Map<String, Object> parameters) {
        if (!supportsModel(model)) {
            throw new IllegalArgumentException("Model type not supported by this benchmark");
        }

        ChatModel chatModel = (ChatModel) model;
        BenchmarkResult result = new BenchmarkResult(BENCHMARK_NAME, model.getModelId());

        try {
            if (!chatModel.isLoaded()) {
                chatModel.load();
            }

            int warmupIterations = getParameter(parameters, "warmup_iterations", DEFAULT_WARMUP_ITERATIONS);
            int testIterations = getParameter(parameters, "test_iterations", DEFAULT_TEST_ITERATIONS);

            // 加载数据集或prompt列表
            List<String> prompts = loadPromptsForBenchmark(parameters);

            if (prompts.isEmpty()) {
                prompts.add(DEFAULT_TEST_PROMPT);
            }

            logger.info("Using {} prompts for performance testing", prompts.size());
            logger.info("Warmup iterations: {}", warmupIterations);
            logger.info("Test iterations: {}", testIterations);

            // Warmup phase - 随机选择prompt
            logger.info("Running warmup phase ({} iterations)...", warmupIterations);
            Random random = new Random();
            for (int i = 0; i < warmupIterations; i++) {
                String prompt = prompts.get(random.nextInt(prompts.size()));
                chatModel.generate(prompt);
            }

            // Actual benchmarking
            logger.info("Running performance benchmark ({} iterations)...", testIterations);
            List<Long> responseTimes = new ArrayList<>();
            List<Long> tokenCounts = new ArrayList<>();
            int successfulRequests = 0;
            int failedRequests = 0;

            for (int i = 0; i < testIterations; i++) {
                String prompt = prompts.get(random.nextInt(prompts.size()));

                try {
                    long startTime = System.nanoTime();
                    ModelResponse response = chatModel.generate(prompt);
                    long endTime = System.nanoTime();

                    if (response.isSuccess() && response.getOutput() != null) {
                        long responseTimeMs = (endTime - startTime) / 1_000_000; // Convert to milliseconds
                        int tokenCount = estimateTokenCount(response.getOutput());

                        responseTimes.add(responseTimeMs);
                        tokenCounts.add((long) tokenCount);
                        successfulRequests++;
                    } else {
                        failedRequests++;
                    }

                } catch (Exception e) {
                    failedRequests++;
                }

                if ((i + 1) % 10 == 0) {
                    logger.info("Completed {}/{} iterations", i + 1, testIterations);
                }
            }

            // Calculate statistics
            Map<String, Object> metrics = calculateMetrics(
                responseTimes, tokenCounts, successfulRequests, failedRequests, testIterations
            );

            result.setMetrics(metrics);
            result.setSuccess(true);

            logger.info("Performance benchmark completed successfully!");
            logger.info("Average response time: {} ms", metrics.get("average_response_time_ms"));
            logger.info("Requests per second: {}", metrics.get("requests_per_second"));

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Benchmark failed: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> calculateMetrics(
            List<Long> responseTimes,
            List<Long> tokenCounts,
            int successfulRequests,
            int failedRequests,
            int totalRequests) {

        Map<String, Object> metrics = new HashMap<>();

        if (!responseTimes.isEmpty()) {
            // Response time metrics
            metrics.put("min_response_time_ms", responseTimes.stream().min(Long::compareTo).orElse(0L));
            metrics.put("max_response_time_ms", responseTimes.stream().max(Long::compareTo).orElse(0L));
            metrics.put("average_response_time_ms", responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0));

            double medianResponseTime = calculatePercentile(responseTimes, 50);
            double p95ResponseTime = calculatePercentile(responseTimes, 95);
            double p99ResponseTime = calculatePercentile(responseTimes, 99);

            metrics.put("median_response_time_ms", medianResponseTime);
            metrics.put("p95_response_time_ms", p95ResponseTime);
            metrics.put("p99_response_time_ms", p99ResponseTime);
        }

        if (!tokenCounts.isEmpty()) {
            // Token processing metrics
            metrics.put("average_output_tokens", tokenCounts.stream().mapToLong(Long::longValue).average().orElse(0.0));

            if (!responseTimes.isEmpty()) {
                // Token throughput (tokens per second)
                double totalTokens = tokenCounts.stream().mapToLong(Long::longValue).sum();
                double totalTimeSeconds = responseTimes.stream().mapToLong(Long::longValue).sum() / 1000.0;
                double tokensPerSecond = totalTimeSeconds > 0 ? totalTokens / totalTimeSeconds : 0.0;
                metrics.put("tokens_per_second", tokensPerSecond);
            }
        }

        // Request success metrics
        metrics.put("total_requests", totalRequests);
        metrics.put("successful_requests", successfulRequests);
        metrics.put("failed_requests", failedRequests);
        metrics.put("success_rate", (double) successfulRequests / totalRequests);

        if (!responseTimes.isEmpty()) {
            double totalTimeSeconds = responseTimes.stream().mapToLong(Long::longValue).sum() / 1000.0;
            double requestsPerSecond = totalTimeSeconds > 0 ? (double) successfulRequests / totalTimeSeconds : 0.0;
            metrics.put("requests_per_second", requestsPerSecond);
        }

        return metrics;
    }

    private double calculatePercentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return 0.0;

        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));

        return sorted.get(index);
    }

    private int estimateTokenCount(String text) {
        // Simple token estimation: roughly 4 characters per token on average
        return text != null ? text.length() / 4 : 0;
    }

    private List<String> loadPromptsForBenchmark(Map<String, Object> parameters) throws IOException {
        List<String> prompts = new ArrayList<>();

        if (parameters == null) {
            return prompts;
        }

        // 检查是否是通过dataset参数指定的line_by_line模式
        String datasetType = getParameter(parameters, "dataset", null);
        if (!"line_by_line".equalsIgnoreCase(datasetType)) {
            // 如果不是line_by_line模式，检查是否有test_prompt参数
            String testPrompt = getParameter(parameters, "test_prompt", null);
            if (testPrompt != null) {
                prompts.add(testPrompt);
            }
            return prompts;
        }

        // line_by_line模式：加载datasetPath指定的文件
        String datasetPath = getParameter(parameters, "datasetPath", null);
        if (datasetPath == null || datasetPath.isEmpty()) {
            logger.warn("line_by_line dataset specified but datasetPath is missing, using default prompt");
            return prompts;
        }

        try {
            // 创建临时的数据集配置用于加载
            DatasetConfig datasetConfig = new DatasetConfig("performance_dataset", "txt", datasetPath);

            // 从parameters拷贝相关参数到数据集配置中
            Map<String, Object> datasetParams = new HashMap<>();
            datasetParams.put("dataset", "line_by_line");
            datasetParams.put("shuffle", getParameter(parameters, "dataset_shuffle", false));
            datasetParams.put("limit", getParameter(parameters, "max_examples", Integer.MAX_VALUE));
            datasetParams.put("skip_lines", getParameter(parameters, "skip_lines", 0));
            datasetParams.put("line_prefix", getParameter(parameters, "line_prefix", ""));
            datasetConfig.setParameters(datasetParams);

            // 使用DataLoader加载数据集
            List<TestCase> testCases = DataLoaderFactory.loadDataset(datasetConfig);

            // 提取每个testcase的输入作为prompt
            for (TestCase testCase : testCases) {
                prompts.add(testCase.getInput());
            }

            logger.info("Loaded {} prompts from line_by_line dataset: {}", prompts.size(), datasetPath);

        } catch (Exception e) {
            logger.error("Failed to load line_by_line dataset from {}: {}", datasetPath, e.getMessage());
            logger.warn("Falling back to default prompt");
            // fallback已经在方法开始时处理
        }

        return prompts;
    }

    @SuppressWarnings("unchecked")
    private <T> T getParameter(Map<String, Object> parameters, String key, T defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }

        try {
            return (T) parameters.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}