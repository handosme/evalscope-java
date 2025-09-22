package com.evalscope.evaluator;

import com.evalscope.model.Model;
import com.evalscope.model.ModelResponse;
import java.util.Map;

/**
 * AI模型评估器接口
 * 定义了评估AI模型性能和质量的标准方法，支持不同类型的评估策略
 * 所有具体的评估器类都应实现此接口
 */
public interface Evaluator {
    /**
     * 获取评估器名称
     * @return 评估器名称字符串，用于标识特定的评估方法
     */
    String getEvaluatorName();

    /**
     * 获取评估类型
     * @return 评估类型字符串，如："chat"、"performance"等
     */
    String getEvaluationType();

    /**
     * 检查该评估器是否支持指定模型
     * @param model 待评估的模型对象
     * @return true表示支持该模型，false表示不支持
     */
    boolean supportsModel(Model model);

    /**
     * 执行模型评估（基础方法）
     * @param model 待评估的模型
     * @param data 评估数据，包含测试用例
     * @return 评估结果对象，包含得分、测试结果和统计信息
     */
    EvaluationResult evaluate(Model model, EvaluationData data);

    /**
     * 执行模型评估（带参数）
     * @param model 待评估的模型
     * @param data 评估数据，包含测试用例
     * @param parameters 评估参数，如阈值、迭代次数等
     * @return 评估结果对象，包含得分、测试结果和统计信息
     */
    EvaluationResult evaluate(Model model, EvaluationData data, Map<String, Object> parameters);
}