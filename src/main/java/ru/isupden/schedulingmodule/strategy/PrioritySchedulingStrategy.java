package ru.isupden.schedulingmodule.strategy;

import java.util.Comparator;
import java.util.Queue;

import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.model.PriorityTask;

/**
 * Picks the task with highest priority value.
 */
@Component("priority")
public class PrioritySchedulingStrategy implements SchedulingStrategy<PriorityTask> {
    @Override
    public Class<PriorityTask> getTaskClass() {
        return PriorityTask.class;
    }

    @Override
    public PriorityTask nextTask(Queue<PriorityTask> readyQueue) {
        return readyQueue.stream()
                .max(Comparator.comparingInt(PriorityTask::getPriority))
                .orElse(null);
    }
}
