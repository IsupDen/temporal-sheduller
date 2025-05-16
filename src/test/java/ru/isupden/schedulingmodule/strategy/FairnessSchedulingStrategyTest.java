package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.isupden.schedulingmodule.model.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FairnessSchedulingStrategyTest {

    private static final long HALF_LIFE_SEC = 3600; // 1 hour
    private Map<String, Double> quotas;
    private FairnessSchedulingStrategy strategy;

    @BeforeEach
    void setUp() {
        quotas = new HashMap<>();
        quotas.put("tenant1", 1.0);
        quotas.put("tenant2", 0.5);
        quotas.put("tenant3", 0.1);

        strategy = new FairnessSchedulingStrategy(quotas, HALF_LIFE_SEC);
    }

    @Test
    void testCanCompare_WhenBothTasksHaveTenantId_ShouldReturnTrue() {
        var taskA = createTaskWithTenant("A", "tenant1");
        var taskB = createTaskWithTenant("B", "tenant2");

        assertTrue(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCanCompare_WhenFirstTaskHasNoTenantId_ShouldReturnFalse() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = createTaskWithTenant("B", "tenant2");

        assertFalse(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCanCompare_WhenSecondTaskHasNoTenantId_ShouldReturnFalse() {
        var taskA = createTaskWithTenant("A", "tenant1");
        var taskB = Task.builder().workflowId("B").build();

        assertFalse(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCompare_WhenNoUsageRecorded_ShouldReturnZero() {
        var taskA = createTaskWithTenant("A", "tenant1");
        var taskB = createTaskWithTenant("B", "tenant2");

        int result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    @Test
    void testCompare_ShouldPreferTenantWithLowerShare() {
        var taskA = createTaskWithTenant("A", "tenant1"); // quota 10.0
        var taskB = createTaskWithTenant("B", "tenant2"); // quota 5.0

        var now = Instant.now();
        strategy.recordUsage("tenant1", 5.0, now);
        strategy.recordUsage("tenant2", 5.0, now);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result < 0);
    }

    @Test
    void testCompare_ShouldPreferTenantWithLowerShare_EqualQuota() {
        var taskA = createTaskWithTenant("A", "tenant1");
        var taskB = createTaskWithTenant("B", "tenant3");

        quotas.put("tenant1", 0.1);
        quotas.put("tenant3", 0.1);

        var now = Instant.now();
        strategy.recordUsage("tenant1", 5.0, now);
        strategy.recordUsage("tenant3", 3.0, now);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result > 0);
    }

    @Test
    void testRecordUsage_ShouldAccumulateUsage() {
        var now = Instant.now();
        var tenantId = "tenant1";

        strategy.recordUsage(tenantId, 5.0, now);
        strategy.recordUsage(tenantId, 3.0, now);

        var taskA = createTaskWithTenant("A", tenantId);
        var taskB = createTaskWithTenant("B", "tenant3");

        var result = strategy.compare(taskA, taskB);
        assertTrue(result > 0);
    }

    @Test
    void testPreprocess_ShouldDecayUsage() {
        var start = Instant.now();
        var tenantId = "tenant1";

        strategy.recordUsage(tenantId, 100.0, start);

        var halfLifeLater = start.plus(HALF_LIFE_SEC, ChronoUnit.SECONDS);

        Queue<Task> queue = new LinkedList<>();

        strategy.preprocess(queue, halfLifeLater);

        var taskA = createTaskWithTenant("A", tenantId);
        var taskB = createTaskWithTenant("B", "tenant3");

        strategy.recordUsage("tenant3", 50.0, halfLifeLater);

        var result = strategy.compare(taskA, taskB);
        assertEquals(-1, result);
    }

    @Test
    void testCompare_WhenCannotCompare_ShouldReturnNegative() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = createTaskWithTenant("B", "tenant2");

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    private Task createTaskWithTenant(String id, String tenantId) {
        var task = Task.builder().workflowId(id).build();
        task.getAttributes().put("tenantId", tenantId);
        return task;
    }
}
