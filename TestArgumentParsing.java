import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;

public class TestArgumentParsing {
    public static void main(String[] args) {
        System.out.println("Testing --dataset-path argument parsing...");

        String[] testArgs = {
            "--dataset-path", "test_prompts.txt",
            "--dataset", "line_by_line",
            "--concurrent", "1",
            "--number", "5"
        };

        try {
            CommandLineArgs cmdArgs = ArgumentParser.parse(testArgs);
            System.out.println("✓ Argument parsing successful!");
            System.out.println("  dataset-path: " + cmdArgs.getDatasetPath());
            System.out.println("  dataset: " + cmdArgs.getDataset());
            System.out.println("  concurrent: " + cmdArgs.getConcurrent());
            System.out.println("  number: " + cmdArgs.getNumber());
        } catch (Exception e) {
            System.out.println("✗ Argument parsing failed!");
            e.printStackTrace();
        }
    }
}