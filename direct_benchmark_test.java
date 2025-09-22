import com.evalscope.config.EvaluationConfig;
import com.evalscope.benchmark.PerformanceBenchmark;
import com.evalscope.benchmark.BenchmarkResult;
import com.evalscope.data.DataLoaderFactory;
import com.evalscope.data.LineByLineDatasetLoader;
import com.evalscope.model.ChatModel;
import com.evalscope.model.ModelResponse;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class direct_benchmark_test {
    public static void main(String[] args) {
        System.out.println("=== Direct PerformanceBenchmark Test with Line by Line Dataset ===");

        try {
            // Create evaluation config with line_by_line dataset
            EvaluationConfig config = new EvaluationConfig("test_performance");
            config.setModelIds(new ArrayList<String>());
            config.setEvaluatorTypes(new ArrayList<String>());

            // Set parameters to trigger line_by_line dataset loading
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("dataset", "line_by_line");
            params.put("datasetPath", "test_prompts.txt");
            params.put("max_examples", 10);
            params.put("warmup_iterations", 2);
            params.put("test_iterations", 5);
            config.setParameters(params);

            System.out.println("Configuration parameters:");
            System.out.println("  dataset: " + params.get("dataset"));
            System.out.println("  datasetPath: " + params.get("datasetPath"));
            System.out.println("  max_examples: " + params.get("max_examples"));
            System.out.println();

            // Create mock model
            MockChatModel model = new MockChatModel("test_model", "chat");

            // Test the PerformanceBenchmark directly
            PerformanceBenchmark benchmark = new PerformanceBenchmark();
            BenchmarkResult result = benchmark.run(model, config.getParameters());

            System.out.println("Benchmark result:");
            System.out.println("  Success: " + result.isSuccess());
            if (result.isSuccess()) {
                System.out.println("  Avg response time: " + result.getMetric("average_response_time_ms") + " ms");
                System.out.println("  Requests/second: " + result.getMetric("requests_per_second"));
                System.out.println("  Test iterations: " + result.getMetric("total_requests"));
        }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Simple mock model similar to existing tests
    static class MockChatModel extends ChatModel {
        private boolean loaded = false;

        public MockChatModel(String modelId, String modelType) {
            super(modelId, modelType);
        }

        @Override
        public void load() throws Exception {
            loaded = true;
            System.out.println("MockChatModel loaded");
        }

        @Override
        public void unload() throws Exception {
            loaded = false;
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
            System.out.println("Processing prompt: '" + prompt.substring(0, Math.min(20, prompt.length())) + "...'");

            ModelResponse response = new ModelResponse(getModelId(), getModelType());
            response.setOutput("Response to: " + prompt);
            response.setProcessingTimeMs(100);
            response.setSuccess(true);

            // Simulate processing delay
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            return response;
        }
    }
}