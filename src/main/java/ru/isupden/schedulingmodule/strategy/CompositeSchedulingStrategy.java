package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Queue;

import ru.isupden.schedulingmodule.model.Task;

public class CompositeSchedulingStrategy
        implements SchedulingStrategy, UsageAwareStrategy {

    private final List<SchedulingStrategy> chain;

    public CompositeSchedulingStrategy(List<SchedulingStrategy> chain) {
        this.chain = chain;
    }

    /* ---- compare ---- */
    @Override
    public boolean canCompare(Task a, Task b) {
        return true;
    }

    @Override
    public int compare(Task a, Task b) {
        for (var s : chain) {
            if (!s.canCompare(a, b)) {
                continue;
            }
            int c = s.compare(a, b);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    /* ---- delegation ---- */
    @Override
    public void preprocess(Queue<Task> q, Instant now) {
        chain.forEach(s -> s.preprocess(q, now));
    }

    @Override
    public void onDispatch(Task t, Instant at) {
        chain.forEach(s -> s.onDispatch(t, at));
    }

    @Override
    public void onShutdown() {
        chain.forEach(SchedulingStrategy::onShutdown);
    }

    /* ---- UsageAware ---- */
    @Override
    public void recordUsage(String tenant, double cost, Instant at) {
        chain.stream()
                .filter(c -> c instanceof UsageAwareStrategy)
                .map(UsageAwareStrategy.class::cast)
                .forEach(c -> c.recordUsage(tenant, cost, at));
    }
}
