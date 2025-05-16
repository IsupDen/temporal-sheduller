package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ru.isupden.schedulingmodule.model.Task;

/**
 * Dependency-Aware Critical-Path Scheduling.
 * 1.  Задачу можно выбирать, только если ВСЕ её dependsOn уже dispatch-нуты.
 * 2.  Среди готовых побеждает та, у которой criticalLen больше (длиннее хвост).
 * 3.  Если обе ещё «не готовы» → считается, что они эквивалентны (compare==0).
 */
public class CriticalPathSchedulingStrategy implements SchedulingStrategy {

    /**
     * ID уже dispatch-нутых задач (не обязательно завершённых)
     */
    private final Set<String> dispatched = ConcurrentHashMap.newKeySet();

    /* ---------- core ---------- */

    @Override
    public boolean canCompare(Task a, Task b) {
        return true;
    }

    @Override
    public int compare(Task a, Task b) {
        var readyA = depsSatisfied(a);
        var readyB = depsSatisfied(b);

        if (readyA && !readyB) {
            return -1;       // A готова, B нет  → A «лучше»
        }
        if (!readyA && readyB) {
            return 1;       // наоборот
        }
        if (!readyA) {
            return 0;     // обе не готовы
        }

        /* обе готовы → смотрим длину критического пути */
        var ca = Optional.ofNullable(a.attr("criticalLen", Integer.class)).orElse(0);
        var cb = Optional.ofNullable(b.attr("criticalLen", Integer.class)).orElse(0);
        return Integer.compare(cb, ca);         // длиннее путь → раньше
    }

    /* --- helper: все ли зависимости dispatch-нуты? --- */
    private boolean depsSatisfied(Task t) {
        var deps = t.attr("dependsOn", List.class);
        return deps == null || dispatched.containsAll(deps);
    }

    /* ---------- lifecycle hooks ---------- */

    @Override
    public void onDispatch(Task t, Instant at) {
        dispatched.add(t.getWorkflowId());      // помечаем как «выданную»
    }
}
