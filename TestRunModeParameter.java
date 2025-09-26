import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;

public class TestRunModeParameter {
    public static void main(String[] args) {
        System.out.println("Testing run-mode parameter parsing...");

        // Test with run-mode parameter
        String[] testArgs = {
            "--url", "http://localhost:8080",
            "--model", "test-model",
            "--run-mode", "test-mode"
        };

        try {
            CommandLineArgs cmdArgs = ArgumentParser.parse(testArgs);

            System.out.println("Successfully parsed arguments:");
            System.out.println("URL: " + cmdArgs.getUrl());
            System.out.println("Model: " + cmdArgs.getModel());
            System.out.println("Run Mode: " + cmdArgs.getRunMode());

            if (cmdArgs.getRunMode() != null) {
                System.out.println("SUCCESS: run-mode parameter was parsed: " + cmdArgs.getRunMode());
            } else {
                System.out.println("ERROR: run-mode parameter was not parsed or is null");
            }

        } catch (Exception e) {
            System.out.println("ERROR parsing arguments: " + e.getMessage());
            e.printStackTrace();
        }
    }
}