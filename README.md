# EvalScope Java

Java implementation of the EvalScope AI model evaluation framework.

## Features

- **Multi-Model Support**: Evaluate different types of AI models (Chat, Embedding, etc.)
- **Pluggable Evaluators**: Extensible evaluation system with different evaluation strategies
- **Performance Benchmarking**: Built-in performance testing and benchmarking
- **Configuration Management**: Flexible configuration system with HOCON format
- **Concurrent Execution**: Support for running multiple evaluations in parallel
- **Comprehensive Reporting**: Detailed evaluation reports with metrics and statistics
- **Extensible Architecture**: Easy to add new models, evaluators, and benchmarks

## Architecture

```
com.evalscope/
├── model/              # Model interfaces and implementations
├── evaluator/          # Evaluation system
├── benchmark/          # Benchmark utilities
├── config/             # Configuration management
├── runner/             # Execution engine
└── utils/              # Utility classes
```

## Quick Start

### Build and Run

```bash
# Build the project
mvn clean compile

# Run with default configuration
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner"

# Run specific evaluation
mvn exec:java -Dexec.mainClass="com.evalscope.EvalScopeRunner" -Dexec.args="default_evaluation"
```

### Configuration

Configure models and evaluations in `src/main/resources/application.conf`:

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

## Usage Examples

### Basic Model Evaluation

```java
// Create configuration
ConfigManager configManager = ConfigManager.createDefault();

// Configure model
ModelConfig modelConfig = new ModelConfig("gpt-3.5", "chat", "openai");
modelConfig.addParameter("api_key", "your-api-key");
configManager.addModelConfig(modelConfig);

// Configure evaluation
EvaluationConfig evalConfig = new EvaluationConfig("chat-test");
evalConfig.setModelIds(Arrays.asList("gpt-3.5"));
evalConfig.setEvaluatorTypes(Arrays.asList("chat", "performance"));
configManager.addEvaluationConfig(evalConfig);

// Run evaluation
EvalScopeRunner runner = new EvalScopeRunner(configManager);
EvaluationReport report = runner.runEvaluation("chat-test");
```

### Custom Model Implementation

```java
public class MyCustomModel extends ChatModel {
    @Override
    public void load() throws Exception {
        // Load your model
    }

    @Override
    public void unload() throws Exception {
        // Unload your model
    }

    @Override
    public ModelResponse generate(String prompt, Map<String, Object> parameters) {
        // Generate response using your model
        ModelResponse response = new ModelResponse(getModelId(), "chat");
        response.setOutput("Generated response");
        response.setSuccess(true);
        return response;
    }
}
```

### Custom Evaluator

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
        // Implement your evaluation logic
        EvaluationResult result = new EvaluationResult(getEvaluatorName(), model.getModelId(), data.getTaskType());
        // ... evaluation logic ...
        return result;
    }
}
```

## Key Concepts

### Models
- **ChatModel**: For conversational AI models
- **EmbeddingModel**: For text embedding models
- Custom model types extending the base `Model` interface

### Evaluators
- **ChatModelEvaluator**: Evaluates chat/conversational models
- **PerformanceBenchmark**: Benchmarks model performance
- Custom evaluators implementing the `Evaluator` interface

### Benchmarks
- **PerformanceBenchmark**: Measures response time, throughput, etc.
- Custom benchmarks implementing the `Benchmark` interface

## Performance Metrics

During evaluation, the following metrics are collected:

- Response time statistics (min, max, average, median, P95, P99)
- Throughput (requests per second, tokens per second)
- Success/failure rates
- Similarity scores (for text generation tasks)
- Custom metrics based on evaluator implementation

## Testing

```bash
# Run tests
mvn test

# Run specific test
mvn test -Dtest=EvalScopeTest
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request