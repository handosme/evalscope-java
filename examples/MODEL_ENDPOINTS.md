# 示例：测试真实API端点

## 场景1：OpenAI API测试配置

```yaml
evalscope:
  models:
    gpt-3.5-turbo-test:
      type: "chat"
      provider: "openai"
      enabled: false  # 设置为启用前配置你的OpenAI API密钥
      parameters:
        model_name: "gpt-3.5-turbo"
        max_tokens: 500
        temperature: 0.7
      credentials:
        api_key: "YOUR_OPENAI_API_KEY_HERE"

  evaluations:
    openai_quality_test:
      models: ["gpt-3.5-turbo-test"]
      evaluators: ["chat"]
      maxConcurrency: 1
      saveResults: true
      outputPath: "results/openai"
      parameters:
        max_examples: 50
        timeout_seconds: 30

  settings:
    result_format: "json"
    log_level: "INFO"
```

运行命令：
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" \
  -Dexec.args="openai_quality_test" \
  -Dexec.vmArguments="-DOPENAI_API_KEY=sk-XXX..."
```

---

## 场景2：HuggingFace Model API测试

```yaml
evalscope:
  models:
    hf-flan-t5-base:
      type: "chat"  # 文本生成模式
      provider: "custom"
      enabled: false  # 需要配置HF TOKEN
      parameters:
        endpoint: "https://api-inference.huggingface.co/models/google/flan-t5-base"
        max_tokens: 128
        temperature: 0.7
      credentials:
        api_key: "HF_ETHICS_TOKEN"  # HuggingFace Inference Token

specify-test:
  models: ["hf-flan-t5-base"]
  evaluators: ["chat"]
  maxConcurrency: 1
  saveResults: true
  outputPath: "results/huggingface"
  parameters:
    max_examples: 20
    timeout_seconds: 60
:
  settings:
    result_format: "json"
    max_job_concurrency: 2
```

运行命令：
```bash
export HF_TOKEN="hf_...xxx..."
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" \
  -Dexec.args="huggingface-quality"
```

---

## 场景3：本地私有模型测试

假设你在本地运行一个私有模型：

```yaml
evalscope:
  models:
    local-private-llm:
      type: "chat"
      provider: "custom"
      enabled: true
      parameters:
        model_name: "llama2-7b-chinese"
        endpoint: "http://localhost:8080/v1/completions"
        max_tokens: 1000
        temperature: 0.7
      credentials:
        api_key: "local-pass"

  evaluations:
    private_model_test:
      models: ["local-private-llm"]
      evaluators: ["chat"]
      maxConcurrency: 2
      saveResults: true
      outputPath: "results/local-chinese"
      parameters:
        max_examples: 30
        timeout_seconds: 40
        prompts_folder: "test-prompts/chinese"

  settings:
    result_format: "json"
    response_timeout_seconds: 45
    max_job_concurrency: 1
```

---

## 场景4：对比测试两个模型

```yaml
evalscope:
  models:
    production_openai:
      type: "chat"
      provider: "openai"
      enabled: true
      parameters:
        model_name: "gpt-3.5-turbo"
        max_tokens: 512
      credentials:
        api_key: "${OPENAI_API_KEY}"

    production_claude:
      type: "chat"
      provider: "anthropic"
      enabled: false
      parameters:
        model_name: "claude-instant-1"
        max_tokens: 1000
      credentials:
        api_key: "${CLADUE_API_KEY}"

  evaluations:
    ai_gateway_product_comparison:
      models: ["production_openai", "production_claude"]
      evaluators: ["chat", "performance"]
      maxConcurrency: 2
      saveResults: true
      outputPath: "results/ai-gateway"
      parameters:
        max_examples: 100
        timeout_seconds: 45
        compare_models: true
        eval_categories:
          - "customer-service"
          - "code-review"
          - "documentation"

  settings:
    result_format: "json"
    log_level: "INFO"
    export_comparative_report: true
```

---

## 配置提示

1. **API密钥安全**:
   ```bash
   export MY_API_KEY="sk-abc..."
   # 在YAML中使用 ${MY_API_KEY}
   ```

2. **测试频率控制**:
   避免持续高频调用API端点，避免触发限流

3. **多云多模型冗余**:
   可以通过配置多个禁用状态的备用来源API

4. **结果文件结构化存储**:
   outputPath: "results/{provider}/{model}/{timestamp}/"

通过这种方式, 你可以在YAML中定义真实API端点的配置, 对生产环境中的AI服务进行系统性的评估对比! 📈

------
*Note: 使用真实API时确保遵守各服务提供商的使用条款和费用政策*🔐