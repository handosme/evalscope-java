package com.evalscope.evaluator;

import java.util.Map;

public class TestCase {
    private String id;
    private String input;
    private String expectedOutput;
    private String description;
    private Map<String, Object> metadata;

    public TestCase(String id, String input, String expectedOutput) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
    }

    public TestCase(String id, String input, String expectedOutput, String description) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.description = description;
    }

    public TestCase(String id, String input, String expectedOutput, String description, Map<String, Object> metadata) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.description = description;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}