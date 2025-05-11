package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.model.Task;

/**
 * Dependency-Aware Critical-Path Scheduling.
 * 1.  Задачу можно выбирать, только если ВСЕ её dependsOn уже dispatch-нуты.
 * 2.  Среди готовых побеждает та, у которой criticalLen больше (длиннее хвост).
 * 3.  Если обе ещё «не готовы» → считается, что они эквивалентны (compare==0).
 */
@Component("critical")
public class CriticalPathSchedulingStrategy implements SchedulingStrategy {

    /** ID уже dispatch-нутых задач (не обязательно завершённых) */
    private final Set<String> dispatched = ConcurrentHashMap.newKeySet();

    /* ---------- core ---------- */

    @Override
    public boolean canCompare(Task a, Task b) { return true; }   // сами разберёмся

    @Override
    public int compare(Task a, Task b) {
        boolean readyA = depsSatisfied(a), readyB = depsSatisfied(b);

        if (readyA && !readyB) return -1;       // A готова, B нет  → A «лучше»
        if (!readyA && readyB) return  1;       // наоборот
        if (!readyA)             return  0;     // обе не готовы

        /* обе готовы → смотрим длину критического пути */
        int ca = Optional.ofNullable(a.attr("criticalLen", Integer.class)).orElse(0);
        int cb = Optional.ofNullable(b.attr("criticalLen", Integer.class)).orElse(0);
        return Integer.compare(cb, ca);         // длиннее путь → раньше
    }

    /* --- helper: все ли зависимости dispatch-нуты? --- */
    @SuppressWarnings("unchecked")
    private boolean depsSatisfied(Task t) {
        List<String> deps = t.attr("dependsOn", List.class);
        return deps == null || dispatched.containsAll(deps);
    }

    /* ---------- lifecycle hooks ---------- */

    @Override
    public void onDispatch(Task t, Instant at) {
        dispatched.add(t.getWorkflowId());      // помечаем как «выданную»
    }
}
