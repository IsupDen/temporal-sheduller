package ru.isupden.schedulingmodule.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.config.SchedulingModuleProperties;
import ru.isupden.schedulingmodule.model.Task;
import ru.isupden.schedulingmodule.strategy.CompositeSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.UsageAwareStrategy;

@Slf4j
@Component
@Scope("prototype")
public class SchedulerWorkflowImpl implements SchedulerWorkflow {

    /* ─────────── DI ─────────── */
    private final SchedulingModuleProperties props;
    private final Map<String, SchedulingStrategy> strategies;
    /* ─────────── state ─────────── */
    private final Queue<Task> ready = new LinkedList<>();
    private final Deque<Instant> window = new ArrayDeque<>();   // скользящее окно
    private final List<Promise<Void>> async = new ArrayList<>();
    private DispatchActivity dispatch;
    private SchedulingStrategy strategy;
    private SchedulingModuleProperties.ClientProperties cfg;

    public SchedulerWorkflowImpl(SchedulingModuleProperties p,
                                 Map<String, SchedulingStrategy> reg,
                                 DispatchActivity a) {
        this.props = p;
        this.strategies = reg;
        this.dispatch = a;
    }

    /* ═════════════ Workflow API ═════════════ */

    @Override
    public void run(String clientName) {
        cfg = Optional.ofNullable(props.getClients().get(clientName))
                .orElseThrow();                      // если клиента нет — ошибка

        strategy = buildStrategy(cfg.getStrategy());

        ActivityOptions ao = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(1)).build();
        dispatch = Workflow.newActivityStub(DispatchActivity.class, ao);

        Workflow.await(() -> !ready.isEmpty());

        try {
            while (true) {
                strategy.preprocess(ready, now());

                Task next = ready.stream()
                        .min(strategy::compare)
                        .orElse(null);
                if (next == null) {
                    Workflow.await(() -> !ready.isEmpty());
                    continue;
                }
                ready.remove(next);

                /* back-pressure BEFORE dispatch */
                throttleIfNeeded();

                /* асинхронный dispatch */
                Promise<Void> p = Async.procedure(() -> dispatch.dispatchTask(
                        next.getWorkflowType(),
                        next.getWorkflowId(),
                        next.getPayload(),
                        cfg.getTaskQueue()));
                async.add(p);

                /* фиксируем факт dispatch-а */
                recordDispatch();

                strategy.onDispatch(next, now());
            }

        } catch (CanceledFailure cf) {
            Promise.allOf(async).get();
            strategy.onShutdown();
        }
    }

    /* ───── signals / query ───── */
    @Override
    public void submitTasks(List<Task> tasks) {
        ready.addAll(tasks);
    }

    @Override
    public void reportUsage(String tenant, double cost) {
        if (strategy instanceof UsageAwareStrategy u) {
            u.recordUsage(tenant, cost, now());
        }
    }

    @Override
    public int getQueueLength() {
        return ready.size();
    }

    /* ─────────── helpers ─────────── */
    private SchedulingStrategy buildStrategy(String name) {
        List<SchedulingStrategy> list = Arrays.stream(name.split("\\+"))
                .map(strategies::get).toList();
        return list.size() == 1 ? list.getFirst() : new CompositeSchedulingStrategy(list);
    }

    private Instant now() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }

    /* запись метки времени успешного dispatch-а */
    private void recordDispatch() {
        window.addLast(now());
    }

    /* скользящее окно + динамический sleep */
    private void throttleIfNeeded() {
        long W = props.getBackpressure().getWindowSeconds();
        double limit = props.getBackpressure().getThroughputFactor();  // задач / сек

        Instant border = now().minusSeconds(W);
        while (!window.isEmpty() && window.peekFirst().isBefore(border)) {
            window.removeFirst();
        }

        double rate = window.size() / (double) W;      // фактический QPS
        if (rate <= limit) {
            return;
        }

        double excess = rate / limit - 1.0;              // 0 … ∞
        long sleepMs = (long) (excess * W * 1000);      // ≤ W с
        log.debug("Back-pressure: qps {} > {}, sleep {} ms", rate, limit, sleepMs);
        Workflow.sleep(Duration.ofMillis(sleepMs));
    }
}
