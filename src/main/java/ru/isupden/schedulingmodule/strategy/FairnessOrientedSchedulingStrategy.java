package ru.isupden.schedulingmodule.strategy;

import java.util.Comparator;
import java.util.Queue;

import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.model.FairnessOrientedTask;

/**
 * Picks the task with the lowest fairness score (longest waiting).
 */
@Component("fairness")
public class FairnessOrientedSchedulingStrategy implements SchedulingStrategy<FairnessOrientedTask> {
    @Override
    public FairnessOrientedTask nextTask(Queue<FairnessOrientedTask> readyQueue) {
        return readyQueue.stream()
                .min(Comparator.comparingDouble(FairnessOrientedTask::getFairnessScore))
                .orElse(null);
    }

    @Override
    public Class<FairnessOrientedTask> getTaskClass() {
        return FairnessOrientedTask.class;
    }
}
