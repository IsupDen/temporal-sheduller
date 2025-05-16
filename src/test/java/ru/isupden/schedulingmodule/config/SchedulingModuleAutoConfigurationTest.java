package ru.isupden.schedulingmodule.config;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.isupden.schedulingmodule.activity.DispatchActivity;
import ru.isupden.schedulingmodule.strategy.CriticalPathSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.DeadlineSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.FairnessSchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.PrioritySchedulingStrategy;
import ru.isupden.schedulingmodule.strategy.SchedulingStrategy;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SchedulingModuleAutoConfiguration.class)
class SchedulingModuleAutoConfigurationTest {

    @Autowired
    WorkflowServiceStubs serviceStubs;

    @Autowired
    WorkflowClient workflowClient;

    @Autowired
    WorkerFactory workerFactory;

    @Autowired
    DispatchActivity dispatchActivity;

    @Autowired
    Map<String, SchedulingStrategy> strategyRegistry;

    @Test
    void contextLoads_andAllBeansCreated() {
        assertThat(serviceStubs).isNotNull();
        assertThat(workflowClient).isNotNull();
        assertThat(workerFactory).isNotNull();
        assertThat(dispatchActivity).isNotNull();
        assertThat(strategyRegistry).isNotNull().containsKeys("priority", "deadline", "critical", "fairness");
        assertThat(strategyRegistry.get("priority")).isInstanceOf(PrioritySchedulingStrategy.class);
        assertThat(strategyRegistry.get("deadline")).isInstanceOf(DeadlineSchedulingStrategy.class);
        assertThat(strategyRegistry.get("critical")).isInstanceOf(CriticalPathSchedulingStrategy.class);
        assertThat(strategyRegistry.get("fairness")).isInstanceOf(FairnessSchedulingStrategy.class);
    }
}