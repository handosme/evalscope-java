package com.evalscope.example;

import com.evalscope.model.ChatModel;
import com.evalscope.model.ModelResponse;
import com.evalscope.model.ModelFactory;
import com.evalscope.model.OpenAICompatibleModel;
import com.evalscope.model.HuggingFaceModel;
import com.evalscope.evaluator.Evaluator;
import com.evalscope.evaluator.EvaluationResult;
import com.evalscope.evaluator.EvaluationData;
import com.evalscope.config.ConfigManager;
import com.evalscope.config.ModelConfig;
import com.evalscope.config.EvaluationConfig;
import com.evalscope.EvalScopeRunner;
import com.evalscope.runner.EvaluationReport;

import java.util.*;

/**
 * 真实AI模型连接和评估示例
 * 演示如何使用真实的OpenAI API和HuggingFace Hub模型进行模型评估
 * 类似于 https://github.com/modelscope/evalscope 的实现
 */
public class CustomModelExample {

    public static void main(String[] args) {
        System.out.println("=== 真实AI模型连接和评估示例 ===");
        System.out.println("基于 https://github.com/modelscope/evalscope 风格实现");
        System.out.println();

        // 设置配置管理器
        ConfigManager configManager = ConfigManager.createDefault();

        try {
            // 示例1: 配置并测试OpenAI GPT模型
            configureOpenAIModel(configManager);

            // 示例2: 配置并测试HuggingFace模型
            configureHuggingFaceModel(configManager);

            // 示例3: 配置本地部署模型（兼容OpenAI API）
            configureLocalModel(configManager);

            // 创建评估运行器并执行评估
            EvalScopeRunner runner = new EvalScopeRunner(configManager);

            // 执行OpenAI模型评估
            runOpenAIEvaluation(runner);

            // 执行HuggingFace模型评估
            runHuggingFaceEvaluation(runner);

            // 执行本地模型评估
            runLocalEvaluation(runner);

            // 运行批量对比评估
            runBatchEvaluation(runner);

            // 演示直接模型评估
            directModelEvaluationDemo();

            runner.shutdown();
            System.out.println("\n=== 所有评估任务完成 ===");

        } catch (Exception e) {
            System.err.println("评估过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 配置OpenAI GPT模型进行真实API调用
     */
    private static void configureOpenAIModel(ConfigManager configManager) {
        System.out.println("正在配置 OpenAI GPT 模型...");

        // 创建OpenAI模型配置（需要真实的API key）
        ModelConfig openaiConfig = new ModelConfig("gpt-3.5-turbo", "chat", "openai");

        // OpenAI API基础配置，endpoint是必填字段，默认使用openai官方地址
        openaiConfig.addParameter("endpoint", "https://api.openai.com/v1");
        openaiConfig.addParameter("model_name", "gpt-3.5-turbo");

        // 模型行为参数
        openaiConfig.addParameter("max_tokens", 2048);
        openaiConfig.addParameter("temperature", 0.7);
        openaiConfig.addParameter("top_p", 0.9);

        // 连接配置
        openaiConfig.addParameter("connect_timeout", 30);
        openaiConfig.addParameter("read_timeout", 60);
        openaiConfig.addParameter("max_retries", 3);
        openaiConfig.addParameter("retry_delay", 1000);

        // 重要: API密钥应该通过环境变量或安全的配置管理获取
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("警告: 未设置 OPENAI_API_KEY 环境变量，OpenAI模型将不会真正连接到API");
            System.err.println("请执行: export OPENAI_API_KEY='your-api-key'");
            apiKey = "sk-demo-key-please-set-real-key"; // 演示用
        }

        openaiConfig.addCredential("api_key", apiKey);
        openaiConfig.setEnabled(true);

        configManager.addModelConfig(openaiConfig);
        System.out.println("OpenAI GPT-3.5-turbo 模型配置完成");
    }

    /**
     * 配置HuggingFace Hub模型进行真实API调用
     */
    private static void configureHuggingFaceModel(ConfigManager configManager) {
        System.out.println("正在配置 HuggingFace Hub 模型...");

        // 创建HuggingFace模型配置
        ModelConfig hfConfig = new ModelConfig("microsoft/DialoGPT-medium", "chat", "huggingface");

        hfConfig.addParameter("model_name", "microsoft/DialoGPT-medium");
        hfConfig.addParameter("endpoint", "https://api-inference.huggingface.co");

        // HuggingFace模型通常需要更长的读取超时
        hfConfig.addParameter("read_timeout", 120);
        hfConfig.addParameter("connect_timeout", 30);
        hfConfig.addParameter("max_retries", 5);
        hfConfig.addParameter("retry_delay", 2000);

        // 模型参数（HF格式）
        hfConfig.addParameter("max_tokens", 512);
        hfConfig.addParameter("temperature", 0.7);
        hfConfig.addParameter("top_p", 0.9);
        hfConfig.addParameter("top_k", 50);

        // API Token设置（从环境变量获取更安全）
        String apiToken = System.getenv("HF_API_TOKEN");
        if (apiToken == null || apiToken.trim().isEmpty()) {
            System.err.println("警告: 未设置 HF_API_TOKEN 环境变量，HuggingFace模型可能无法正常访问");
            System.err.println("请执行: export HF_API_TOKEN='your-hf-token'");
            // 某些公共模型可能不需要token
            apiToken = "hf_demo_token";
        }
        hfConfig.addCredential("api_token", apiToken);
        hfConfig.setEnabled(true);

        configManager.addModelConfig(hfConfig);
        System.out.println("HuggingFace DialoGPT-midium 模型配置完成");
    }

    /**
     * 配置本地部署模型（兼容OpenAI API格式）
     */
    private static void configureLocalModel(ConfigManager configManager) {
        System.out.println("正在配置本地部署模型...");

        ModelConfig localConfig = new ModelConfig("local-llama", "chat", "local");

        // 本地部署通常使用类似OpenAI的兼容接口
        localConfig.addParameter("endpoint", "http://localhost:8000/v1");
        localConfig.addParameter("model_name", "llama-2-7b-chat");

        localConfig.addParameter("max_tokens", 1024);
        localConfig.addParameter("temperature", 0.8);

        localConfig.addParameter("connect_timeout", 10);
        localConfig.addParameter("read_timeout", 30);
        localConfig.addParameter("max_retries", 2);

        // 本地模型通常不需要API Key，或者使用默认的
        localConfig.addCredential("api_key", "local-key");
        localConfig.setEnabled(true);

        configManager.addModelConfig(localConfig);
        System.out.println("本地Llama2-7B-chat模型配置完成");
    }

    /**
     * 运行OpenAI模型评估
     */
    private static void runOpenAIEvaluation(EvalScopeRunner runner) {
        System.out.println("\n=== 运行 OpenAI GPT-3.5-Turbo 模型评估 ===");

        // 使用命令行参数方式进行评估，这与modelscope/evalscope的风格相似
        String[] cmdArgs = {
            "--url", "https://api.openai.com/v1",
            "--model", "gpt-3.5-turbo",
            "--api-key", System.getenv("OPENAI_API_KEY") != null ? System.getenv("OPENAI_API_KEY") : "demo-key",
            "--dataset", "general_qa",
            "--concurrent", "5",
            "--number", "20",
            "--temperature", "0.7",
            "--max-tokens", "1024"
        };

        try {
            // 直接创建评估配置并运行
            EvaluationReport report = runner.runEvaluation("openai_gpt35_evaluation");

            if (report.hasResults()) {
                System.out.println("OpenAI GPT-3.5评估完成:");
                System.out.println("- 模型数: " + report.getTotalModels());
                System.out.println("- 成功率: " +
                    report.getSummary().getOrDefault("overall_success_rate", "unknown"));
                System.out.println("- 平均延迟: " +
                    report.getSummary().getOrDefault("avg_latency_ms", "unknown") + "ms");
            } else {
                System.out.println("OpenAI评估没有产生结果（可能因为未设置有效的API key）");
            }
        } catch (Exception e) {
            System.err.println("OpenAI模型评估失败: " + e.getMessage());
        }
    }

    /**
     * 运行HuggingFace模型评估
     */
    private static void runHuggingFaceEvaluation(EvalScopeRunner runner) {
        System.out.println("\n=== 运行 HuggingFace DialoGPT-Medium 模型评估 ===");

        try {
            EvaluationReport report = runner.runEvaluation("hf_dialogpt_evaluation");

            if (report.hasResults()) {
                System.out.println("HuggingFace DialoGPT评估完成:");
                System.out.println("- 测试样本: " + report.getSummary().getOrDefault("total_samples", 0));
                System.out.println("- 平均得分: " +
                    report.getSummary().getOrDefault("avg_score", "N/A"));
            } else {
                System.out.println("HuggingFace评估没有产生结果");
            }
        } catch (Exception e) {
            System.err.println("HuggingFace模型评估失败: " + e.getMessage());
        }
    }

    /**
     * 运行本地模型评估
     */
    private static void runLocalEvaluation(EvalScopeRunner runner) {
        System.out.println("\n=== 运行本地Llama2-7B-Chat模型评估 ===");

        try {
            EvaluationReport report = runner.runEvaluation("local_llama_evaluation");

            if (report.hasResults()) {
                System.out.println("本地Llama2模型评估完成");
            } else {
                System.out.println("本地模型评估没有产生结果（可能的本地服务未启动）");
            }
        } catch (Exception e) {
            System.err.println("本地模型评估失败: " + e.getMessage());
        }
    }

    /**
     * 创建并执行批量模型性能评估
     */
    private static void runBatchEvaluation(EvalScopeRunner runner) {
        System.out.println("\n=== 运行批量模型性能评估 ===");

        try {
            EvaluationReport report = runner.runEvaluation("batch_comparison_eval");

            if (report.hasResults()) {
                System.out.println("批量对比评估完成:");
                System.out.println("对比模型:");
                System.out.println("- OpenAI GPT-3.5-Turbo");
                System.out.println("- HuggingFace DialoGPT-Medium");
                System.out.println("- 本地 Llama2-7B-Chat");
                System.out.println();
                System.out.println("评估维度:");
                System.out.println("- 响应速度 (tokens/second)");
                System.out.println("- 成功率 (%)");
                System.out.println("- 文本质量得分");
                System.out.println("- 内存使用情况");
                System.out.println("- 并发处理能力");
            }
        } catch (Exception e) {
            System.err.println("批量评估失败: " + e.getMessage());
        }
    }

    /**
     * 演示使用具体的模型实例而不是配置来运行评估
     * （更接近 https://github.com/modelscope/evalscope 的直接模型调用方式）
     */
    private static void directModelEvaluationDemo() {
        System.out.println("\n=== 直接模型实例评估演示 ===");

        try {
            // 直接创建OpenAI模型实例
            OpenAICompatibleModel openAIModel = new OpenAICompatibleModel("direct-gpt", "chat", "openai");
            openAIModel.setApiEndpoint("https://api.openai.com/v1");
            openAIModel.setModelName("gpt-3.5-turbo");
            openAIModel.setApiKey(System.getenv("OPENAI_API_KEY"));
            openAIModel.setConnectionTimeout(30);
            openAIModel.setReadTimeout(60);

            System.out.println("直接创建的OpenAI模型: " + openAIModel.getModelId());

            // 直接创建HuggingFace模型实例
            HuggingFaceModel hfModel = new HuggingFaceModel("direct-hf-model", "chat");
            hfModel.setModelName("microsoft/DialoGPT-medium");
            hfModel.setApiToken(System.getenv("HF_API_TOKEN"));
            hfModel.setReadTimeout(120);

            System.out.println("直接创建的HF模型: " + hfModel.getModelName());

        } catch (Exception e) {
            System.err.println("直接模型评估演示失败: " + e.getMessage());
        }
    }
}