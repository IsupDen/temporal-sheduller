package ru.isupden.schedulingmodule.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import ru.isupden.schedulingmodule.config.SchedulingModuleAutoConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SchedulingModuleAutoConfiguration.class)
public @interface EnableSchedulingModule {
}
