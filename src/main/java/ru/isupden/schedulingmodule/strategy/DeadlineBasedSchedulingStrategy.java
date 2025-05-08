package ru.isupden.schedulingmodule.strategy;

import java.util.Comparator;
import java.util.Queue;

import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.model.DeadlineBasedTask;

/**
 * Picks the task with the earliest deadline.
 */
@Component("deadline")
public class DeadlineBasedSchedulingStrategy implements SchedulingStrategy<DeadlineBasedTask> {
    @Override
    public DeadlineBasedTask nextTask(Queue<DeadlineBasedTask> readyQueue) {
        return readyQueue.stream()
                .min(Comparator.comparing(DeadlineBasedTask::getDeadline))
                .orElse(null);
    }

    @Override
    public Class<DeadlineBasedTask> getTaskClass() {
        return DeadlineBasedTask.class;
    }
}
