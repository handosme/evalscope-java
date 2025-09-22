# EvalScope Java - 使用指南

## 项目概览

这是一个基于Java 8的完整实现版本的 EvalScope AI 模型评测框架，支持：

- 🎯 多模型类型支持（Chat、Embedding等）
- 📊 多种评估方法和指标
- ⚡ 性能基准测试
- 🛠 可配置化系统
- 🚀 并发执行
- 📈 详细的报告生成

## 快速开始

### 1. 构建项目
```bash
mvn clean compile
```

### 2. 运行默认评测
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner"
```

### 3. 运行指定评测
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" -Dexec.args="default_evaluation"
```

## 核心组件

### 1. 模型层 (Model Layer)
```
com.evalscope.model/
├── Model.java              # 基础模型接口
├── ModelResponse.java      # 模型响应包装
├── ChatModel.java          # 对话模型基类
└── EmbeddingModel.java     # 嵌入模型基类
```

### 2. 评估系统 (Evaluation System)
```
com.evalscope.evaluator/
├── Evaluator.java          # 评估器接口
├── EvaluationData.java     # 评估数据
├── TestCase.java          # 测试用例
├── TestResult.java        # 测试结果
├── EvaluationResult.java  # 评估结果
└── ChatModelEvaluator.java # 对话模型评估器
```

### 3. 基准测试 (Benchmark)
```
com.evalscope.benchmark/
├── Benchmark.java              # 基准测试接口
├── BenchmarkResult.java       # 测试结果
└── PerformanceBenchmark.java  # 性能基准测试
```

### 4. 配置管理 (Configuration)
```
com.evalscope.config/
├── ConfigManager.java      # 配置管理器
├── ModelConfig.java       # 模型配置
└── EvaluationConfig.java  # 评估配置
```

## 使用示例

### 基本用法

```java
// 创建评测器
EvalScopeRunner runner = new EvalScopeRunner();

// 运行默认评测
runner.runEvaluation();

// 关闭资源
runner.shutdown();
```

### 自定义配置

```java
// 创建配置管理器
ConfigManager configManager = ConfigManager.createDefault();

// 配置模型
ModelConfig modelConfig = new ModelConfig("my-model", "chat", "custom");
modelConfig.addParameter("endpoint", "http://localhost:8080");
configManager.addModelConfig(modelConfig);

// 配置评测
EvaluationConfig evalConfig = new EvaluationConfig("my-evaluation");
List<String> modelIds = new ArrayList<>();
modelIds.add("my-model");
evalConfig.setModelIds(modelIds);
List<String> evaluatorTypes = new ArrayList<>();
evaluatorTypes.add("chat");
evaluatorTypes.add("performance");
evalConfig.setEvaluatorTypes(evaluatorTypes);
configManager.addEvaluationConfig(evalConfig);

// 运行评测
EvalScopeRunner runner = new EvalScopeRunner(configManager);
EvaluationReport report = runner.runEvaluation("my-evaluation");
```

### 创建自定义模型

```java
public class MyCustomModel extends ChatModel {
    @Override
    public void load() throws Exception {
        // 加载模型逻辑
    }

    @Override
    public void unload() throws Exception {
        // 卸载模型逻辑
    }

    @Override
    public ModelResponse generate(String prompt, Map<String, Object> parameters) {
        ModelResponse response = new ModelResponse(getModelId(), "chat");

        // 调用你的AI模型API
        // ...

        response.setOutput("模型响应");
        response.setSuccess(true);
        return response;
    }
}
```

## 评测指标

### 性能指标
- **响应时间**: 最小值、最大值、平均值、中位数、P95、P99
- **吞吐量**: 每秒请求数、每秒token数
- **成功率**: 成功/失败请求统计
- **并发性能**: 支持多模型并行评测

### 质量指标
- **相似度得分**: 基于编辑距离和文本相似度
- **通过/失败率**: 测试用例通过率
- **置信度评分**: 模型响应质量评估

## 配置文件

EvalScope 支持两种配置格式，配置文件位于 `src/main/resources/`:

### YAML 格式 (推荐)
编辑 `src/main/resources/application.yaml`:

