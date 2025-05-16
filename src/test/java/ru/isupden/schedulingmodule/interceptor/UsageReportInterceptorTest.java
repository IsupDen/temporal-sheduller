package ru.isupden.schedulingmodule.interceptor;

import io.temporal.common.interceptors.WorkflowInboundCallsInterceptor;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ru.isupden.schedulingmodule.workflow.SchedulerWorkflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageReportInterceptorTest {

    @Test
    void sendUsageSignal_shouldSendSignal_whenTenantIdPresent() {
        // Arrange
        var next = mock(WorkflowInboundCallsInterceptor.class);
        var interceptor = new UsageReportInterceptor(next);

        var input = mock(WorkflowInboundCallsInterceptor.WorkflowInput.class);
        var output =
                mock(WorkflowInboundCallsInterceptor.WorkflowOutput.class);

        // Мокаем выполнение воркфлоу
        when(next.execute(any())).thenReturn(output);

        try (MockedStatic<Workflow> workflowMock = mockStatic(Workflow.class)) {
            workflowMock.when(() -> Workflow.currentTimeMillis()).thenReturn(1000L, 4000L); // startMs=1000, finish=4000
            workflowMock.when(() -> Workflow.getMemo(eq("tenantId"), eq(String.class), any()))
                    .thenReturn("tenant1");
            workflowMock.when(() -> Workflow.getMemo(eq("clientName"), eq(String.class)))
                    .thenReturn("clientA");

            var schedMock = mock(SchedulerWorkflow.class);
            workflowMock.when(() ->
                            Workflow.newExternalWorkflowStub(eq(SchedulerWorkflow.class), eq("SCHED_clientA")))
                    .thenReturn(schedMock);

            // Act
            interceptor.execute(input);

            // Assert
            verify(schedMock).reportUsage(eq("tenant1"), eq(3.0)); // 4000-1000=3000ms=3.0 сек
        }
    }

    @Test
    void sendUsageSignal_shouldNotSendSignal_whenTenantIdMissing() {
        var next = mock(WorkflowInboundCallsInterceptor.class);
        var interceptor = new UsageReportInterceptor(next);

        var input = mock(WorkflowInboundCallsInterceptor.WorkflowInput.class);
        var output =
                mock(WorkflowInboundCallsInterceptor.WorkflowOutput.class);

        when(next.execute(any())).thenReturn(output);

        try (MockedStatic<Workflow> workflowMock = mockStatic(Workflow.class)) {
            workflowMock.when(Workflow::currentTimeMillis).thenReturn(1000L, 4000L);
            workflowMock.when(() -> Workflow.getMemo(eq("tenantId"), eq(String.class), any()))
                    .thenReturn("tenant1");
            workflowMock.when(() -> Workflow.getMemo(eq("clientName"), eq(String.class)))
                    .thenReturn("clientA");

            var schedMock = mock(SchedulerWorkflow.class);
            workflowMock.when(() ->
                    Workflow.newExternalWorkflowStub(
                            eq(SchedulerWorkflow.class),
                            eq("SCHED_clientA")
                    )
            ).thenReturn(schedMock);

            interceptor.execute(input);

            verify(schedMock).reportUsage(eq("tenant1"), eq(3.0));
        }
    }
}
