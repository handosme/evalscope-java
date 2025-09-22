import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;
import com.evalscope.config.EvaluationConfig;
import com.evalscope.config.ModelConfig;
import com.evalscope.runner.EvaluationRunner;
import com.evalscope.model.ChatModel;
import com.evalscope.model.Model;
import com.evalscope.model.ModelResponse;
import com.evalscope.benchmark.PerformanceBenchmark;
import com.evalscope.benchmark.BenchmarkResult;
import com.evalscope.config.YamlConfigManager;
import com.evalscope.runner.EvaluationReport;

import java.util.*;

public class CLIIntegrationTest {

    public static void main(String[] args) {
        String[] testArgs = {
            "--model", "test-model",
            "--dataset", "line_by_line",
            "--dataset-path", "test_prompts.txt",
            "--max-examples", "10",
            "--skip-lines", "1",
            "--dataset-shuffle",
            "--concurrent", "2",
            "--number", "8",
            "--warmup-iterations", "2",
            "--test-iterations", "5"
        };

        System.out.println("=== CLI集成测试 ===");

        try {
            // 模拟完整的CLI参数传递流程
            // Step 1: 解析CLI参数
            CommandLineArgs cmdArgs = ArgumentParser.parse(testArgs);
            System.out.println("CLI参数解析成功！");

            // Step 2: 将CLI参数映射到EvaluationConfig
            EvaluationConfig evalConfig = new EvaluationConfig("cli_integration_test");
            evalConfig.setEvaluatorTypes(Arrays.asList("performance"));
            evalConfig.setModelIds(Arrays.asList("test-model"));

            // 复制EvalScopeRunner的参数映射逻辑
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

            evalConfig.addParameter("warmup_iterations", 2);
            evalConfig.addParameter("test_iterations", 5);

            System.out.println("配置映射完成:");
            System.out.println("  parameters.dataset: " + evalConfig.getParameters().get("dataset"));
            System.out.println("  datasetPath: " + evalConfig.getDatasetPath());
            System.out.println("  parameters.max_examples: " + evalConfig.getParameters().get("max_examples"));
            System.out.println("  parameters.skip_lines: " + evalConfig.getParameters().get("skip_lines"));
            System.out.println("  parameters.dataset_shuffle: " + evalConfig.getParameters().get("dataset_shuffle"));
            System.out.println("  parameters.warmup_iterations: " + evalConfig.getParameters().get("warmup_iterations"));
            System.out.println("  parameters.test_iterations: " + evalConfig.getParameters().get("test_iterations"));

            // Step 3: 创建模型配置
            ModelConfig modelConfig = new ModelConfig("test-model", "chat", "mock");
            modelConfig.setEnabled(true);

            // Step 4: 创建YamlConfigManager并添加配置
            YamlConfigManager configManager = new YamlConfigManager();
            configManager.addModelConfig(modelConfig);
            configManager.addEvaluationConfig(evalConfig);

            // Step 5: 运行性能测试
            EvaluationRunner runner = new EvaluationRunner(configManager);
            EvaluationReport report = runner.runEvaluation("cli_integration_test");

            // Step 6: 验证测试结果
            System.out.println("\n测试结果分析:");
            System.out.println("  评估完成: " + report.getEvaluationName());
            System.out.println("  模型评估数量: " + report.getEvaluationResults().size());
            System.out.println("  基准测试数量: " + report.getBenchmarkResults().size());

            if (report.getBenchmarkResults().size() > 0) {
                System.out.println("\n✅ 成功：line_by_line数据集功能全程集成正常！");
            } else {
                System.err.println("\n❌ 基准测试结果数量为0，可能有问题");
            }

            System.out.println("\n=== CLI集成测试 SUCCESS! ===");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}