```yaml
evalscope:
  models:
    your-model:
      type: "chat"
      provider: "custom"
      enabled: true
      parameters:
        endpoint: "http://localhost:8080"
        max_tokens: 1000
      credentials:
        api_key: "${API_KEY}"

  evaluations:
    your-evaluation:
      models: ["your-model"]
      evaluators: ["chat", "performance"]
      parameters:
        max_examples: 100
        warmup_iterations: 10
        test_iterations: 200
```

### HOCON 格式 (传统)
编辑 `src/main/resources/application.conf`:

```hocon
evalscope {
  models {
    your-model {
      type = "chat"
      provider = "custom"
      enabled = true
      parameters {
        endpoint = "http://localhost:8080"
        max_tokens = 1000
      }
      credentials {
        api_key = "${API_KEY}"
      }
    }
  }

  evaluations {
    your-evaluation {
      models = ["your-model"]
      evaluators = ["chat", "performance"]
      parameters {
        max_examples = 100
        warmup_iterations = 10
        test_iterations = 200
      }
    }
  }
}
```

注意: 如果同时存在 application.yaml 和 application.conf，application.yaml 将优先被使用。

## Java 8 兼容性说明

本项目完全基于Java 8开发，确保了在Java 8环境中的兼容性。特别注意以下几点：

1. **集合操作**: 使用传统ArrayList和显式添加元素，而非Java 9+的集合工厂方法
2. **流API使用**: 仅使用Java 8中提供的Stream API功能
3. **依赖版本**: 所有依赖库版本均选择与Java 8兼容的版本
4. **并发工具**: 使用Java 8中的并发工具和ExecutorService

示例代码均已按Java 8标准编写，无需更高版本Java特性即可运行。

## 扩展功能

### 添加新的评估器
```java
public class MyEvaluator implements Evaluator {
    @Override
    public String getEvaluatorName() {
        return "MyEvaluator";
    }

    @Override
    public boolean supportsModel(Model model) {
        return model instanceof ChatModel;
    }

    // 实现评估逻辑...
}
```

### 添加新的基准测试
```java
public class MyBenchmark implements Benchmark {
    @Override
    public String getBenchmarkName() {
        return "MyBenchmark";
    }

    @Override
    public boolean supportsModel(Model model) {
        return true; // 支持所有模型类型
    }

    // 实现基准测试逻辑...
}
```

## 测试结果

运行测试后，你会看到类似输出：

```
=== EvalScope Java ===
AI Model Evaluation Framework

Running with default configuration...

--- Evaluating model: mock-chat-model ---
Running evaluator: ChatModelEvaluator
Evaluation completed. Score: 1.0

--- Running benchmarks for model: mock-chat-model ---
Average response time: 314.82 ms
Requests per second: 3.18
Performance benchmark completed.

=== Evaluation Summary ===
Generated at: 2024-xx-xx
Total models evaluated: 1
Successful evaluations: 1
Successful benchmarks: 1
Report ID: report_xxx
Evaluation completed successfully!
```

## 开发说明

### 架构特点
- **接口驱动设计**: 核心功能通过接口定义，易于扩展
- **配置优先**: 支持灵活的YAML和HOCON配置文件
- **并发安全**: 支持多模型并行评测
- **日志记录**: 完整的日志系统支持
- **错误处理**: 全面的异常处理机制

### 性能考量
- 支持连接池配置
- 并发度可配置
- 内存使用优化
- 资源自动清理

## 项目结构

```
evalscope-java/
├── src/
│   ├── main/java/com/evalscope/
│   │   ├── model/
│   │   ├── evaluator/
│   │   ├── benchmark/
│   │   ├── config/
│   │   ├── runner/
│   │   ├── example/
│   │   └── utils/
│   ├── main/resources/
│   │   ├── application.yaml  # 推荐使用YAML配置格式
│   │   ├── application.conf.bak  # HOCON格式备份
│   │   └── logback.xml
│   └── test/java/
└── pom.xml
```

这个基于Java 8的EvalScope提供了完整的AI模型评测功能，可以作为生产环境的基础框架进行扩展，同时保证了在Java 8环境中的完全兼容性。