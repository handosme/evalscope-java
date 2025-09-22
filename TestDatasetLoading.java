import com.evalscope.config.YamlConfigManager;
import com.evalscope.config.DatasetConfig;
import com.evalscope.data.DataLoaderFactory;
import com.evalscope.data.LineByLineDatasetLoader;
import com.evalscope.evaluator.TestCase;
import com.evalscope.runner.EvaluationRunner;
import com.evalscope.model.ChatModel;
import com.evalscope.model.ModelResponse;
import com.evalscope.benchmark.PerformanceBenchmark;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TestDatasetLoading {

    public static void main(String[] args) {
        System.out.println("=== Testing Direct Dataset Loading ===");

        try {
            // Test direct loading of line by line dataset
            DatasetConfig config = new DatasetConfig("test", "txt", "test_prompts.txt");
            Map<String, Object> params = new HashMap<>();
            params.put("dataset", "line_by_line");
            params.put("skip_lines", 0);
            params.put("limit", 10);
            config.setParameters(params);

            System.out.println("Dataset path: " + config.getPath());
            System.out.println("Dataset type: " + config.getParameters().get("dataset"));

            List<TestCase> testCases = DataLoaderFactory.loadDataset(config);

            System.out.println("Loaded " + testCases.size() + " test cases:");
            for (int i = 0; i < Math.min(5, testCases.size()); i++) {
                TestCase tc = testCases.get(i);
                System.out.println("  " + (i+1) + ". ID: " + tc.getId() + ", Prompt: '" + tc.getInput().substring(0, Math.min(20, tc.getInput().length())) + "...'");
            }

            System.out.println("\n=== Testing PerformanceBenchmark with line_by_line dataset ===");

            // Test the benchmark with line by line dataset
            Map<String, Object> benchmarkParams = new HashMap<>();
            benchmarkParams.put("dataset", "line_by_line");
            benchmarkParams.put("datasetPath", "test_prompts.txt");
            benchmarkParams.put("warmup_iterations", 2);
            benchmarkParams.put("test_iterations", 5);
            benchmarkParams.put("concurrent", 1);

            // Create a simple mock model for testing
            MockChatModel mockModel = new MockChatModel("test-model", "chat");

            PerformanceBenchmark benchmark = new PerformanceBenchmark();
            com.evalscope.benchmark.BenchmarkResult benchmarkResult = benchmark.run(mockModel, benchmarkParams);

            System.out.println("Benchmark completed successfully: " + benchmarkResult.isSuccess());
            if (benchmarkResult.isSuccess()) {
                System.out.println("Requests per second: " + benchmarkResult.getMetric("requests_per_second"));
                System.out.println("Average response time: " + benchmarkResult.getMetric("average_response_time_ms") + " ms");
            }

            System.out.println("\n=== SUCCESS: Dataset loading functionality working! ===");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Simple mock model for testing
    static class MockChatModel extends ChatModel {
        private boolean loaded = false;

        public MockChatModel(String modelId, String modelType) {
            super(modelId, modelType);
        }

        @Override
        public void load() throws Exception {
            loaded = true;
            System.out.println("Mock model loaded");
        }

        @Override
        public void unload() throws Exception {
            loaded = false;
            System.out.println("Mock model unloaded");
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public ModelResponse generate(String prompt) {
            return generate(prompt, null);
        }

        @Override
        public ModelResponse generate(String prompt, Map<String, Object> parameters) {
            System.out.println("Generating response for prompt: '" + prompt.substring(0, Math.min(15, prompt.length())) + "...'");

            ModelResponse response = new ModelResponse(getModelId(), getModelType());
            response.setOutput("Mock response to: " + prompt);
            response.setProcessingTimeMs(100);
            response.setSuccess(true);

            // Add small delay to simulate processing
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return response;
        }
    }
}