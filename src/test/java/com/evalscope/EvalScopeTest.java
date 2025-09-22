package com.evalscope;

import com.evalscope.config.ConfigManager;
import com.evalscope.config.EvaluationConfig;
import com.evalscope.config.ModelConfig;
import com.evalscope.runner.EvaluationReport;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class EvalScopeTest {

    private EvalScopeRunner runner;
    private ConfigManager configManager;

    @Before
    public void setUp() {
        configManager = ConfigManager.createDefault();
        runner = new EvalScopeRunner(configManager);
    }

    @After
    public void tearDown() {
        runner.shutdown();
    }

    @Test
    public void testDefaultConfiguration() {
        assertEquals(configManager, runner.getConfigManager());
        assertNotNull(runner.getEvaluationRunner());
    }

    @Test
    public void testModelConfiguration() {
        ModelConfig modelConfig = new ModelConfig("test-model", "chat", "test");
        modelConfig.addParameter("endpoint", "http://localhost:8080");
        modelConfig.setEnabled(true);

        configManager.addModelConfig(modelConfig);
        ModelConfig retrievedConfig = configManager.getModelConfig("test-model");

        assertNotNull(retrievedConfig);
        assertEquals("test-model", retrievedConfig.getModelId());
        assertEquals("chat", retrievedConfig.getModelType());
        assertEquals("test", retrievedConfig.getProvider());
        assertTrue(retrievedConfig.isEnabled());
        assertEquals("http://localhost:8080", retrievedConfig.getParameters().get("endpoint"));
    }

    @Test
    public void testEvaluationConfiguration() {
        EvaluationConfig evalConfig = new EvaluationConfig("test-evaluation");
        evalConfig.setModelIds(Arrays.asList("test-model"));
        evalConfig.setEvaluatorTypes(Arrays.asList("chat"));
        evalConfig.addParameter("max_examples", 10);

        configManager.addEvaluationConfig(evalConfig);
        EvaluationConfig retrievedConfig = configManager.getEvaluationConfig("test-evaluation");

        assertNotNull(retrievedConfig);
        assertEquals("test-evaluation", retrievedConfig.getEvaluationName());
        assertEquals(1, retrievedConfig.getModelIds().size());
        assertEquals("test-model", retrievedConfig.getModelIds().get(0));
        assertEquals(1, retrievedConfig.getEvaluatorTypes().size());
        assertEquals("chat", retrievedConfig.getEvaluatorTypes().get(0));
        assertEquals(10, retrievedConfig.getParameters().get("max_examples"));
    }

    @Test
    public void testDefaultEvaluation() {
        // Test that default evaluation can be created and has configuration
        try {
            runner.runEvaluation("nonexistent");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Evaluation config not found: nonexistent", e.getMessage());
        }
    }

    @Test
    public void testReportGeneration() {
        // Create and configure evaluation
        EvaluationConfig evalConfig = new EvaluationConfig("test-report-evaluation");
        ModelConfig modelConfig = new ModelConfig("test-report-model", "chat", "mock");
        modelConfig.setEnabled(true);

        configManager.addModelConfig(modelConfig);
        evalConfig.setModelIds(Arrays.asList("test-report-model"));
        evalConfig.setEvaluatorTypes(Arrays.asList("chat"));
        evalConfig.addParameter("max_examples", 5);

        configManager.addEvaluationConfig(evalConfig);

        // Run evaluation
        EvaluationReport report = runner.runEvaluation("test-report-evaluation");

        assertNotNull(report);
        assertEquals("test-report-evaluation", report.getEvaluationName());
        assertTrue(report.hasResults());
        assertTrue(report.getTotalModels() >= 0);
        assertNotNull(report.getGeneratedAt());
        assertNotNull(report.getReportId());
    }

    @Test
    public void testMultipleModels() {
        // Create multiple model configurations
        ModelConfig model1 = new ModelConfig("model-1", "chat", "mock");
        ModelConfig model2 = new ModelConfig("model-2", "chat", "mock");

        configManager.addModelConfig(model1);
        configManager.addModelConfig(model2);

        // Create evaluation with multiple models
        EvaluationConfig evalConfig = new EvaluationConfig("multi-model-evaluation");
        evalConfig.setModelIds(Arrays.asList("model-1", "model-2"));
        evalConfig.setEvaluatorTypes(Arrays.asList("chat"));
        evalConfig.addParameter("max_examples", 3);

        configManager.addEvaluationConfig(evalConfig);

        EvaluationReport report = runner.runEvaluation("multi-model-evaluation");

        assertNotNull(report);
        assertEquals(2, report.getTotalModels());
    }

    @Test
    public void testDisabledModel() {
        ModelConfig disabledModel = new ModelConfig("disabled-model", "chat", "mock");
        disabledModel.setEnabled(false);
        configManager.addModelConfig(disabledModel);

        EvaluationConfig evalConfig = new EvaluationConfig("disabled-model-evaluation");
        evalConfig.setModelIds(Arrays.asList("disabled-model"));
        evalConfig.setEvaluatorTypes(Arrays.asList("chat"));

        configManager.addEvaluationConfig(evalConfig);

        // Should not throw error, but should skip disabled models
        try {
            runner.runEvaluation("disabled-model-evaluation");
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testShutdown() {
        // Test shutdown does not throw exception
        try {
            runner.shutdown();
        } catch (Exception e) {
            fail("Shutdown should not throw exception: " + e.getMessage());
        }

        // Test multiple shutdown calls are safe
        try {
            runner.shutdown();
        } catch (Exception e) {
            fail("Multiple shutdown calls should not throw exception: " + e.getMessage());
        }
    }

    /**
     * Helper method to check that code does not throw an exception
     */
    private void assertDoesNotThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            fail("Expected no exception but got: " + e.getMessage());
        }
    }
}