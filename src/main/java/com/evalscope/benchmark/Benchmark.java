package com.evalscope.benchmark;

import com.evalscope.model.Model;
import java.util.Map;

public interface Benchmark {
    String getBenchmarkName();
    String getBenchmarkType();
    boolean supportsModel(Model model);

    BenchmarkResult run(Model model);
    BenchmarkResult run(Model model, Map<String, Object> parameters);
}