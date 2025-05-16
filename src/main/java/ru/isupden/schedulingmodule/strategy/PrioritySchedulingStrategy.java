package ru.isupden.schedulingmodule.strategy;

import ru.isupden.schedulingmodule.model.Task;

/**
 * Picks the task with highest priority value.
 */
public class PrioritySchedulingStrategy implements SchedulingStrategy {

    @Override
    public boolean canCompare(Task a, Task b) {
        return a.attr("priority", Integer.class) != null
                && b.attr("priority", Integer.class) != null;
    }

    @Override
    public int compare(Task a, Task b) {
        if (!canCompare(a, b)) {
            return 0;
        }

        var pa = a.attr("priority", Integer.class);
        var pb = b.attr("priority", Integer.class);

        // Более высокий приоритет (большее число) должен идти раньше
        return Integer.compare(pb, pa);
    }
}
