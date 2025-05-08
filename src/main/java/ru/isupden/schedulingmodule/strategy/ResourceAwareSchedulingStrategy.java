package ru.isupden.schedulingmodule.strategy;

import java.util.Comparator;
import java.util.Queue;

import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.model.ResourceAwareTask;

/**
 * Picks the task that requires the fewest resources.
 */
@Component("resourceAware")
public class ResourceAwareSchedulingStrategy implements SchedulingStrategy<ResourceAwareTask> {
    @Override
    public ResourceAwareTask nextTask(Queue<ResourceAwareTask> readyQueue) {
        return readyQueue.stream()
                .min(Comparator.comparingInt(t ->
                        t.getRequiredResources().values().stream().mapToInt(Integer::intValue).sum()))
                .orElse(null);
    }

    @Override
    public Class<ResourceAwareTask> getTaskClass() {
        return ResourceAwareTask.class;
    }
}
