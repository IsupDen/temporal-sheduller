package ru.isupden.schedulingmodule.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.activity.DispatchActivityImpl;
import ru.isupden.schedulingmodule.strategy.CriticalPathSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.DeadlineSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.FairnessSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.PrioritySchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;
import ru.isupden.schedulingmodule.workflow.SchedulerWorkflow;
import ru.isupden.schedulingmodule.workflow.SchedulerWorkflowImpl;

@Configuration
@EnableConfigurationProperties(SchedulingModuleProperties.class)
@RequiredArgsConstructor
public class SchedulingModuleAutoConfiguration {

    private final SchedulingModuleProperties props;

    /* ──────── Temporal basics ──────── */

    @Bean
    public WorkflowServiceStubs serviceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(
                stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(props.getNamespace())
                        .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    /* ──────── Dispatch-activity ──────── */

    @Bean
    @ConditionalOnMissingBean(DispatchActivity.class)
    public DispatchActivity dispatchActivity(WorkflowClient client) {
        return new DispatchActivityImpl(client);
    }

    /* ──────── «Штатные» стратегии ──────── */

    @Bean("priority")
    public SchedulingStrategy priority() {
        return new PrioritySchedulingStrategy();
    }

    @Bean("deadline")
    public SchedulingStrategy deadline() {
        return new DeadlineSchedulingStrategy();
    }

    @Bean("critical")
    public SchedulingStrategy critical() {
        return new CriticalPathSchedulingStrategy();
    }

    @Bean("fairness")
    public SchedulingStrategy fairness() {
        return new FairnessSchedulingStrategy(
                props.getQuotas(),
                props.getFairness().getHalfLifeSeconds());
    }

    /**
     * Map<String, SchedulingStrategy> для автосвязывания в SchedulerWorkflowImpl
     */
    @Bean
    public Map<String, SchedulingStrategy> strategyRegistry(
            @Qualifier("priority") SchedulingStrategy priority,
            @Qualifier("deadline") SchedulingStrategy deadline,
            @Qualifier("critical") SchedulingStrategy critical,
            @Qualifier("fairness") SchedulingStrategy fairness) {

        Map<String, SchedulingStrategy> m = new HashMap<>();
        m.put("priority", priority);
        m.put("deadline", deadline);
        m.put("critical", critical);
        m.put("fairness", fairness);
        return m;
    }

    /* ──────── Служебные worker-ы (по одному на клиента) ──────── */

    @Bean
    public SchedulerWorkflowImpl schedulerWorkflowImpl(
            SchedulingModuleProperties props,
            Map<String, SchedulingStrategy> strategies,
            DispatchActivity dispatchActivity
    ) {
        return new SchedulerWorkflowImpl(props, strategies, dispatchActivity);
    }

    @Bean
    public List<Worker> schedulerWorkers(
            WorkerFactory factory,
            DispatchActivity dispatchActivity,
            SchedulerWorkflowImpl schedulerWorkflowImpl
    ) {
        List<Worker> list = new ArrayList<>();
        props.getClients().forEach((name, cfg) -> {
            String q = "scheduler-" + name;

            Worker w = factory.newWorker(q);

            // регистрируем не класс, а фабрику, возвращающую Spring-бину
            w.registerWorkflowImplementationFactory(
                    SchedulerWorkflow.class,
                    () -> schedulerWorkflowImpl
            );

            w.registerActivitiesImplementations(dispatchActivity);
            list.add(w);
        });
        return list;
    }

    /* ──────── Автоматический запуск Scheduler-workflow-ов ──────── */

    @Bean
    public ApplicationListener<ApplicationReadyEvent> bootstrapSchedulers(
            WorkerFactory factory, WorkflowClient client) {

        return evt -> {
            // 1) стартуем Scheduler-workflow-ы (если ещё не запущены)
            props.getClients().forEach((name, cfg) -> {
                String wfId = "SCHED_" + name;
                String q = "scheduler-" + name;

                SchedulerWorkflow stub = client.newWorkflowStub(
                        SchedulerWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(wfId)
                                .setTaskQueue(q)
                                .build());

                try {
                    WorkflowClient.start(stub::run, name);
                } catch (WorkflowExecutionAlreadyStarted ignore) {
                }
            });

            // 2) запускаем ВСЕ собранные worker-poller-ы
            factory.start();
        };
    }
}
