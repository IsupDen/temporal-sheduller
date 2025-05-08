package ru.isupden.schedulingmodule.activity;

import java.util.Map;
import java.util.Objects;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of DispatchActivity, starts external workflows.
 */
@Component
public class DispatchActivityImpl implements DispatchActivity {
    private final WorkflowClient client;

    public DispatchActivityImpl(WorkflowClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public void dispatchTask(String workflowType,
                             String workflowId,
                             Map<String,Object> payload,
                             String taskQueue) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(workflowId)
                .build();

        WorkflowStub stub = client.newUntypedWorkflowStub(workflowType, options);
        // pass the payload object directly
        stub.start(payload);
    }
}
