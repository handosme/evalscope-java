package com.evalscope.data;

import com.evalscope.config.DatasetConfig;
import com.evalscope.evaluator.TestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.IOException;

public class DataLoaderFactory {
    private static final Map<String, DataLoader> LOADERS = new HashMap<>();

    static {
        // Register default loaders
        registerDataLoader(new LineByLineDatasetLoader());
    }

    public static void registerDataLoader(DataLoader loader) {
        LOADERS.put(loader.getLoaderName(), loader);

        // Also register alternative names for the same loader
        if (loader.getLoaderName().equals("line_by_line")) {
            LOADERS.put("line_by_line", loader);
            LOADERS.put("txt", loader);  // .txt files can be line by line
            LOADERS.put("text", loader); // .text files can be line by line
        }
    }

    public static DataLoader getDataLoader(String loaderName) {
        return LOADERS.get(loaderName);
    }

    public static DataLoader findDataLoader(DatasetConfig datasetConfig) {
        String specifiedLoader = (String) datasetConfig.getParameters().get("dataset");

        if (specifiedLoader != null) {
            DataLoader loader = LOADERS.get(specifiedLoader);
            if (loader != null && loader.supportsFormat(datasetConfig.getFormat())) {
                return loader;
            }
        }

        // Find compatible loader by format and type
        for (DataLoader loader : LOADERS.values()) {
            if (loader.supportsFormat(datasetConfig.getFormat()) &&
                loader.supportsDatasetType(datasetConfig.getDatasetType())) {
                return loader;
            }
        }

        return null;
    }

    public static List<TestCase> loadDataset(DatasetConfig datasetConfig) throws IOException {
        DataLoader loader = findDataLoader(datasetConfig);
        if (loader == null) {
            throw new IOException("No compatible data loader found for dataset format: " +
                                datasetConfig.getFormat() + " and type: " + datasetConfig.getDatasetType());
        }

        return loader.loadDataset(datasetConfig);
    }

    public static List<String> getAvailableLoaders() {
        return new ArrayList<>(LOADERS.keySet());
    }

    public static boolean supportsLoader(String loaderName) {
        return LOADERS.containsKey(loaderName);
    }
}