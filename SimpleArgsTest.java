import com.evalscope.cli.ArgumentParser;
import com.evalscope.cli.CommandLineArgs;

public class SimpleArgsTest {
    public static void main(String[] args) {
        String[] testArgs = args.length == 0 ? new String[] {
            "--dataset-path", "test_prompts.txt",
            "--dataset", "line_by_line",
            "--url", "http://localhost:8080",
            "--model", "test-model",
            "--concurrent", "1",
            "--number", "5"
        } : args;

        System.out.println("=== 参数解析测试 ===");
        System.out.println("命令行参数: " + String.join(" ", testArgs));

        try {
            CommandLineArgs cmdArgs = ArgumentParser.parse(testArgs);
            System.out.println("\n✅ 参数解析成功！");
            System.out.println("\n解析结果:");
            System.out.println("  --dataset-path: " + cmdArgs.getDatasetPath());
            System.out.println("  --dataset: " + cmdArgs.getDataset());
            System.out.println("  --url: " + cmdArgs.getUrl());
            System.out.println("  --model: " + cmdArgs.getModel());
            System.out.println("  --concurrent: " + cmdArgs.getConcurrent());
            System.out.println("  --number: " + cmdArgs.getNumber());
            System.out.println("  --dry-run: " + cmdArgs.getDryRun());
            System.out.println("  --debug: " + cmdArgs.getDebug());

        } catch (Exception e) {
            System.out.println("\n❌ 参数解析失败！");
            e.printStackTrace();
        }
    }
}