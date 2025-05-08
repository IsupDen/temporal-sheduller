package ru.isupden.schedulingmodule.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.config.SchedulingModuleProperties;
import ru.isupden.schedulingmodule.model.Task;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;

@Component
@Slf4j
public class SchedulerWorkflowImpl implements SchedulerWorkflow {

    private final SchedulingModuleProperties properties;
    private final Map<String, SchedulingStrategy<? extends Task>> strategies;
    private DispatchActivity dispatchActivity;

    private final Queue<Task> readyQueue = new LinkedList<>();
    private final Deque<Instant> completionTimes = new ArrayDeque<>();
    private final List<Promise<Void>> dispatchPromises = new ArrayList<>();

    private String currentClient;
    private String currentStrategyName;
    private double lastComputedThroughput = 0.0;

    @Autowired
    public SchedulerWorkflowImpl(SchedulingModuleProperties properties,
                                 Map<String, SchedulingStrategy<? extends Task>> strategies,
                                 DispatchActivity dispatchActivity) {
        this.properties = properties;
        this.strategies = strategies;
        this.dispatchActivity = dispatchActivity;
    }

    @Override
    public void run(String clientName) {
        this.currentClient = clientName;
        SchedulingModuleProperties.ClientProperties clientConfig =
                properties.getClients().getOrDefault(clientName, properties.getClients().get("default"));
        if (clientConfig == null) {
            throw Workflow.wrap(new IllegalArgumentException("Unknown client: " + clientName));
        }
        this.currentStrategyName = clientConfig.getStrategy();

        // rawStrategy may be SchedulingStrategy<PriorityTask>, <DeadlineTask>, etc.
        var rawStrategy = strategies.get(currentStrategyName);
        // determine which subclass of Task this strategy handles
        var taskClass = rawStrategy.getTaskClass();
        // cast to a non-generic SchedulingStrategy<Task> for invocation
        @SuppressWarnings("unchecked")
        var strategy = (SchedulingStrategy<Task>) rawStrategy;

        ActivityOptions ao = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(1))
                .build();
        this.dispatchActivity = Workflow.newActivityStub(DispatchActivity.class, ao);

        // wait for initial tasks
        Workflow.await(() -> !readyQueue.isEmpty());
        try {
            while (true) {
                // 1) filter readyQueue down to only the subclass this strategy cares about
                Queue<Task> filtered = readyQueue.stream()
                        .filter(taskClass::isInstance)
                        .map(taskClass::cast)
                        .collect(Collectors.toCollection(LinkedList::new));

                // 2) pick next task via the strategy
                Task next = strategy.nextTask(filtered);
                log.info("Next task: {}", next);
                if (next == null) {
                    Workflow.await(() -> !readyQueue.isEmpty());
                    continue;
                }
                // remove the selected task from the shared queue
                readyQueue.remove(next);

                // back-pressure: record timestamp and evict old entries
                Instant now = Instant.ofEpochMilli(Workflow.currentTimeMillis());
                completionTimes.addLast(now);
                long windowSec = properties.getBackpressure().getWindowSeconds();
                while (!completionTimes.isEmpty()
                        && completionTimes.peekFirst().isBefore(now.minusSeconds(windowSec))) {
                    completionTimes.removeFirst();
                }
                lastComputedThroughput = completionTimes.size() / (double) windowSec;
                if (lastComputedThroughput > properties.getBackpressure().getThroughputFactor()) {
                    Workflow.sleep(Duration.ofSeconds(1));
                }

                // dispatch asynchronously
                Promise<Void> p = Async.procedure(() ->
                        dispatchActivity.dispatchTask(
                                next.getWorkflowType(),
                                next.getWorkflowId(),
                                next.getPayload(),
                                clientConfig.getTaskQueue()
                        )
                );
                dispatchPromises.add(p);
            }
        } catch (CanceledFailure cf) {
            // graceful shutdown: wait for all dispatches to start
            Promise.allOf(dispatchPromises).get();
        }
    }

    @Override
    public void submitTasks(List<Task> tasks) {
        readyQueue.addAll(tasks);
    }

    @Override
    public int getQueueLength() {
        return readyQueue.size();
    }

    @Override
    public double getCurrentThroughput() {
        return lastComputedThroughput;
    }

    @Override
    public String getCurrentStrategy() {
        return currentStrategyName;
    }
}
