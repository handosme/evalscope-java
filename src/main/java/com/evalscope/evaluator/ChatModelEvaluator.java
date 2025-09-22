package com.evalscope.evaluator;

import com.evalscope.model.ChatModel;
import com.evalscope.model.Model;
import com.evalscope.model.ModelResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 对话模型评估器
 * 专门用于评估对话类AI模型（如ChatGPT、Claude等）的性能和质量
 *
 * 功能特点：
 * - 基于文本相似度的质量评估
 * - 支持自定义相似度阈值（默认70%）
 * - 提供详细的测试反馈和改进建议
 * - 自动计算处理时间等性能指标
 *
 * 评估方法：
 * 1. 文本相似度计算：使用编辑距离算法计算生成文本与期望文本的相似度
 * 2. 质量分级：根据相似度得分分为优秀(>80%)、良好(>60%)、一般(>40%)、较差(≤40%)
 * 3. 性能统计：自动收集响应时间和成功率等关键指标
 *
 * 适用场景：
 * - 聊天机器人质量评估
 * - 文本生成模型测试
 * - 对话系统性能基准测试
 */
public class ChatModelEvaluator implements Evaluator {
    private static final String EVALUATOR_NAME = "ChatModelEvaluator";
    private static final String EVALUATION_TYPE = "conversation";

    @Override
    public String getEvaluatorName() {
        return EVALUATOR_NAME;
    }

    @Override
    public String getEvaluationType() {
        return EVALUATION_TYPE;
    }

    @Override
    public boolean supportsModel(Model model) {
        return model instanceof ChatModel;
    }

    @Override
    public EvaluationResult evaluate(Model model, EvaluationData data) {
        return evaluate(model, data, null);
    }

    @Override
    public EvaluationResult evaluate(Model model, EvaluationData data, Map<String, Object> parameters) {
        if (!supportsModel(model)) {
            throw new IllegalArgumentException("Model type not supported by this evaluator");
        }

        ChatModel chatModel = (ChatModel) model;
        EvaluationResult result = new EvaluationResult(EVALUATOR_NAME, model.getModelId(), data.getTaskType());

        try {
            if (!chatModel.isLoaded()) {
                chatModel.load();
            }

            List<TestResult> testResults = new ArrayList<>();

            for (TestCase testCase : data.getTestCases()) {
                TestResult testResult = evaluateSingleTest(chatModel, testCase);
                testResults.add(testResult);
            }

            result.setTestResults(testResults);
            result.setMetrics(generateMetrics(testResults));
            result.setEndTime(LocalDateTime.now());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Evaluation failed: " + e.getMessage());
        }

        return result;
    }

    private TestResult evaluateSingleTest(ChatModel model, TestCase testCase) {
        TestResult testResult = new TestResult(
                testCase.getId(),
                testCase.getInput(),
                testCase.getExpectedOutput()
        );

        try {
            long startTime = System.currentTimeMillis();

            ModelResponse response = model.generate(testCase.getInput());

            long processingTime = System.currentTimeMillis() - startTime;
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("processing_time_ms", processingTime);
            testResult.setMetrics(metrics);

            if (response.isSuccess() && response.getOutput() != null) {
                String actualOutput = response.getOutput();
                testResult.setActualOutput(actualOutput);

                double similarity = calculateSimilarity(testCase.getExpectedOutput(), actualOutput);
                testResult.setScore(similarity);
                testResult.setPassed(similarity >= 0.7); // 70% threshold

                String feedback = generateFeedback(testCase.getExpectedOutput(), actualOutput, similarity);
                testResult.setFeedback(feedback);
            } else {
                testResult.setScore(0.0);
                testResult.setPassed(false);
                testResult.setErrorMessage("Model generation failed: " + response.getErrorMessage());
            }

        } catch (Exception e) {
            testResult.setScore(0.0);
            testResult.setPassed(false);
            testResult.setErrorMessage("Test evaluation failed: " + e.getMessage());
        }

        return testResult;
    }

    private double calculateSimilarity(String expected, String actual) {
        if (expected == null || actual == null) {
            return 0.0;
        }

        // Simple similarity calculation based on Levenshtein distance
        int distance = calculateLevenshteinDistance(expected.toLowerCase(), actual.toLowerCase());
        int maxLength = Math.max(expected.length(), actual.length());

        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - (double) distance / maxLength;
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private String generateFeedback(String expected, String actual, double similarity) {
        if (similarity >= 0.8) {
            return "Excellent match (similarity: " + String.format("%.2f", similarity) + ")";
        } else if (similarity >= 0.6) {
            return "Good match (similarity: " + String.format("%.2f", similarity) + ")";
        } else if (similarity >= 0.4) {
            return "Fair match (similarity: " + String.format("%.2f", similarity) + ")";
        } else {
            return "Poor match (similarity: " + String.format("%.2f", similarity) + ") - significant differences detected";
        }
    }

    private Map<String, Object> generateMetrics(List<TestResult> testResults) {
        if (testResults.isEmpty()) {
            return new HashMap<>();
        }

        long passedCount = testResults.stream().filter(TestResult::isPassed).count();
        double passRate = (double) passedCount / testResults.size();
        double avgScore = testResults.stream().mapToDouble(TestResult::getScore).average().orElse(0.0);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total_tests", testResults.size());
        metrics.put("passed_tests", passedCount);
        metrics.put("failed_tests", testResults.size() - passedCount);
        metrics.put("pass_rate", passRate);
        metrics.put("average_score", avgScore);
        return metrics;
    }
}