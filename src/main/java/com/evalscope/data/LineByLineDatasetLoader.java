package com.evalscope.data;

import com.evalscope.config.DatasetConfig;
import com.evalscope.evaluator.TestCase;
import com.evalscope.utils.JsonUtils;

import java.io.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineByLineDatasetLoader implements DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(LineByLineDatasetLoader.class);
    private static final String LOADER_NAME = "line_by_line";

    @Override
    public List<TestCase> loadDataset(DatasetConfig datasetConfig) throws IOException {
        if (!supportsFormat(datasetConfig.getFormat())) {
            throw new IOException("Unsupported format: " + datasetConfig.getFormat());
        }

        String filePath = datasetConfig.getPath();
        if (filePath == null || filePath.isEmpty()) {
            throw new IOException("Dataset path cannot be null or empty");
        }

        logger.info("Loading line-by-line dataset from: {}", filePath);

        // Try to find the file in different locations
        File datasetFile = findDatasetFile(filePath);
        if (datasetFile == null || !datasetFile.exists()) {
            throw new FileNotFoundException("Dataset file not found: " + filePath);
        }

        return loadLineByLine(datasetFile, datasetConfig);
    }

    private File findDatasetFile(String path) {
        // Try absolute path first
        File file = new File(path);
        if (file.exists()) {
            return file;
        }

        // Try relative path
        file = new File("." + File.separator + path);
        if (file.exists()) {
            return file;
        }

        // Try resources directory
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is != null) {
                // Create a temporary file from the resource stream
                is.close();
                throw new UnsupportedOperationException("Loading from resources stream not implemented yet");
            }
        } catch (IOException e) {
            System.err.println("Error accessing resource: " + path);
        }

        return null;
    }

    private List<TestCase> loadLineByLine(File file, DatasetConfig datasetConfig) throws IOException {
        List<TestCase> testCases = new ArrayList<>();
        Map<String, Object> parameters = datasetConfig.getParameters();

        // Get configuration parameters
        boolean shuffle = (boolean) parameters.getOrDefault("shuffle", false);
        int limit = (int) parameters.getOrDefault("limit", Integer.MAX_VALUE);
        int skipLines = (int) parameters.getOrDefault("skip_lines", 0);
        String linePrefix = (String) parameters.getOrDefault("line_prefix", "");

        logger.info("Loading dataset with params - shuffle: {}, limit: {}, skip_lines: {}", 
            shuffle, limit, skipLines);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            int lineOffset = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip empty lines and comments
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Skip specified number of lines
                if (lineOffset++ < skipLines) {
                    continue;
                }

                // Add line prefix if specified
                if (!linePrefix.isEmpty() && !line.startsWith(linePrefix)) {
                    line = linePrefix + line;
                }

                // Add metadata from parameters
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("line_number", lineNumber);
                metadata.put("filename", file.getName());

                // Create test case using the line as prompt with metadata
                TestCase testCase = new TestCase(
                    "line_" + lineNumber,  // test case id
                    line,                   // input prompt
                    line,                   // expected output (for consistency)
                    "Line " + lineNumber + " from " + file.getName(), // description
                    metadata                // metadata
                );

                testCases.add(testCase);

                if (testCases.size() >= limit) {
                    break;
                }
            }
        }

        // Apply shuffle if requested
        if (shuffle) {
            Collections.shuffle(testCases);
        }

        logger.info("Loaded {} test cases from dataset: {}", testCases.size(), file.getName());
        return testCases;
    }

    @Override
    public String getLoaderName() {
        return LOADER_NAME;
    }

    @Override
    public boolean supportsFormat(String format) {
        return "txt".equalsIgnoreCase(format) ||
               "text".equalsIgnoreCase(format) ||
               "json".equalsIgnoreCase(format) ||
               "line_by_line".equalsIgnoreCase(format);
    }

    @Override
    public boolean supportsDatasetType(String datasetType) {
        return "line_by_line".equalsIgnoreCase(datasetType) ||
               "text".equalsIgnoreCase(datasetType) ||
               "conversation".equalsIgnoreCase(datasetType) ||
               "performance".equalsIgnoreCase(datasetType);
    }
}