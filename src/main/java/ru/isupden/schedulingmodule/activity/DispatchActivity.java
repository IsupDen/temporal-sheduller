package ru.isupden.schedulingmodule.activity;

import java.util.Map;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for dispatching a new top-level workflow.
 */
@ActivityInterface
public interface DispatchActivity {
    @ActivityMethod
    void dispatchTask(String workflowType,
                      String workflowId,
                      Map<String,Object> payload,
                      String taskQueue);
}
