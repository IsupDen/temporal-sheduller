package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;

public interface UsageAwareStrategy extends SchedulingStrategy {
    /** Учесть фактически использованный ресурс. */
    void recordUsage(String tenantId, double cost, Instant at);
}
