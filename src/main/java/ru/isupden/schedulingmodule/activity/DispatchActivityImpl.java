package ru.isupden.schedulingmodule.activity;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementation of DispatchActivity, starts external workflows.
 */
@Component
@RequiredArgsConstructor
public class DispatchActivityImpl implements DispatchActivity {

    private final WorkflowClient client;

    @Override
    public void dispatchTask(String wfType,
                             String wfId,
                             Map<String, Object> payload,
                             String taskQueue) {
        String tenant = (String) payload.getOrDefault("tenantId", "default");
        WorkflowOptions opts = WorkflowOptions.newBuilder()
                .setWorkflowId(wfId)
                .setTaskQueue(taskQueue)
                .setMemo(Map.of("tenantId", tenant))      // ← сюда
                .build();

        client.newUntypedWorkflowStub(wfType, opts).start(payload);
    }
}
