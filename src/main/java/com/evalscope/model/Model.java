package com.evalscope.model;

import java.util.Map;

/**
 * AI模型基础接口
 * 定义了所有AI模型必须实现的核心功能，包括模型信息获取、加载/卸载状态和任务支持检测
 */
public interface Model {
    /**
     * 获取模型唯一标识符
     * @return 模型ID字符串
     */
    String getModelId();

    /**
     * 获取模型类型（如：chat、embedding等）
     * @return 模型类型字符串
     */
    String getModelType();

    /**
     * 获取模型详细配置信息
     * @return 包含模型配置信息的Map
     */
    Map<String, Object> getModelInfo();

    /**
     * 检查模型是否已加载
     * @return true表示模型已加载，false表示未加载
     */
    boolean isLoaded();

    /**
     * 检查模型是否支持指定类型的任务
     * @param taskType 任务类型字符串
     * @return true表示支持该任务类型，false表示不支持
     */
    boolean supportsTask(String taskType);

    /**
     * 加载模型到内存中
     * @throws Exception 加载失败时抛出异常
     */
    void load() throws Exception;

    /**
     * 从内存中卸载模型，释放资源
     * @throws Exception 卸载失败时抛出异常
     */
    void unload() throws Exception;
}