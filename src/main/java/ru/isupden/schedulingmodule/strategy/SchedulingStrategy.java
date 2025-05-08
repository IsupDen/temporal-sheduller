package ru.isupden.schedulingmodule.strategy;

import java.util.Queue;

import ru.isupden.schedulingmodule.model.Task;

/**
 * Plugin interface for scheduling algorithms.
 */
public interface SchedulingStrategy<T extends Task> {
    /**
     * Selects the next task from the ready queue.
     * @param readyQueue all available tasks
     * @return next Task to dispatch, or null if none
     */
    T nextTask(Queue<T> readyQueue);

    Class<T> getTaskClass();
}