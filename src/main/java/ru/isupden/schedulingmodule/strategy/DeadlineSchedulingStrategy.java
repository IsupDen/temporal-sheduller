package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Queue;

import ru.isupden.schedulingmodule.model.Task;

/**
 * Picks the task with the earliest deadline.
 */
public class DeadlineSchedulingStrategy implements SchedulingStrategy {

    @Override
    public boolean canCompare(Task a, Task b) {
        return deadlineOf(a) != null && deadlineOf(b) != null;
    }

    @Override
    public int compare(Task a, Task b) {
        if (!canCompare(a, b)) {
            return 0;
        }
        return deadlineOf(a).compareTo(deadlineOf(b));   // раньше = «лучше»
    }

    /* ---------- helper & preprocess ---------- */
    private Instant deadlineOf(Task t) {
        Object raw = t.getAttributes().get("deadline");
        if (raw instanceof Instant i) {
            return i;
        }
        if (raw instanceof String s) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException ignore) {
            }
        }
        return null;
    }

    @Override
    public void preprocess(Queue<Task> queue, Instant now) {
        queue.removeIf(t -> {
            var dl = deadlineOf(t);
            return dl != null && dl.isBefore(now);
        });
    }
}
