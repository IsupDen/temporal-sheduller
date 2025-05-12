package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.Queue;

import ru.isupden.schedulingmodule.model.Task;

/**
 * Plugin interface for scheduling algorithms.
 */
public interface SchedulingStrategy {

    /**
     * Может ли критерий сравнить пару тасков?
     * Например, у одного нет priority — возвращаем false и
     * Composite пропустит этот критерий.
     */
    boolean canCompare(Task a, Task b);

    /**
     * Сравнение.
     * Должно вызываться ТОЛЬКО если canCompare == true для этой пары.
     * < 0 → a «лучше», > 0 → b лучше, 0 → равны.
     */
    int compare(Task a, Task b);

    /**
     * Предобработка очереди (удалить expired и т. п.). По умолчанию — NOP.
     */
    default void preprocess(Queue<Task> queue, Instant now) {
    }

    /**
     * Хук после успешного dispatch (для Fairness).
     */
    default void onDispatch(Task task, Instant at) {
    }

    default void onShutdown() {
    }
}