package ru.isupden.schedulingmodule.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Корневой YAML-префикс  `scheduling-module`. */
@Data
@ConfigurationProperties(prefix = "scheduling-module")
public class SchedulingModuleProperties {

    /** Temporal namespace, если он отличается от defaults. */
    private String namespace = "default";

    /** карта <clientName → ClientProperties>. */
    private Map<String, ClientProperties> clients = new HashMap<>();

    /** Глобальные back-pressure настройки. */
    private Backpressure backpressure = new Backpressure();

    /** Настройки fairness-затухания. */
    private Fairness fairness = new Fairness();

    /** Tenant-квоты (используются Fairness-стратегией). */
    private Map<String, Double> quotas = new HashMap<>();

    /* ---------- вложенные ---------- */

    @Data
    public static class ClientProperties {
        private String taskQueue;
        /**
         * Имя стратегии или композиция (`priority+deadline`).
         */
        private String strategy;
    }

    @Data
    public static class Backpressure {
        private long   windowSeconds      = 60;   // sliding-window W
        private double throughputFactor   = 10;   // лимит задач/сек
    }

    @Data
    public static class Fairness {
        /** Полувремя затухания EWMA (сек). */
        private long halfLifeSeconds = 3600;
    }
}
