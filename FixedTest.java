import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;
import java.util.Arrays;

public class FixedTest {
    public static void main(String[] args) {
        // Test cases to identify the exact issue

        System.out.println("Testing different --dataset-path scenarios...\n");

        // Test 1: Basic parsing from command args
        String[] args1 = {"--dataset-path", "test_prompts.txt", "--dataset", "line_by_line"};
        CommandLineArgs cmdArgs1 = ArgumentParser.parse(args1);
        System.out.println("Test 1 - Basic parsing:");
        System.out.println("  dataset-path: " + cmdArgs1.getDatasetPath());
        System.out.println("  dataset: " + cmdArgs1.getDataset());
        System.out.println();

        // Test 2: Parsing with spaces in path
        String[] args2 = {"--dataset-path", "test file.txt", "--dataset", "line_by_line"};
        CommandLineArgs cmdArgs2 = ArgumentParser.parse(args2);
        System.out.println("Test 2 - Path with spaces:");
        System.out.println("  dataset-path: '" + cmdArgs2.getDatasetPath() + "'");
        System.out.println("  dataset: " + cmdArgs2.getDataset());
        System.out.println();

        // Test 3: Parsing with full path
        String[] args3 = {"--dataset-path", "/Users/kc/kc/dev/ClaudeCode/evalscope-java/test_prompts.txt", "--dataset", "line_by_line"};
        CommandLineArgs cmdArgs3 = ArgumentParser.parse(args3);
        System.out.println("Test 3 - Full path:");
        System.out.println("  dataset-path: " + cmdArgs3.getDatasetPath());
        System.out.println("  dataset: " + cmdArgs3.getDataset());
        System.out.println();

        // Test 4: Check argument order
        String[] args4 = {"--dataset", "line_by_line", "--dataset-path", "test_prompts.txt"};
        CommandLineArgs cmdArgs4 = ArgumentParser.parse(args4);
        System.out.println("Test 4 - Argument order reversed:");
        System.out.println("  dataset-path: " + cmdArgs4.getDatasetPath());
        System.out.println("  dataset: " + cmdArgs4.getDataset());
        System.out.println();

        // Test 5: Multiple parameters like actual usage
        String[] args5 = {
            "--url", "http://localhost:8080",
            "--model", "test-model",
            "--api-key", "test-key",
            "--dataset", "line_by_line",
            "--dataset-path", "test_prompts.txt",
            "--concurrent", "1",
            "--number", "10"
        };
        CommandLineArgs cmdArgs5 = ArgumentParser.parse(args5);
        System.out.println("Test 5 - Multiple parameters:");
        System.out.println("  url: " + cmdArgs5.getUrl());
        System.out.println("  model: " + cmdArgs5.getModel());
        System.out.println("  api-key: " + cmdArgs5.getApiKey());
        System.out.println("  dataset: " + cmdArgs5.getDataset());
        System.out.println("  dataset-path: " + cmdArgs5.getDatasetPath());
        System.out.println("  concurrent: " + cmdArgs5.getConcurrent());
        System.out.println("  number: " + cmdArgs5.getNumber());
    }
}