package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import ru.isupden.schedulingmodule.model.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineSchedulingStrategyTest {

    private final DeadlineSchedulingStrategy strategy = new DeadlineSchedulingStrategy();

    @Test
    void testCanCompare_WhenBothTasksHaveDeadline_ShouldReturnTrue() {
        var taskA = createTaskWithDeadline(Instant.now());
        var taskB = createTaskWithDeadline(Instant.now().plus(1, ChronoUnit.HOURS));

        assertTrue(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCanCompare_WhenFirstTaskHasNoDeadline_ShouldReturnFalse() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = createTaskWithDeadline(Instant.now());

        assertFalse(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCanCompare_WhenSecondTaskHasNoDeadline_ShouldReturnFalse() {
        var taskA = createTaskWithDeadline(Instant.now());
        var taskB = Task.builder().workflowId("B").build();

        assertFalse(strategy.canCompare(taskA, taskB));
    }

    @Test
    void testCompare_WhenFirstTaskHasEarlierDeadline_ShouldReturnNegative() {
        var earlier = Instant.now();
        var later = earlier.plus(1, ChronoUnit.HOURS);
        var taskA = createTaskWithDeadline(earlier);
        var taskB = createTaskWithDeadline(later);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result < 0);
    }

    @Test
    void testCompare_WhenSecondTaskHasEarlierDeadline_ShouldReturnPositive() {
        var earlier = Instant.now();
        var later = earlier.plus(1, ChronoUnit.HOURS);
        var taskA = createTaskWithDeadline(later);
        var taskB = createTaskWithDeadline(earlier);

        var result = strategy.compare(taskA, taskB);

        assertTrue(result > 0);
    }

    @Test
    void testCompare_WhenBothTasksHaveSameDeadline_ShouldReturnZero() {
        var deadline = Instant.now();
        var taskA = createTaskWithDeadline(deadline);
        var taskB = createTaskWithDeadline(deadline);

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    @Test
    void testPreprocess_ShouldRemoveExpiredTasks() {
        var now = Instant.now();
        var past = now.minus(1, ChronoUnit.HOURS);
        var future = now.plus(1, ChronoUnit.HOURS);

        var expiredTask = createTaskWithDeadline(past);
        var validTask = createTaskWithDeadline(future);
        var noDeadlineTask = Task.builder().workflowId("noDeadline").build();

        Queue<Task> queue = new LinkedList<>();
        queue.add(expiredTask);
        queue.add(validTask);
        queue.add(noDeadlineTask);

        strategy.preprocess(queue, now);

        assertEquals(2, queue.size());
        assertFalse(queue.contains(expiredTask));
        assertTrue(queue.contains(validTask));
        assertTrue(queue.contains(noDeadlineTask));
    }

    @Test
    void testPreprocess_WithStringDeadline_ShouldHandleCorrectly() {
        var now = Instant.now();
        var past = now.minus(1, ChronoUnit.HOURS);
        var future = now.plus(1, ChronoUnit.HOURS);

        var expiredTask = createTaskWithStringDeadline(past.toString());
        var validTask = createTaskWithStringDeadline(future.toString());
        var invalidTask = createTaskWithStringDeadline("not-a-date");

        Queue<Task> queue = new LinkedList<>();
        queue.add(expiredTask);
        queue.add(validTask);
        queue.add(invalidTask);

        strategy.preprocess(queue, now);

        assertEquals(2, queue.size());
        assertFalse(queue.contains(expiredTask));
        assertTrue(queue.contains(validTask));
        assertTrue(queue.contains(invalidTask));
    }

    @Test
    void testCompare_WhenCannotCompare_ShouldReturnNegative() {
        var taskA = Task.builder().workflowId("A").build();
        var taskB = createTaskWithDeadline(Instant.now());

        var result = strategy.compare(taskA, taskB);

        assertEquals(0, result);
    }

    private Task createTaskWithDeadline(Instant deadline) {
        var task = Task.builder().workflowId("task-" + deadline.toEpochMilli()).build();
        task.getAttributes().put("deadline", deadline);
        return task;
    }

    private Task createTaskWithStringDeadline(String deadline) {
        var task = Task.builder().workflowId("task-string-" + deadline).build();
        task.getAttributes().put("deadline", deadline);
        return task;
    }
}
