package ru.isupden.schedulingmodule.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(SchedulingModuleProperties props) {
        return registry -> registry.config()
                .commonTags(Tags.of(
                        Tag.of("application", "scheduling-module"),
                        Tag.of("namespace", props.getNamespace())
                ));
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
