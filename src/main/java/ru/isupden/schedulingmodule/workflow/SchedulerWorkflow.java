package ru.isupden.schedulingmodule.workflow;

import java.util.List;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import ru.isupden.schedulingmodule.model.Task;

@WorkflowInterface
public interface SchedulerWorkflow {

    /**
     * Main entrypoint. Client name selects TaskQueue & strategy.
     */
    @WorkflowMethod
    void run(String clientName);

    /**
     * Signal to add more tasks into the ready queue.
     */
    @SignalMethod
    void submitTasks(List<Task> tasks, String clientName);

    /**
     * Query current ready-queue length.
     */
    @QueryMethod
    int getQueueLength();

    @SignalMethod
    void reportUsage(String tenant, double cost);
}