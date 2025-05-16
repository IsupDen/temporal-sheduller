package ru.isupden.schedulingmodule.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchedulingMetricsService {

    private final MeterRegistry registry;

    private final Map<String, AtomicInteger> queueSizeByClient = new ConcurrentHashMap<>();
    private final Map<String, Counter> taskDispatchedByClient = new ConcurrentHashMap<>();
    private final Map<String, Counter> taskDispatchedByType = new ConcurrentHashMap<>();
    private final Map<String, Counter> taskDispatchedByTenant = new ConcurrentHashMap<>();
    private final Map<String, Timer> taskExecutionByType = new ConcurrentHashMap<>();
    private final Map<String, Counter> resourceUsageByTenant = new ConcurrentHashMap<>();

    /**
     * Регистрирует клиента для отслеживания метрик очереди
     */
    public void registerClient(String clientName) {
        queueSizeByClient.computeIfAbsent(clientName, k -> {
            AtomicInteger value = new AtomicInteger(0);
            Gauge.builder("scheduling.queue.size", value, AtomicInteger::get)
                    .tag("client", clientName)
                    .description("Current size of the scheduling queue")
                    .register(registry);
            return value;
        });

        taskDispatchedByClient.computeIfAbsent(clientName, k ->
                Counter.builder("scheduling.tasks.dispatched.client")
                        .tag("client", clientName)
                        .description("Number of tasks dispatched by client")
                        .register(registry)
        );
    }

    /**
     * Обновляет размер очереди для клиента
     */
    public void updateQueueSize(String clientName, int size) {
        queueSizeByClient.computeIfAbsent(clientName, k -> {
            AtomicInteger value = new AtomicInteger(0);
            Gauge.builder("scheduling.queue.size", value, AtomicInteger::get)
                    .tag("client", clientName)
                    .description("Current size of the scheduling queue")
                    .register(registry);
            return value;
        }).set(size);
    }

    /**
     * Регистрирует отправку задачи
     */
    public void recordTaskDispatched(String clientName, String workflowType, String tenantId) {
        taskDispatchedByClient.computeIfAbsent(clientName, k ->
                Counter.builder("scheduling.tasks.dispatched.client")
                        .tag("client", clientName)
                        .description("Number of tasks dispatched by client")
                        .register(registry)
        ).increment();

        taskDispatchedByType.computeIfAbsent(workflowType, k ->
                Counter.builder("scheduling.tasks.dispatched.type")
                        .tag("workflowType", workflowType)
                        .description("Number of tasks dispatched by workflow type")
                        .register(registry)
        ).increment();

        if (tenantId != null) {
            taskDispatchedByTenant.computeIfAbsent(tenantId, k ->
                    Counter.builder("scheduling.tasks.dispatched.tenant")
                            .tag("tenant", tenantId)
                            .description("Number of tasks dispatched by tenant")
                            .register(registry)
            ).increment();
        }
    }

    /**
     * Получает таймер для измерения времени выполнения задачи
     */
    public Timer getTaskExecutionTimer(String workflowType) {
        return taskExecutionByType.computeIfAbsent(workflowType, k ->
                Timer.builder("scheduling.task.execution")
                        .tag("workflowType", workflowType)
                        .description("Task execution time by workflow type")
                        .register(registry)
        );
    }

    /**
     * Регистрирует использование ресурсов тенантом
     */
    public void recordResourceUsage(String tenantId, double cost) {
        if (tenantId != null) {
            resourceUsageByTenant.computeIfAbsent(tenantId, k ->
                    Counter.builder("scheduling.resource.usage")
                            .tag("tenant", tenantId)
                            .description("Resource usage by tenant")
                            .register(registry)
            ).increment(cost);
        }
    }

    /**
     * Регистрирует время ожидания задачи в очереди до отправки
     */
    public void recordTaskWaitTime(String clientName, long waitTimeMs) {
        Timer.builder("scheduling.task.wait")
                .tag("client", clientName)
                .description("Time tasks wait in queue before being dispatched")
                .register(registry)
                .record(java.time.Duration.ofMillis(waitTimeMs));
    }

    /**
     * Регистрирует задержку из-за backpressure
     */
    public void recordBackpressureDelay(String clientName, long delayMs) {
        Timer.builder("scheduling.backpressure.delay")
                .tag("client", clientName)
                .description("Delay caused by backpressure mechanism")
                .register(registry)
                .record(java.time.Duration.ofMillis(delayMs));
    }
}
