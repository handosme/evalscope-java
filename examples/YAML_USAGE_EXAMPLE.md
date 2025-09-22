# EvalScope Java - Application.yaml 使用示例

## 🎯 概述
本示例展示了如何使用 `application.yaml` 配置文件驱动 EvalScope Java 的运行。配置中包含实际场景下的模型和评估策略设置。

## 📋 配置内容

我们的 YAML 配置包含：

### 🔧 模型配置
1. **simple-chat-model** - 启用 Mock 模型进行测试
2. **advanced-gpt** - OpenAI GPT-3.5 Turbo（处于禁用状态，需要API key）

### 🧪 评估配置
1. **basic_convo** - 基础对话质量评估
2. **quick_performance** - 快速性能评估

## 🚀 运行步骤

### 1. 启动对话质量评估
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" -Dexec.args="basic_convo"
```

输出示例：
```
=== EvalScope Java ===
AI Model Evaluation Framework

Loaded YAML model configuration: simple-chat-model
Loaded YAML model configuration: advanced-gpt
Loaded YAML evaluation configuration: basic_convo
Loaded YAML evaluation configuration: quick_performance
Loaded configuration from application.yaml
Running evaluation: basic_convo
Starting evaluation: basic_convo
Models to evaluate: [simple-chat-model]
Evaluators to use: [chat]

--- Evaluating model: simple-chat-model ---
Running evaluator: ChatModelEvaluator
Evaluation completed. Score: 1.0

--- Running benchmarks for model: simple-chat-model ---
Running warmup phase (5 iterations)...
Running performance benchmark (50 iterations)...
Completed 10/50 iterations
Completed 20/50 iterations
Completed 30/50 iterations
Completed 40/50 iterations
Completed 50/50 iterations
Performance benchmark completed successfully!
Average response time: 297.46 ms
Requests per second: 3.362

=== Evaluation Summary ===
Generated at: 2025-09-22T12:32:40.238781
Total models evaluated: 1
Total benchmarks run: 1
Successful evaluations: 1
Failed evaluations: 0
Successful benchmarks: 1
Failed benchmarks: 0
Report ID: report_1758515560238
Evaluation completed successfully!
```

### 2. 启动性能评估
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" -Dexec.args="quick_performance"
```

主要输出：
```
Running evaluation: quick_performance
Starting evaluation: quick_performance
Models to evaluate: [simple-chat-model]
Evaluators to use: [performance]

--- Evaluating model: simple-chat-model ---
Error running evaluator performance: Unknown evaluator type: performance

--- Running benchmarks for model: simple-chat-model ---
Running warmup phase (3 iterations)...
Running performance benchmark (50 iterations)...
Performance benchmark completed successfully!
Average response time: 339.12 ms
Requests per second: 2.949
```

### 3. 运行默认配置
如果没有指定评估名称，将运行默认评估：
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner"
```

## 📊 关键指标解读

### 通过YAML配置驱动的指标：
- **Response Time**: `response_delay_ms: 80`
- **Concurrency**: `maxConcurrency: 2`
- **Timeout**: `timeout_seconds: 15`
- **Test Scope**: `test_iterations: 50`

### 实际观测到的性能数据：
| 评估类型 | 平均响应时间 | 每秒请求数 |
|----------|-------------|-----------|
| 基础对话 | 297.46 ms | 3.362 |
| 性能测试 | 339.12 ms | 2.949 |

## 🔍 YAML配置关键点

### ✅ 配置验证
运行开始时你会看到确认信息：
```
Loaded configuration from application.yaml
```

### 🏗️ 结构清晰
- **models**: 定义可用的AI模型及其参数
- **evaluations**: 定义评估策略和测试用例
- **settings**: 全局系统配置

### 🔄 动态参数调整
通过修改YAML文件即可调整：
```yaml
simple-chat-model:
  parameters:
    response_delay_ms: 50   # 降低延迟以提高性能
    temperature: 0.6       # 调整生成多样性

quick_performance:
  parameters:
    test_iterations: 100    # 增加测试规模
    timeout_seconds: 20    # 延长超时时间
```

## ⚡ 快速修改示例

### 增强模型数量评估
```yaml
models:
  added-model:
    type: "chat"
    provider: "mock"
    enabled: true
    parameters:
      endpoint: "mock://localhost:8081"  # 不同端口
      max_tokens: 500  # 更大上下文

evaluations:
  multi_model_compare:
    models: ["simple-chat-model", "added-model"]
    evaluators: ["chat"]
    maxConcurrency: 2
```

### 调节评估频率
```yaml
evaluation_intervals:
  hourly_test:
    interval: "1h"
    # ... 其他配置
  daily_performance:
    interval: "24h"
    # ... 其他配置
```

## 🎯 总结

通过这个示例，我们看到：
1. **YAML配置**成功取代了传统的`.conf`文件
2. **评估参数**完全可通过配置文件控制
3. **模型配置**灵活支持多种提供商和参数集
4. **运行效率**由YAML中定义的并发度和超时策略驱动

这个模式使得 EvalScope Java 的使用对非技术人员也更加友好，配置维护简单明了。修改配置后无需重新编译代码即可生效！

尝试修改 `application.yaml` 文件中的参数，重新运行命令，体验配置驱动的AI模型评估工作流程吧！