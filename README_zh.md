# EvalScope Java

[English](README.md) | [中文](README_zh.md)

EvalScope AI模型评估框架的Java实现。

## 功能特点

- **多模型支持**：评估不同类型的AI模型（聊天、嵌入等）
- **可插拔评估器**：具有不同评估策略的可扩展评估系统
- **性能基准测试**：内置性能测试和基准测试功能
- **配置管理**：使用HOCON格式的灵活配置系统
- **并发执行**：支持并行运行多个评估
- **全面报告**：包含指标和统计数据的详细评估报告
- **可扩展架构**：易于添加新模型、评估器和基准测试
- **Java 8兼容**：完全兼容Java 8运行环境

## 架构

```
com.evalscope/
├── model/              # 模型接口和实现
├── evaluator/          # 评估系统
├── benchmark/          # 基准测试工具
├── config/             # 配置管理
├── runner/             # 执行引擎
└── utils/              # 工具类
```

## 快速开始

### 构建和运行

```bash
# 构建项目
mvn clean compile

# 使用默认配置运行
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner"

# 运行特定评估
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" -Dexec.args="default_evaluation"
```

### 配置

在`src/main/resources/application.conf`中配置模型和评估：

```hocon
evalscope {
  models {
    my-model {
      type = "chat"
      provider = "openai"
      enabled = true
      parameters {
        model_name = "gpt-3.5-turbo"
        max_tokens = 1000
      }
      credentials {
        api_key = "${OPENAI_API_KEY}"
      }
    }
  }

  evaluations {
    my-evaluation {
      models = ["my-model"]
      evaluators = ["chat", "performance"]
      parameters {
        max_examples = 50
      }
    }
  }
}
```

## 使用示例

### 基本模型评估

```java
// 创建配置
ConfigManager configManager = ConfigManager.createDefault();

// 配置模型
ModelConfig modelConfig = new ModelConfig("gpt-3.5", "chat", "openai");
modelConfig.addParameter("api_key", "your-api-key");
configManager.addModelConfig(modelConfig);

// 配置评估
EvaluationConfig evalConfig = new EvaluationConfig("chat-test");
List<String> modelIds = new ArrayList<>();
modelIds.add("gpt-3.5");
evalConfig.setModelIds(modelIds);
List<String> evaluatorTypes = new ArrayList<>();
evaluatorTypes.add("chat");
evaluatorTypes.add("performance");
evalConfig.setEvaluatorTypes(evaluatorTypes);
configManager.addEvaluationConfig(evalConfig);

// 运行评估
EvalScopeRunner runner = new EvalScopeRunner(configManager);
EvaluationReport report = runner.runEvaluation("chat-test");
```

### 自定义模型实现

```java
public class MyCustomModel extends ChatModel {
    @Override
    public void load() throws Exception {
        // 加载您的模型
    }

    @Override
    public void unload() throws Exception {
        // 卸载您的模型
    }

    @Override
    public ModelResponse generate(String prompt, Map<String, Object> parameters) {
        // 使用您的模型生成响应
        ModelResponse response = new ModelResponse(getModelId(), "chat");
        response.setOutput("生成的响应");
        response.setSuccess(true);
        return response;
    }
}
```

### 自定义评估器

```java
public class MyCustomEvaluator implements Evaluator {
    @Override
    public String getEvaluatorName() {
        return "MyCustomEvaluator";
    }

    @Override
    public boolean supportsModel(Model model) {
        return model instanceof ChatModel;
    }

    @Override
    public EvaluationResult evaluate(Model model, EvaluationData data) {
        // 实现您的评估逻辑
        EvaluationResult result = new EvaluationResult(getEvaluatorName(), model.getModelId(), data.getTaskType());
        // ... 评估逻辑 ...
        return result;
    }
}
```

## 核心概念

### 模型
- **ChatModel**：用于对话AI模型
- **EmbeddingModel**：用于文本嵌入模型
- 扩展基础`Model`接口的自定义模型类型

### 评估器
- **ChatModelEvaluator**：评估聊天/对话模型
- **PerformanceBenchmark**：对模型性能进行基准测试
- 实现`Evaluator`接口的自定义评估器

### 基准测试
- **PerformanceBenchmark**：测量响应时间、吞吐量等
- 实现`Benchmark`接口的自定义基准测试

## 性能指标

在评估过程中，收集以下指标：

- 响应时间统计（最小值、最大值、平均值、中位数、P95、P99）
- 吞吐量（每秒请求数、每秒令牌数）
- 成功/失败率
- 相似度分数（用于文本生成任务）
- 基于评估器实现的自定义指标

## 测试

```bash
# 运行测试
mvn test

# 运行特定测试
mvn test -Dtest=EvalScopeTest
```

## 许可证

本项目采用MIT许可证 - 详情请参阅LICENSE文件。

## 贡献

1. Fork仓库
2. 创建您的功能分支（`git checkout -b feature/amazing-feature`）
3. 提交您的更改（`git commit -m 'Add some amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 打开Pull Request