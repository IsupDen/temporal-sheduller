package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import ru.isupden.schedulingmodule.model.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalPathSchedulingStrategyTest {

    private final CriticalPathSchedulingStrategy strategy = new CriticalPathSchedulingStrategy();

    @Test
    void testCanCompare_ShouldAlwaysReturnTrue() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = Task.builder().workflowId("B").build();

        assertTrue(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCompare_WhenFirstTaskAllDependenciesDispatched_ShouldPreferFirst() {
        var taskA = createTaskWithDependencies("A", Arrays.asList("dep1", "dep2"));
        var taskB = createTaskWithDependencies("B", Arrays.asList("dep1", "dep3"));

        strategy.onDispatch(createTask("dep1"), Instant.now());
        strategy.onDispatch(createTask("dep2"), Instant.now());

        var result = strategy.compare(taskA, taskB);

        assertTrue(result < 0);
    }

    @Test
    void testCompare_WhenSecondTaskAllDependenciesDispatched_ShouldPreferSecond() {
        var taskA = createTaskWithDependencies("A", Arrays.asList("dep1", "dep2"));
        var taskB = createTaskWithDependencies("B", Arrays.asList("dep1", "dep3"));

        strategy.onDispatch(createTask("dep1"), Instant.now());
        strategy.onDispatch(createTask("dep3"), Instant.now());

        var result = strategy.compare(taskA, taskB);

        assertTrue(result > 0);
    }

    @Test
    void testCompare_WhenBothTasksAllDependenciesDispatched_ShouldPreferLongerCriticalPath() {
        var taskA = createTaskWithDependencies("A", Arrays.asList("dep1", "dep2"));
        taskA.getAttributes().put("criticalLen", 5);

        var taskB = createTaskWithDependencies("B", Arrays.asList("dep1", "dep3"));
        taskB.getAttributes().put("criticalLen", 10);

        strategy.onDispatch(createTask("dep1"), Instant.now());
        strategy.onDispatch(createTask("dep2"), Instant.now());
        strategy.onDispatch(createTask("dep3"), Instant.now());

        var result = strategy.compare(taskA, taskB);

        assertTrue(result > 0);
    }

    @Test
    void testCompare_WhenNeitherTaskDependenciesSatisfied_ShouldReturnZero() {
        var taskA = createTaskWithDependencies("A", Arrays.asList("dep1", "dep2"));
        var taskB = createTaskWithDependencies("B", Arrays.asList("dep3", "dep4"));

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    @Test
    void testCompare_WhenTasksHaveNoDependencies_ShouldCompareByCriticalPathLength() {
        var taskA = createTask("A");
        taskA.getAttributes().put("criticalLen", 5);

        var taskB = createTask("B");
        taskB.getAttributes().put("criticalLen", 3);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result < 0);
    }

    @Test
    void testCompare_WhenOneTaskHasNoCriticalLength_ShouldAssumeZero() {
        var taskA = createTask("A");
        taskA.getAttributes().put("criticalLen", 5);

        var taskB = createTask("B");

        var result = strategy.compare(taskA, taskB);

        assertTrue(result < 0);
    }

    private Task createTask(String id) {
        return Task.builder().workflowId(id).build();
    }

    private Task createTaskWithDependencies(String id, List<String> dependencies) {
        var task = Task.builder().workflowId(id).build();
        task.getAttributes().put("dependsOn", dependencies);
        return task;
    }
}
