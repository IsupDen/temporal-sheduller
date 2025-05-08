package ru.isupden.schedulingmodule.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "ru.isupden.schedulingmodule")
@EnableConfigurationProperties(SchedulingModuleProperties.class)
public class SchedulingModuleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WorkflowServiceStubs temporalService() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs,
                                         SchedulingModuleProperties props) {
        WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
                .setNamespace(props.getNamespace())
                .build();
        return WorkflowClient.newInstance(stubs, options);
    }
}
