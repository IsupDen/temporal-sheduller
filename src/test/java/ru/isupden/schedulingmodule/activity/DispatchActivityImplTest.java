package ru.isupden.schedulingmodule.activity;

import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.Timer;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.isupden.schedulingmodule.metrics.SchedulingMetricsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchActivityImplTest {

    private WorkflowClient workflowClient;
    private DispatchActivityImpl dispatchActivity;
    private WorkflowStub workflowStub;
    private SchedulingMetricsService metricsService;
    private Timer timer;

    @BeforeEach
    void setUp() {
        workflowClient = mock(WorkflowClient.class);
        workflowStub = mock(WorkflowStub.class);
        metricsService = mock(SchedulingMetricsService.class);
        timer = mock(Timer.class);
        when(metricsService.getTaskExecutionTimer(anyString())).thenReturn(timer);
        dispatchActivity = new DispatchActivityImpl(workflowClient, metricsService);
    }

    @Test
    void testDispatchTask() {
        // Setup test data
        var workflowType = "TestWorkflow";
        var workflowId = "test-wf-id";
        var payload = new HashMap<String, Object>();
        payload.put("data", "testData");
        payload.put("tenantId", "testTenant");
        var taskQueue = "test-task-queue";

        // Setup mocks
        when(workflowClient.newUntypedWorkflowStub(anyString(), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);

        // Execute method under test
        dispatchActivity.dispatchTask(workflowType, workflowId, payload, taskQueue);

        // Capture WorkflowOptions
        var optionsCaptor = ArgumentCaptor.forClass(WorkflowOptions.class);

        // Verify WorkflowClient.newUntypedWorkflowStub was called with correct parameters
        verify(workflowClient, times(1)).newUntypedWorkflowStub(
                eq(workflowType),
                optionsCaptor.capture());

        // Verify start was called with payload
        verify(workflowStub, times(1)).start(eq(payload));

        // Verify WorkflowOptions
        var options = optionsCaptor.getValue();
        assertEquals(workflowId, options.getWorkflowId());
        assertEquals(taskQueue, options.getTaskQueue());

        // Verify memo contains tenantId
        Map<String, ?> memo = options.getMemo();
        assertEquals("testTenant", memo.get("tenantId"));
    }

    @Test
    void testDispatchTaskWithDefaultTenant() {
        // Setup test data with no tenantId in payload
        var workflowType = "TestWorkflow";
        var workflowId = "test-wf-id";
        var payload = new HashMap<String, Object>();
        payload.put("data", "testData");
        // No tenantId
        var taskQueue = "test-task-queue";

        // Setup mocks
        when(workflowClient.newUntypedWorkflowStub(anyString(), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);

        // Execute method under test
        dispatchActivity.dispatchTask(workflowType, workflowId, payload, taskQueue);

        // Capture WorkflowOptions
        var optionsCaptor = ArgumentCaptor.forClass(WorkflowOptions.class);

        // Verify WorkflowClient.newUntypedWorkflowStub was called
        verify(workflowClient, times(1)).newUntypedWorkflowStub(anyString(), optionsCaptor.capture());

        // Verify memo contains default tenant
        var options = optionsCaptor.getValue();
        Map<String, ?> memo = options.getMemo();
        assertEquals("default", memo.get("tenantId"));
    }

    // Helper method to access private WorkflowOptions fields via reflection
    private String getWorkflowId(WorkflowOptions options) {
        try {
            var field = WorkflowOptions.class.getDeclaredField("workflowId");
            field.setAccessible(true);
            return (String) field.get(options);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access workflowId", e);
        }
    }

    private String getTaskQueue(WorkflowOptions options) {
        try {
            var field = WorkflowOptions.class.getDeclaredField("taskQueue");
            field.setAccessible(true);
            return (String) field.get(options);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access taskQueue", e);
        }
    }

    private Map<String, ?> getMemo(WorkflowOptions options) {
        try {
            var field = WorkflowOptions.class.getDeclaredField("memo");
            field.setAccessible(true);
            return (Map<String, ?>) field.get(options);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access memo", e);
        }
    }

    private void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
