# EvalScope Java - YAML配置启动指南

## 启动阶段使用application.yaml配置

EvalScope Java现在全面支持YAML格式的配置文件，在启动时会自动优先加载`application.yaml`，如果找不到则回退到传统的`application.conf`。

### 自动加载功能

1. **查找顺序**：启动时会按照以下顺序查找配置文件：
   - 优先尝试加载 `src/main/resources/application.yaml`
   - 如果找不到，回退到加载 `src/main/resources/application.conf`

2. **确认加载**：控制台会输出实际使用的配置文件：
   ```
   Loaded YAML model configuration: mock-chat-model
   Loaded YAML model configuration: gpt-3.5-turbo
   Loaded YAML evaluation configuration: default_evaluation
   Loaded configuration from application.yaml
   ```

### YAML配置示例

```yaml
evalscope:
  # Model configurations
  models:
    mock-chat-model:
      type: "chat"
      provider: "mock"
      enabled: false
      parameters:
        endpoint: "mock://localhost:8080"
        max_tokens: 100
        temperature: 0.7

    my-gpt-model:
      type: "chat"
      provider: "custom"
      enabled: true
      parameters:
        endpoint: "http://localhost:8080/v1/completions"
        model_name: "gpt-3.5-turbo"
        max_tokens: 1000
        temperature: 0.7
      credentials:
        api_key: "${API_KEY}"

  # Evaluation configurations
  evaluations:
    my-evaluation:
      models: ["my-gpt-model"]
      evaluators: ["chat", "performance"]
      maxConcurrency: 1
      saveResults: true
      outputPath: "results/my-eval"
      parameters:
        max_examples: 50
        timeout_seconds: 30
        warmup_iterations: 5
        test_iterations: 100

  # Global settings
  settings:
    max_job_concurrency: 5
    response_timeout_seconds: 30
    result_format: "json"
    log_level: "INFO"
```

## 启动方法

### 方法1：使用默认配置（自动查找YAML）
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner"
```

### 方法2：指定特定评估执行
```bash
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" -Dexec.args="my-evaluation"
```

### 方法3：使用JAR文件运行
```bash
java -cp target/evalscope-java-1.0.0.jar com.evalscope.EvalScopeRunner
java -cp target/evalscope-java-1.0.0.jar com.evalscope.EvalScopeRunner my-evaluation
```

## 验证配置加载

启动时会看到以下信息确认YAML配置被正确加载：
```
=== EvalScope Java ===
AI Model Evaluation Framework

Loaded YAML model configuration: mock-chat-model
Loaded YAML model configuration: gpt-3.5-turbo
.
.
Loaded configuration from application.yaml
Running with default configuration...
```

## 配置优先级规则

1. **文件存在时**：如果有`application.yaml`，一定会优先使用YAML格式
2. **回退机制**：如果没有YAML文件，会自动使用`application.conf`
3. **错误处理**：如果YAML格式错误，会显示错误信息并回退到`.conf`文件
4. **兼容性**：完全支持原有的HOCON/Java Properties格式配置

## 集群化部署建议

### Docker环境变量方式：
```dockerfile
FROM openjdk:8-jre-slim
COPY target/evalscope-java-1.0.0.jar /app/app.jar
COPY application.yaml /app/resources/
WORKDIR /app
CMD ["java", "-jar", "app.jar"]
```

### K8s ConfigMap方式：
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: evalscope-config
data:
  application.yaml: |
    evalscope:
      models:
        production-gpt:
          enabled: true
          # ...其他配置
---
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      volumes:
      - name: config
        configMap:
          name: evalscope-config
      containers:
      - name: evalscope
        volumeMounts:
        - name: config
          mountPath: /app/resources
```

这样，EvalScope Java就会在启动时自动加载YAML配置并开始相应的模型评估工作。