package ru.isupden.schedulingmodule.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "scheduling-module")
public class SchedulingModuleProperties {
    /**
     * Temporal namespace
     */
    private String namespace = "default";

    /**
     * Back-pressure parameters
     */
    private Backpressure backpressure = new Backpressure();

    /**
     * Map of client configurations
     */
    private Map<String, ClientProperties> clients = new HashMap<>();

    @Data
    public static class Backpressure {
        /**
         * Sliding window size in seconds
         */
        private int windowSeconds = 60;
        /**
         * Throughput factor threshold
         */
        private double throughputFactor = 1.0;
    }

    @Data
    public static class ClientProperties {
        /**
         * Temporal Task Queue name
         */
        private String taskQueue;
        /**
         * Scheduling strategy bean name
         */
        private String strategy;
    }
}
