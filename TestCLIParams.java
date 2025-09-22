import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;
import com.evalscope.config.EvaluationConfig;
import com.evalscope.EvalScopeRunner;

public class TestCLIParams {
    public static void main(String[] args) {
        //Test CommandLineArgs parsing
        String[] testArgs = {
            "--dataset", "line_by_line",
            "--dataset-path", "test_prompts.txt",
            "--max-examples", "10",
            "--skip-lines", "1",
            "--dataset-shuffle",
            "--concurrent", "1",
            "--number", "5",
            "--warmup-iterations", "2",
            "--test-iterations", "3"
        };

        System.out.println("=== Testing CLI参数解析和传递 ===");

        try {
            // Test ArgumentParser
            CommandLineArgs cmdArgs = ArgumentParser.parse(testArgs);
            System.out.println("CLI参数解析成功！");
            System.out.println("  dataset: " + cmdArgs.getDataset());
            System.out.println("  datasetPath: " + cmdArgs.getDatasetPath());
            System.out.println("  maxExamples: " + cmdArgs.getMaxExamples());
            System.out.println("  skipLines: " + cmdArgs.getSkipLines());
            System.out.println("  datasetShuffle: " + cmdArgs.getDatasetShuffle());
            System.out.println("  concurrent: " + cmdArgs.getConcurrent());
            System.out.println();

            // Test配置创建
            com.evalscope.config.EvaluationConfig evalConfig = new com.evalscope.config.EvaluationConfig("cli_test");

            // 模拟EvalScopeRunner的映射逻辑
            if (cmdArgs.getDataset() != null) {
                evalConfig.addParameter("dataset", cmdArgs.getDataset());
            }
            if (cmdArgs.getDatasetPath() != null) {
                evalConfig.setDatasetPath(cmdArgs.getDatasetPath());
            }
            if (cmdArgs.getMaxExamples() != null) {
                evalConfig.addParameter("max_examples", cmdArgs.getMaxExamples());
            }
            if (cmdArgs.getSkipLines() != null) {
                evalConfig.addParameter("skip_lines", cmdArgs.getSkipLines());
            }
            if (cmdArgs.getDatasetShuffle() != null && cmdArgs.getDatasetShuffle()) {
                evalConfig.addParameter("dataset_shuffle", true);
            }

            System.out.println("配置映射结果:");
            System.out.println("  parameters.dataset: " + evalConfig.getParameters().get("dataset"));
            System.out.println("  datasetPath: " + evalConfig.getDatasetPath());
            System.out.println("  parameters.max_examples: " + evalConfig.getParameters().get("max_examples"));
            System.out.println("  parameters.skip_lines: " + evalConfig.getParameters().get("skip_lines"));
            System.out.println("  parameters.dataset_shuffle: " + evalConfig.getParameters().get("dataset_shuffle"));

            System.out.println("\n=== CLI参数解析和配置映射 SUCCESS! ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}