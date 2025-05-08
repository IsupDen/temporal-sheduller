package ru.isupden.schedulingmodule.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures strategies & workflow/activity beans are detected.
 */
@Configuration
@ComponentScan("ru.isupden.schedulingmodule")
public class StrategyConfiguration {
}
