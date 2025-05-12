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

    private final long startMs = Workflow.currentTimeMillis();

    public UsageReportInterceptor(WorkflowInboundCallsInterceptor next) {
        super(next);
    }

    @Override                                        // ← актуальная сигнатура
    public WorkflowOutput execute(WorkflowInput input) {
        WorkflowOutput out;
        try {
            out = super.execute(input);                 // выполняем Workflow
        } finally {
            sendUsageSignal();
        }
        return out;
    }

    /* ---------------- helper ---------------- */
    private void sendUsageSignal() {
        // tenantId может быть передан в Memo при запуске child-WF
        String tenant = Workflow.getMemo("tenantId", String.class, null);
        if (tenant == null) {
            return;
        }

        double cpuSec = (Workflow.currentTimeMillis() - startMs) / 1000.0;

        SchedulerWorkflow sched = Workflow.newExternalWorkflowStub(
                SchedulerWorkflow.class, "SCHED_" + Workflow.getInfo().getNamespace());

        sched.reportUsage(tenant, cpuSec);
    }
}
