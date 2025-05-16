package ru.isupden.schedulingmodule.interceptor;

import io.temporal.common.interceptors.WorkflowInboundCallsInterceptor;
import io.temporal.common.interceptors.WorkflowInboundCallsInterceptorBase;
import io.temporal.workflow.Workflow;
import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.workflow.SchedulerWorkflow;

/**
 * Интерцептор автоматически отправляет signal reportUsage()
 * при любом завершении child-Workflow.
 */
@Component
public class UsageReportInterceptor extends WorkflowInboundCallsInterceptorBase {

    private long startMs;

    public UsageReportInterceptor(WorkflowInboundCallsInterceptor next) {
        super(next);
    }

    @Override
    public WorkflowOutput execute(WorkflowInput input) {
        startMs = Workflow.currentTimeMillis();
        WorkflowOutput out;
        try {
            out = super.execute(input);
        } finally {
            sendUsageSignal();
        }
        return out;
    }

    /* ---------------- helper ---------------- */
    private void sendUsageSignal() {
        var tenant = Workflow.getMemo("tenantId", String.class, null);
        if (tenant == null) {
            return;
        }

        var cpuSec = (Workflow.currentTimeMillis() - startMs) / 1000.0;

        // Получаем clientName из Memo или используем namespace как fallback
        var clientName = Workflow.getMemo("clientName", String.class);

        var sched = Workflow.newExternalWorkflowStub(
                SchedulerWorkflow.class, "SCHED_" + clientName);

        sched.reportUsage(tenant, cpuSec);
    }
}
