package ru.isupden.schedulingmodule.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.config.SchedulingModuleProperties;
import ru.isupden.schedulingmodule.metrics.SchedulingMetricsService;
import ru.isupden.schedulingmodule.model.Task;
import ru.isupden.schedulingmodule.strategy.PrioritySchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerWorkflowImplTest {

    @RegisterExtension
    public static final TestWorkflowExtension testWorkflowExtension = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(SchedulerWorkflowImpl.class)
            .setDoNotStart(true)
            .build();
    private static final String CLIENT_NAME = "test-client";
    private static final String TASK_QUEUE = "test-task-queue";

    @Mock
    private SchedulingStrategy mockStrategy;
    @Mock
    private SchedulingMetricsService mockMetricsService;

    private SchedulingModuleProperties properties;
    private Map<String, SchedulingStrategy> strategies;
    private SchedulerWorkflow workflowStub;
    private WorkflowStub untypedWorkflowStub;
    private TestWorkflowEnvironment testEnv;

    @BeforeEach
    void setUp(TestWorkflowEnvironment testEnv) {
        MockitoAnnotations.openMocks(this);
        this.testEnv = testEnv;

        // Setup properties
        properties = new SchedulingModuleProperties();
        var clientProps = new SchedulingModuleProperties.ClientProperties();
        clientProps.setTaskQueue(TASK_QUEUE);
        clientProps.setStrategy("priority");

        Map<String, SchedulingModuleProperties.ClientProperties> clients = new HashMap<>();
        clients.put(CLIENT_NAME, clientProps);
        properties.setClients(clients);

        // Setup backpressure config with minimal throttling
        var backpressure = new SchedulingModuleProperties.Backpressure();
        backpressure.setWindowSeconds(60);
        backpressure.setThroughputFactor(1000); // High value to avoid throttling in tests
        properties.setBackpressure(backpressure);

        // Setup strategies
        strategies = new HashMap<>();
        strategies.put("priority", new PrioritySchedulingStrategy());
        strategies.put("mock", mockStrategy);

        // Инициализация тестового активити
        var testDispatchActivity = new TestDispatchActivityImpl();

        // Register workflow and activities
        var worker = testEnv.newWorker(CLIENT_NAME);

        // Регистрируем активити
        worker.registerActivitiesImplementations(testDispatchActivity);

        // Используем фабрику для регистрации workflow с зависимостями
        worker.registerWorkflowImplementationFactory(
                SchedulerWorkflow.class,
                () -> {
                    var workflow = new SchedulerWorkflowImpl();
                    // Инициализируем вручную, так как Spring не используется в тестах
                    workflow.initialize(properties, strategies, null, mockMetricsService);
                    return workflow;
                }
        );

        testEnv.start();

        // Create workflow stubs
        var workflowClient = testEnv.getWorkflowClient();
        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(CLIENT_NAME)
                .setWorkflowId("test-scheduler-workflow")
                .build();

        workflowStub = workflowClient.newWorkflowStub(SchedulerWorkflow.class, options);
        untypedWorkflowStub = WorkflowStub.fromTyped(workflowStub);
    }

    @Test
    void testSubmitAndProcessTasks() throws InterruptedException {
        // Start workflow (non-blocking)
        WorkflowClient.start(workflowStub::run, CLIENT_NAME);

        // Create tasks with different priorities
        var task1 = Task.builder()
                .workflowId("task1")
                .workflowType("TestWorkflow")
                .payload(Map.of("data", "test1"))
                .build();
        task1.getAttributes().put("priority", 1);

        var task2 = Task.builder()
                .workflowId("task2")
                .workflowType("TestWorkflow")
                .payload(Map.of("data", "test2"))
                .build();
        task2.getAttributes().put("priority", 3); // Higher priority

        var task3 = Task.builder()
                .workflowId("task3")
                .workflowType("TestWorkflow")
                .payload(Map.of("data", "test3"))
                .build();
        task3.getAttributes().put("priority", 2);

        // Submit tasks
        workflowStub.submitTasks(Arrays.asList(task1, task2, task3), "client");

        // Ждем некоторое время, чтобы Workflow успел выполнить планирование задач
        Thread.sleep(500);

        // Получаем историю выполнения Workflow
        var execution = untypedWorkflowStub.getExecution();
        var history = testEnv.getWorkflowServiceStubs().blockingStub().getWorkflowExecutionHistory(
                        GetWorkflowExecutionHistoryRequest.newBuilder()
                                .setNamespace("UnitTest")
                                .setExecution(execution)
                                .build())
                .getHistory();

        // Анализируем события в истории для определения порядка отправки задач
        List<String> scheduledActivityIds = new ArrayList<>();

        for (var event : history.getEventsList()) {
            if (event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED) {
                var workflowId = event.getActivityTaskScheduledEventAttributes()
                        .getInput().getPayloads(1).getData().toStringUtf8();

                // Удаляем кавычки из строки
                workflowId = workflowId.replaceAll("\"", "");
                scheduledActivityIds.add(workflowId);
            }
        }

        // Проверяем порядок планирования активити (должен соответствовать приоритетам)
        assertEquals(3, scheduledActivityIds.size(), "Должно быть запланировано 3 активити");
        assertEquals("task2", scheduledActivityIds.get(0), "Первой должна быть запланирована задача task2 (высший " +
                "приоритет)");
        assertEquals("task3", scheduledActivityIds.get(1), "Второй должна быть запланирована задача task3 (средний " +
                "приоритет)");
        assertEquals("task1", scheduledActivityIds.get(2), "Третьей должна быть запланирована задача task1 (низкий " +
                "приоритет)");

        // Verify current queue length
        assertEquals(0, workflowStub.getQueueLength());
    }

    // Тестовая реализация DispatchActivity
    // Сделана максимально простой, чтобы избежать проблем с инициализацией
    public static class TestDispatchActivityImpl implements DispatchActivity {

        @Override
        public void dispatchTask(String workflowType,
                                 String workflowId,
                                 Map<String, Object> payload,
                                 String taskQueue) {
            // Просто успешно завершаем активити
            System.out.println("Dispatching task: " + workflowId);
        }
    }
}
