package ru.isupden.schedulingmodule.config;

import java.util.HashMap;
import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.activity.DispatchActivityImpl;
import ru.isupden.schedulingmodule.strategy.CriticalPathSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.DeadlineSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.FairnessSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.PrioritySchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;

@Configuration
@EnableConfigurationProperties(SchedulingModuleProperties.class)
@RequiredArgsConstructor
public class SchedulingModuleAutoConfiguration {

    private final SchedulingModuleProperties props;

    /* -------- Temporal базовые бины, если пользователь их не создал сам -------- */

    @Bean
    @ConditionalOnMissingBean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    /* ---------------- Activity ---------------- */

    @Bean
    @ConditionalOnMissingBean(DispatchActivity.class)
    public DispatchActivity dispatchActivity(WorkflowClient client) {
        return new DispatchActivityImpl(client);
    }

    /* ---------------- Регистрация «штатных» стратегий ---------------- */

    @Bean("priority")
    public SchedulingStrategy priorityStrategy() { return new PrioritySchedulingStrategy(); }

    @Bean("deadline")
    public SchedulingStrategy deadlineStrategy() { return new DeadlineSchedulingStrategy(); }

    @Bean("critical")
    public SchedulingStrategy criticalStrategy() { return new CriticalPathSchedulingStrategy(); }

    @Bean("fairness")
    public SchedulingStrategy fairnessStrategy() {
        return new FairnessSchedulingStrategy(
                props.getQuotas(),
                props.getFairness().getHalfLifeSeconds());
    }

    /**
     * Регистратор бинов в Map: <имя → стратегия>.
     * Пользователь может @Autowire Map<String,SchedulingStrategy> strategies
     * и получить все зарегистрированные экземпляры.
     */
    @Bean
    public Map<String, SchedulingStrategy> strategyRegistry(
            PrioritySchedulingStrategy priority,
            DeadlineSchedulingStrategy deadline,
            FairnessSchedulingStrategy fairness,
            CriticalPathSchedulingStrategy critical) {

        Map<String, SchedulingStrategy> map = new HashMap<>();
        map.put("priority", priority);
        map.put("deadline", deadline);
        map.put("fairness", fairness);
        map.put("critical", critical);
        return map;
    }
}
