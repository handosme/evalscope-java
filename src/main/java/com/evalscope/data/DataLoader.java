package com.evalscope.data;

import com.evalscope.config.DatasetConfig;
import com.evalscope.evaluator.TestCase;
import java.util.List;
import java.io.IOException;

public interface DataLoader {
    /**
     * 加载数据集
     * @param datasetConfig 数据集配置
     * @return 测试用例列表
     * @throws IOException 当文件读取失败时
     */
    List<TestCase> loadDataset(DatasetConfig datasetConfig) throws IOException;

    /**
     * 获取加载器名称
     */
    String getLoaderName();

    /**
     * 检查是否支持指定的数据集格式
     */
    boolean supportsFormat(String format);

    /**
     * 检查是否支持指定的数据集类型
     */
    boolean supportsDatasetType(String datasetType);
}