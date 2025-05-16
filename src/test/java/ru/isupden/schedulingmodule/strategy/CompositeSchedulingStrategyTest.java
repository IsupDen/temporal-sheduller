package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.isupden.schedulingmodule.model.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeSchedulingStrategyTest {

    @Test
    void testCanCompare_ShouldAlwaysReturnTrue() {
        var strategy1 = Mockito.mock(SchedulingStrategy.class);
        var strategy2 = Mockito.mock(SchedulingStrategy.class);

        var strategy = new CompositeSchedulingStrategy(
                Arrays.asList(strategy1, strategy2));

        var taskA = Task.builder().workflowId("A").build();
        var taskB = Task.builder().workflowId("B").build();

        assertTrue(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCompare_ShouldReturnFirstNonZeroComparison() {
        var strategy1 = Mockito.mock(SchedulingStrategy.class);
        var strategy2 = Mockito.mock(SchedulingStrategy.class);
        var strategy3 = Mockito.mock(SchedulingStrategy.class);

        var taskA = Task.builder().workflowId("A").build();
        var taskB = Task.builder().workflowId("B").build();

        when(strategy1.canCompare(taskA, taskB)).thenReturn(true);
        when(strategy1.compare(taskA, taskB)).thenReturn(0);

        when(strategy2.canCompare(taskA, taskB)).thenReturn(true);
        when(strategy2.compare(taskA, taskB)).thenReturn(-1);

        when(strategy3.canCompare(taskA, taskB)).thenReturn(true);
        when(strategy3.compare(taskA, taskB)).thenReturn(1);

        var strategy = new CompositeSchedulingStrategy(
                Arrays.asList(strategy1, strategy2, strategy3));

        var result = strategy.compare(taskA, taskB);

        assertEquals(-1, result);
        verify(strategy3, never()).compare(taskA, taskB);
    }

    @Test
    void testCompare_WhenAllStrategiesReturnZero_ShouldReturnZero() {
        var strategy1 = Mockito.mock(SchedulingStrategy.class);
        var strategy2 = Mockito.mock(SchedulingStrategy.class);

        var taskA = Task.builder().workflowId("A").build();
        var taskB = Task.builder().workflowId("B").build();

        when(strategy1.canCompare(taskA, taskB)).thenReturn(true);
        when(strategy1.compare(taskA, taskB)).thenReturn(0);

        when(strategy2.canCompare(taskA, taskB)).thenReturn(true);
        when(strategy2.compare(taskA, taskB)).thenReturn(0);

        var strategy = new CompositeSchedulingStrategy(
                Arrays.asList(strategy1, strategy2));

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    @Test
    void testPreprocess_ShouldCallPreprocessOnAllStrategies() {
        var strategy1 = Mockito.mock(SchedulingStrategy.class);
        var strategy2 = Mockito.mock(SchedulingStrategy.class);

        var strategy = new CompositeSchedulingStrategy(
                Arrays.asList(strategy1, strategy2));

        Queue<Task> queue = new LinkedList<>();
        var now = Instant.now();

        strategy.preprocess(queue, now);

        verify(strategy1).preprocess(queue, now);
        verify(strategy2).preprocess(queue, now);
    }

    @Test
    void testOnDispatch_ShouldCallOnDispatchOnAllStrategies() {
        var strategy1 = Mockito.mock(SchedulingStrategy.class);
        var strategy2 = Mockito.mock(SchedulingStrategy.class);

        var strategy = new CompositeSchedulingStrategy(
                Arrays.asList(strategy1, strategy2));

        var task = Task.builder().workflowId("A").build();
        var now = Instant.now();

        strategy.onDispatch(task, now);

        verify(strategy1).onDispatch(task, now);
        verify(strategy2).onDispatch(task, now);
    }

    @Test
    void testOnShutdown_ShouldCallOnShutdownOnAllStrategies() {
        SchedulingStrategy strategy1 = Mockito.mock(SchedulingStrategy.class);
        SchedulingStrategy strategy2 = Mockito.mock(SchedulingStrategy.class);

        CompositeSchedulingStrategy strategy = new CompositeSchedulingStrategy(
                Arrays.asList(strategy1, strategy2));

        strategy.onShutdown();

        verify(strategy1).onShutdown();
        verify(strategy2).onShutdown();
    }

    @Test
    void testRecordUsage_ShouldCallRecordUsageOnlyOnUsageAwareStrategies() {
        SchedulingStrategy regularStrategy = Mockito.mock(SchedulingStrategy.class);
        UsageAwareStrategy usageAwareStrategy = Mockito.mock(UsageAwareStrategy.class);

        List<SchedulingStrategy> strategies = Arrays.asList(regularStrategy, usageAwareStrategy);
        CompositeSchedulingStrategy strategy = new CompositeSchedulingStrategy(strategies);

        String tenant = "tenant1";
        double cost = 10.0;
        Instant now = Instant.now();

        strategy.recordUsage(tenant, cost, now);

        verify(usageAwareStrategy).recordUsage(tenant, cost, now);
    }
}
