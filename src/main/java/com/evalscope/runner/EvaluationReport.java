package com.evalscope.runner;

import com.evalscope.evaluator.EvaluationResult;
import com.evalscope.benchmark.BenchmarkResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class EvaluationReport {
    private String reportId;
    private LocalDateTime generatedAt;
    private String evaluationName;
    private List<EvaluationResult> evaluationResults;
    private List<BenchmarkResult> benchmarkResults;
    private Map<String, Object> summary;

    public EvaluationReport(String evaluationName) {
        this.reportId = generateReportId();
        this.generatedAt = LocalDateTime.now();
        this.evaluationName = evaluationName;
        this.evaluationResults = new ArrayList<>();
        this.benchmarkResults = new ArrayList<>();
        this.summary = new HashMap<>();
    }

    public void addEvaluationResult(EvaluationResult result) {
        evaluationResults.add(result);
        updateSummary();
    }

    public void addBenchmarkResult(BenchmarkResult result) {
        benchmarkResults.add(result);
        updateSummary();
    }

    private void updateSummary() {
        // Update evaluation summary
        if (!evaluationResults.isEmpty()) {
            Map<String, Object> evalSummary = new HashMap<>();
            evalSummary.put("total_models", evaluationResults.size());
            evalSummary.put("successful_evaluations",
                evaluationResults.stream().filter(EvaluationResult::isSuccess).count());
            evalSummary.put("failed_evaluations",
                evaluationResults.stream().filter(r -> !r.isSuccess()).count());

            double avgScore = evaluationResults.stream()
                .filter(EvaluationResult::isSuccess)
                .mapToDouble(EvaluationResult::getOverallScore)
                .average()
                .orElse(0.0);
            evalSummary.put("average_score", avgScore);

            summary.put("evaluation", evalSummary);
        }

        // Update benchmark summary
        if (!benchmarkResults.isEmpty()) {
            Map<String, Object> benchSummary = new HashMap<>();
            benchSummary.put("total_benchmarks", benchmarkResults.size());
            benchSummary.put("successful_benchmarks",
                benchmarkResults.stream().filter(BenchmarkResult::isSuccess).count());
            benchSummary.put("failed_benchmarks",
                benchmarkResults.stream().filter(r -> !r.isSuccess()).count());

            summary.put("benchmark", benchSummary);
        }
    }

    private String generateReportId() {
        return "report_" + System.currentTimeMillis();
    }

    public String getReportId() {
        return reportId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public String getEvaluationName() {
        return evaluationName;
    }

    public List<EvaluationResult> getEvaluationResults() {
        return new ArrayList<>(evaluationResults);
    }

    public List<BenchmarkResult> getBenchmarkResults() {
        return new ArrayList<>(benchmarkResults);
    }

    public Map<String, Object> getSummary() {
        return new HashMap<>(summary);
    }

    public boolean hasResults() {
        return !evaluationResults.isEmpty() || !benchmarkResults.isEmpty();
    }

    public int getTotalModels() {
        return evaluationResults.size();
    }

    public int getTotalBenchmarks() {
        return benchmarkResults.size();
    }
}