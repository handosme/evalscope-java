package com.evalscope.example;

import com.evalscope.model.Model;
import com.evalscope.model.ChatModel;
import com.evalscope.model.ModelResponse;
import com.evalscope.model.ModelFactory;
import com.evalscope.model.OpenAICompatibleModel;
import com.evalscope.model.HuggingFaceModel;
import com.evalscope.config.ConfigManager;
import com.evalscope.config.ModelConfig;
import com.evalscope.runner.EvaluationReport;
import com.evalscope.EvalScopeRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 真实AI模型连接和使用示例
 *
 * 这个示例演示了如何直接使用真实的AI模型，包括：
 * 1. OpenAI GPT模型 （需要设置OPENAI_API_KEY环境变量）
 * 2. HuggingFace Hub模型（可选设置HF_API_TOKEN环境变量）
 * 3. 本地部署模型（兼容OpenAI格式）
 *
 * 使用方式:
 * export OPENAI_API_KEY='your-api-key'
 * export HF_API_TOKEN='your-hf-token'  # 可选
 * java -cp target/classes com.evalscope.example.RealModelExample
 *
 * 基于 modelscope/evalscope 项目的实现模式
 */
public class RealModelExample {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== Real AI Models Connection Demo ===");
        System.out.println("基于 https://github.com/modelscope/evalscope");
        System.out.println();

