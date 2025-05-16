package ru.isupden.schedulingmodule.activity;

import java.util.Map;

import io.micrometer.core.annotation.Timed;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.metrics.SchedulingMetricsService;

/**
 * Implementation of DispatchActivity, starts external workflows.
 */
@Component
@RequiredArgsConstructor
public class DispatchActivityImpl implements DispatchActivity {

    private final WorkflowClient client;
    private final SchedulingMetricsService metricsService;

    @Override
    @Timed(value = "dispatch.activity.execution",
            description = "Time taken to dispatch a task to Temporal",
            extraTags = {"activity", "dispatchTask"})
    public void dispatchTask(String wfType,
                             String wfId,
                             Map<String, Object> payload,
                             String taskQueue) {
        long startTime = System.currentTimeMillis();

        var tenant = (String) payload.getOrDefault("tenantId", "default");
        var opts = WorkflowOptions.newBuilder()
                .setWorkflowId(wfId)
                .setTaskQueue(taskQueue)
                .setMemo(Map.of("tenantId", tenant))
                .build();

        client.newUntypedWorkflowStub(wfType, opts).start(payload);

        var executionTime = System.currentTimeMillis() - startTime;

        metricsService.getTaskExecutionTimer(wfType)
                .record(java.time.Duration.ofMillis(executionTime));
    }
}
