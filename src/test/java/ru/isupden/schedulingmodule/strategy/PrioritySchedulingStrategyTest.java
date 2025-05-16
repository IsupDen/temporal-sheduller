package ru.isupden.schedulingmodule.strategy;

import org.junit.jupiter.api.Test;
import ru.isupden.schedulingmodule.model.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrioritySchedulingStrategyTest {

    private final PrioritySchedulingStrategy strategy = new PrioritySchedulingStrategy();

    @Test
    void testCanCompare_WhenBothTasksHavePriority_ShouldReturnTrue() {
        var taskA = createTaskWithPriority(5);
        var taskB = createTaskWithPriority(10);

        assertTrue(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCanCompare_WhenFirstTaskHasNoPriority_ShouldReturnFalse() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = createTaskWithPriority(10);

        assertFalse(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCanCompare_WhenSecondTaskHasNoPriority_ShouldReturnFalse() {
        var taskA = createTaskWithPriority(5);
        var taskB = Task.builder().workflowId("B").build();

        assertFalse(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCompare_WhenFirstTaskHasHigherPriority_ShouldReturnNegative() {
        var taskA = createTaskWithPriority(10);
        var taskB = createTaskWithPriority(5);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result < 0);
    }

    @Test
    void testCompare_WhenSecondTaskHasHigherPriority_ShouldReturnPositive() {
        var taskA = createTaskWithPriority(5);
        var taskB = createTaskWithPriority(10);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result > 0);
    }

    @Test
    void testCompare_WhenBothTasksHaveEqualPriority_ShouldReturnZero() {
        var taskA = createTaskWithPriority(5);
        var taskB = createTaskWithPriority(5);

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    @Test
    void testCompare_WhenCannotCompare_ShouldReturnNegative() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = createTaskWithPriority(10);

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    private Task createTaskWithPriority(int priority) {
        var task = Task.builder().workflowId("task-" + priority).build();
        task.getAttributes().put("priority", priority);
        return task;
    }
}