        showMainMenu();
    }

    private static void showMainMenu() {
        while (true) {
            System.out.println("\n请选择一个操作:");
            System.out.println("1. 测试 OpenAI GPT 模型");
            System.out.println("2. 测试 HuggingFace 模型");
            System.out.println("3. 测试本地模型");
            System.out.println("4. 运行模型对比评估");
            System.out.println("5. 查看环境变量设置");
            System.out.println("6. 退出");
            System.out.print("请输入选项 (1-6): ");

            try {
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        testOpenAIModel();
                        break;
                    case "2":
                        testHuggingFaceModel();
                        break;
                    case "3":
                        testLocalModel();
                        break;
                    case "4":
                        runModelComparison();
                        break;
                    case "5":
                        showEnvironmentVariables();
                        break;
                    case "6":
                        System.out.println("再见!");
                        return;
                    default:
                        System.out.println("无效选项，请输入1-6之间的数字");
                }
            } catch (Exception e) {
                System.err.println("操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 测试OpenAI GPT模型
     */
    private static void testOpenAIModel() {
        System.out.println("\n=== OpenAI GPT模型测试 ===");

        // 检查API Key
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("错误: 未设置OPENAI_API_KEY环境变量");
            System.err.println("请先执行: export OPENAI_API_KEY='your-api-key'");
            return;
        }

        try {
            // 使用配置方式创建模型
            ConfigManager configManager = ConfigManager.createDefault();

            ModelConfig openaiConfig = new ModelConfig("gpt-test", "chat", "openai");
            openaiConfig.addParameter("endpoint", "https://api.openai.com/v1");
            openaiConfig.addParameter("model_name", "gpt-3.5-turbo");
            openaiConfig.addParameter("max_tokens", 1024);
            openaiConfig.addParameter("temperature", 0.7);
            openaiConfig.addCredential("api_key", apiKey);
            openaiConfig.setEnabled(true);

            configManager.addModelConfig(openaiConfig);

            // 使用模型工厂创建模型实例
            Model model = ModelFactory.createModel(openaiConfig);

            if (model == null) {
                System.err.println("模型创建失败");
                return;
            }

            System.out.println("正在加载模型: " + model.getModelId());
            model.load();

            System.out.println("模型加载成功!");
            System.out.println("请输入测试问题 (输入'quit'退出):");

            while (true) {
                System.out.print("\u003e ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("quit")) {
                    break;
                }

                if (input.isEmpty()) {
                    continue;
                }

                System.out.println("正在生成响应...");

                // 由于 ChatModel 继承了 Model 接口，我们需要将 Model 转换为 ChatModel 来调用 generate 方法
                if (!(model instanceof ChatModel)) {
                    System.err.println("错误：模型不是 ChatModel 类型，无法生成响应");
                    continue;
                }

                ChatModel chatModel = (ChatModel) model;
                ModelResponse response = chatModel.generate(input);

                if (response.isSuccess()) {
                    System.out.println("💡 模型响应: " + response.getOutput());
                    System.out.println("⚡ 处理时间: " + response.getProcessingTimeMs() + " ms");

                    if (response.getMetadata() != null) {
                        System.out.println("📊 元数据: " + response.getMetadata());
                    }
                } else {
                    System.err.println("❌ 生成失败: " + response.getErrorMessage());
                }
            }

            // 测试完成后卸载模型
            model.unload();
            System.out.println("模型已卸载");

        } catch (Exception e) {
            System.err.println("GPT模型测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试HuggingFace模型
     */
    private static void testHuggingFaceModel() {
        System.out.println("\n=== HuggingFace Hub模型测试 ===");

        String apiToken = System.getenv("HF_API_TOKEN");
        if (apiToken == null || apiToken.trim().isEmpty()) {
            System.out.println("提示: 未设置HF_API_TOKEN环境变量");
            System.out.println("某些HF模型可能不需要token，但设置token可以获得更好的访问配额");
            apiToken = "hf_demo_token";
        }

        try {
            // 创建HuggingFace DialogGPT模型
            HuggingFaceModel hfModel = new HuggingFaceModel("hf-dialogpt", "chat");
            hfModel.setModelName("microsoft/DialoGPT-medium");
            hfModel.setApiToken(apiToken);
            hfModel.setConnectionTimeout(30);
            hfModel.setReadTimeout(120);

            System.out.println("正在加载HuggingFace模型: microsoft/DialoGPT-medium");
            hfModel.load();

            System.out.println("HuggingFace模型加载成功!");
            System.out.println("请输入对话内容 (输入'quit'退出):");

            while (true) {
                System.out.print("\u003e ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("quit")) {
                    break;
                }

                if (input.isEmpty()) {
                    continue;
                }

                System.out.println("正在生成对话响应...");
                ModelResponse response = hfModel.generate(input);

                if (response.isSuccess()) {
                    System.out.println("💭 HF模型的回答: " + response.getOutput());
                    System.out.println("⚡ 处理时间: " + response.getProcessingTimeMs() + " ms");

                    if (response.getMetadata() != null) {
                        System.out.println("📊 使用信息: " + response.getMetadata().entrySet());
                    }
                } else {
                    System.err.println("❌ 生成失败: " + response.getErrorMessage());
                }
            }

            hfModel.unload();
            System.out.println("HuggingFace模型已卸载");

        } catch (Exception e) {
            System.err.println("HuggingFace模型测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试本地模型
     */
    private static void testLocalModel() {
        System.out.println("\n=== 本地模型测试 ===");
        System.out.print("请输入本地模型端点 (默认:http://localhost:8000/v1): ");

        String localEndpoint = scanner.nextLine().trim();
        if (localEndpoint.isEmpty()) {
            localEndpoint = "http://localhost:8000/v1";
        }

        try {
            OpenAICompatibleModel localModel = new OpenAICompatibleModel("local-llama", "chat", "local");
            localModel.setApiEndpoint(localEndpoint);
            localModel.setModelName("llama-2-7b-chat");
            localModel.setApiKey("local-key");
            localModel.setConnectionTimeout(10);
            localModel.setReadTimeout(30);

            System.out.println("正在连接本地模型服务: " + localEndpoint);
            localModel.load();

            System.out.println("本地模型连接成功!");
            System.out.println("请输入测试问题 (输入'quit'退出):");

            while (true) {
                System.out.print("\u003e ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("quit")) {
                    break;
                }

                if (input.isEmpty()) {
                    continue;
                }

                System.out.println("正在生成本地模型响应...");
                ModelResponse response = localModel.generate(input);

                if (response.isSuccess()) {
                    System.out.println("🏠 本地模型响应: " + response.getOutput());
                    System.out.println("⚡ 处理时间: " + response.getProcessingTimeMs() + " ms");
                } else {
                    System.err.println("❌ 本地模型生成失败: " + response.getErrorMessage());
                }
            }

            localModel.unload();
            System.out.println("本地模型连接已关闭");

        } catch (Exception e) {
            System.err.println("本地模型测试失败: " + e.getMessage());
            System.err.println("提示: 请确保本地模型服务正在运行在 " + localEndpoint);
            e.printStackTrace();
        }
    }

    /**
     * 运行模型对比评估
     */
    private static void runModelComparison() {
        System.out.println("\n=== 模型对比评估 ===");

        // 检查必要的API密钥
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey == null || openaiKey.trim().isEmpty()) {
            System.err.println("错误: 未设置OPENAI_API_KEY环境变量");
            return;
        }

        try {
            ConfigManager configManager = ConfigManager.createDefault();

            // 配置OpenAI模型
            ModelConfig openaiConfig = new ModelConfig("openai-gpt", "chat", "openai");
            openaiConfig.addParameter("endpoint", "https://api.openai.com/v1");
            openaiConfig.addParameter("model_name", "gpt-3.5-turbo");
            openaiConfig.addParameter("max_tokens", 1024);
            openaiConfig.addCredential("api_key", openaiKey);
            openaiConfig.setEnabled(true);
            configManager.addModelConfig(openaiConfig);

            // 配置HuggingFace模型
            String hfToken = System.getenv("HF_API_TOKEN");
            ModelConfig hfConfig = new ModelConfig("hf-dialogpt", "chat", "huggingface");
            hfConfig.addParameter("model_name", "microsoft/DialoGPT-medium");
            hfConfig.addParameter("max_tokens", 512);
            if (hfToken != null) {
                hfConfig.addCredential("api_token", hfToken);
            }
            hfConfig.setEnabled(true);
            configManager.addModelConfig(hfConfig);

            // 运行评估
            EvalScopeRunner runner = new EvalScopeRunner(configManager);
            EvaluationReport report = runner.runEvaluation("model_comparison");

            System.out.println("\n=== 对比评估结果 ===");
            System.out.println("📊 总模型数: " + report.getTotalModels());
            System.out.println("📈 成功率: " +
                report.getSummary().getOrDefault("successful_evaluations", "0") + "/" +
                report.getTotalModels());

            System.out.println("\n详细结果已保存到 results/ 目录");

        } catch (Exception e) {
            System.err.println("模型对比评估失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 显示环境变量设置
     */
    private static void showEnvironmentVariables() {
        System.out.println("\n=== 环境变量设置 ===");

        String openaiKey = System.getenv("OPENAI_API_KEY");
        String hfToken = System.getenv("HF_API_TOKEN");

        System.out.println("OPENAI_API_KEY: " +
            (openaiKey != null ? "✅ 已设置 (" + openaiKey.substring(0, Math.min(10, openaiKey.length())) + "...)" :
             "❌ 未设置"));

        System.out.println("HF_API_TOKEN: " +
            (hfToken != null ? "✅ 已设置 (" + hfToken.substring(0, Math.min(10, hfToken.length())) + "...)" :
             "❌ 未设置（可选）"));

        System.out.println("\n💡 设置说明:");
        System.out.println("1. OpenAI API Key: 访问 https://platform.openai.com/api-keys 获取");
        System.out.println("2. HuggingFace Token: 访问 https://huggingface.co/settings/tokens 获取");
        System.out.println("3. 本地模型: 需要运行兼容OpenAI格式的本地服务");
    }

    /**
     * 获取使用说明
     */
    private static void printUsage() {
        System.out.println("\n=== 使用说明 ===");
        System.out.println("环境变量设置:");
        System.out.println("  export OPENAI_API_KEY='your-api-key'");
        System.out.println("  export HF_API_TOKEN='your-hf-token'  # 可选");
        System.out.println();
        System.out.println("运行程序:");
        System.out.println("  java -cp target/classes com.evalscope.example.RealModelExample");
    }

    /**
     * 创建配置的快捷方法 - 类似modelscope风格
     */
    public static Model createOpenAIModel(String name, String apiKey) {
        ConfigManager configManager = ConfigManager.createDefault();

        ModelConfig config = new ModelConfig(name, "chat", "openai");
        config.addParameter("endpoint", "https://api.openai.com/v1");
        config.addParameter("model_name", "gpt-3.5-turbo");
        config.addCredential("api_key", apiKey);
        config.setEnabled(true);

        configManager.addModelConfig(config);

        return ModelFactory.createModel(config);
    }

    /**
     * 获取支持的模型列表 - 类似modelscope风格
     */
    public static List<String> getSupportedModels() {
        List<String> models = new ArrayList<>();
        models.add("openai/gpt-3.5-turbo");
        models.add("openai/gpt-4");
        models.add("huggingface/microsoft/DialogoGPT-medium");
        models.add("huggingface/distilgpt2");
        models.add("local/llama2-7b-chat");
        models.add("local/any-openai-compatible");
        return models;
    }
}