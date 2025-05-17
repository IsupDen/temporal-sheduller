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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.config.SchedulingModuleProperties;
import ru.isupden.schedulingmodule.metrics.SchedulingMetricsService;
import ru.isupden.schedulingmodule.model.Task;
import ru.isupden.schedulingmodule.strategy.CompositeSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.UsageAwareStrategy;

@Slf4j
@Component
@Scope("prototype")
@NoArgsConstructor
public class SchedulerWorkflowImpl implements SchedulerWorkflow {

    private final Queue<Task> ready = new LinkedList<>();
    private final Deque<Instant> window = new ArrayDeque<>();
    private final List<Promise<Void>> async = new ArrayList<>();
    private SchedulingMetricsService metricsService;
    private SchedulingModuleProperties props;
    private Map<String, SchedulingStrategy> strategies;
    private DispatchActivity dispatch;
    private SchedulingStrategy strategy;
    private SchedulingModuleProperties.ClientProperties cfg;

    public SchedulerWorkflowImpl(SchedulingModuleProperties p,
                                 Map<String, SchedulingStrategy> reg,
                                 DispatchActivity a,
                                 SchedulingMetricsService m) {
        this.props = p;
        this.strategies = reg;
        this.dispatch = a;
        this.metricsService = m;
    }

    public void initialize(SchedulingModuleProperties p,
                           Map<String, SchedulingStrategy> reg,
                           DispatchActivity a,
                           SchedulingMetricsService m) {
        this.props = p;
        this.strategies = reg;
        this.dispatch = a;
        this.metricsService = m;
    }

    @Override
    public void run(String clientName) {
        if (props == null) {
            throw new IllegalStateException("SchedulerWorkflowImpl not properly initialized");
        }
        log.info("Starting scheduler workflow for client: {}; props: {}", clientName, props);

        cfg = Optional.ofNullable(props.getClients().get(clientName))
                .orElseThrow();

        strategy = buildStrategy(cfg.getStrategy());
        metricsService.registerClient(clientName);
        log.info("Using strategy: {}", cfg.getStrategy());

        var ao = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(1)).build();
        dispatch = Workflow.newActivityStub(DispatchActivity.class, ao);

        // Ожидаем появления задач
        Workflow.await(() -> !ready.isEmpty());
        log.info("Initial tasks received, starting processing");

        try {
            while (true) {
                metricsService.updateQueueSize(clientName, ready.size());
                // Предобработка очереди перед выбором задачи
                strategy.preprocess(ready, now());

                // Безопасный выбор следующей задачи для выполнения
                var next = selectNextTask();

                if (next == null) {
                    log.info("No suitable tasks found, waiting for more tasks");
                    Workflow.await(() -> !ready.isEmpty());
                    continue;
                }

                log.info("Selected task for dispatch: {}", next.getWorkflowId());
                var taskReadyTime = now();
                ready.remove(next);

                /* throttling перед dispatch */
                throttleIfNeeded(clientName);

                var waitTimeMs = Duration.between(taskReadyTime, now()).toMillis();
                metricsService.recordTaskWaitTime(clientName, waitTimeMs);

                var tenantId = (String) next.getPayload().getOrDefault("tenantId",
                        next.attr("tenantId", String.class));
                metricsService.recordTaskDispatched(clientName, next.getWorkflowType(), tenantId);

                /* асинхронный dispatch */
                log.info("Dispatching task: {} to queue: {}", next.getWorkflowId(), cfg.getTaskQueue());
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
            log.info("Workflow being canceled, awaiting completion of dispatched tasks...");
            try {
                Promise.allOf(async).get();
                log.info("All dispatched tasks completed");
            } catch (Exception e) {
                log.error("Error while waiting for tasks to complete", e);
            } finally {
                strategy.onShutdown();
                log.info("Strategy shutdown completed");
            }
        }
    }

    /*
     * Безопасный выбор следующей задачи с учетом результатов canCompare
     */
    private Task selectNextTask() {
        if (ready.isEmpty()) {
            return null;
        }

        // Если в очереди одна задача, просто возвращаем её
        if (ready.size() == 1) {
            return ready.peek();
        }

        // Создаем безопасную функцию сравнения для минимизации
        return ready.stream()
                .min((a, b) -> {
                    try {
                        return strategy.compare(a, b);
                    } catch (Exception e) {
                        log.warn("Error comparing tasks: {}", e.getMessage());
                        return 0; // Если произошла ошибка, считаем задачи равными
                    }
                })
                .orElse(null);
    }

    /* ───── signals / query ───── */
    @Override
    public void submitTasks(List<Task> tasks, String clientName) {
        log.info("Received {} tasks", tasks.size());
        ready.addAll(tasks);
        metricsService.updateQueueSize(clientName, ready.size());
    }

    @Override
    public void reportUsage(String tenant, double cost) {
        if (strategy instanceof UsageAwareStrategy u) {
            log.info("Recording usage for tenant {}: {}", tenant, cost);
            u.recordUsage(tenant, cost, now());
            metricsService.recordResourceUsage(tenant, cost);
        }
    }

    @Override
    public int getQueueLength() {
        return ready.size();
    }

    /* ─────────── helpers ─────────── */
    private SchedulingStrategy buildStrategy(String name) {
        var list = Arrays.stream(name.split("\\+"))
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
    private void throttleIfNeeded(String clientName) {
        if (props == null) {
            return;
        }
        var W = props.getBackpressure().getWindowSeconds();
        var limit = props.getBackpressure().getThroughputFactor();  // задач / сек

        var border = now().minusSeconds(W);
        while (!window.isEmpty() && window.peekFirst().isBefore(border)) {
            window.removeFirst();
        }

        var rate = window.size() / (double) W;      // фактический QPS
        if (rate <= limit) {
            return;
        }

        var excess = rate / limit - 1.0;              // 0 … ∞
        var sleepMs = (long) (excess * W * 1000);      // ≤ W с
        log.info("Back-pressure: qps {} > {}, sleep {} ms", rate, limit, sleepMs);
        if (sleepMs > 0) {
            metricsService.recordBackpressureDelay(clientName, sleepMs);
        }
        Workflow.sleep(Duration.ofMillis(sleepMs));
    }
}
