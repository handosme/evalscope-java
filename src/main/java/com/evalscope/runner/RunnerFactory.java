package com.evalscope.runner;

import com.evalscope.model.Model;
import com.evalscope.model.ChatModel;
import com.evalscope.evaluator.Evaluator;
import com.evalscope.evaluator.ChatModelEvaluator;
import com.evalscope.benchmark.Benchmark;
import com.evalscope.benchmark.PerformanceBenchmark;

import java.util.Map;
import java.util.HashMap;

public class RunnerFactory {
    private static final Map<String, EvaluatorCreator> evaluatorCreators = new HashMap<>();
    private static final Map<String, BenchmarkCreator> benchmarkCreators = new HashMap<>();

    static {
        // Register default evaluators
        evaluatorCreators.put("chat", ChatModelEvaluator::new);
        evaluatorCreators.put("conversation", ChatModelEvaluator::new);

        // Register default benchmarks
        benchmarkCreators.put("performance", PerformanceBenchmark::new);
    }

    public static Evaluator createEvaluator(String type) {
        EvaluatorCreator creator = evaluatorCreators.get(type.toLowerCase());
        if (creator == null) {
            throw new IllegalArgumentException("Unknown evaluator type: " + type);
        }
        return creator.create();
    }

    public static Benchmark createBenchmark(String type) {
        BenchmarkCreator creator = benchmarkCreators.get(type.toLowerCase());
        if (creator == null) {
            throw new IllegalArgumentException("Unknown benchmark type: " + type);
        }
        return creator.create();
    }

    public static Evaluator createEvaluatorForModel(Model model) {
        if (model instanceof ChatModel) {
            return new ChatModelEvaluator();
        }

        throw new IllegalArgumentException("No suitable evaluator found for model type: " + model.getClass().getSimpleName());
    }

    public static Benchmark createBenchmarkForModel(Model model, String benchmarkType) {
        if (model instanceof ChatModel) {
            switch (benchmarkType.toLowerCase()) {
                case "performance":
                    return new PerformanceBenchmark();
                default:
                    return new PerformanceBenchmark(); // Default for chat models
            }
        }

        throw new IllegalArgumentException("No suitable benchmark found for model type: " + model.getClass().getSimpleName());
    }

    public static void registerEvaluator(String type, EvaluatorCreator creator) {
        evaluatorCreators.put(type.toLowerCase(), creator);
    }

    public static void registerBenchmark(String type, BenchmarkCreator creator) {
        benchmarkCreators.put(type.toLowerCase(), creator);
    }

    @FunctionalInterface
    public interface EvaluatorCreator {
        Evaluator create();
    }

    @FunctionalInterface
    public interface BenchmarkCreator {
        Benchmark create();
    }
}