package ru.isupden.schedulingmodule.config;

import java.util.concurrent.TimeUnit;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class TemporalLifecycleManager implements DisposableBean {
    private final WorkerFactory workerFactory;
    private final WorkflowServiceStubs serviceStubs;
    private final Logger log = LoggerFactory.getLogger(TemporalLifecycleManager.class);

    public TemporalLifecycleManager(
            WorkerFactory workerFactory,
            WorkflowServiceStubs serviceStubs) {
        this.workerFactory = workerFactory;
        this.serviceStubs = serviceStubs;
    }

    @Override
    public void destroy() {
        log.info("Initiating graceful shutdown of Temporal components...");
        workerFactory.shutdown();
        workerFactory.awaitTermination(30, TimeUnit.SECONDS);
        serviceStubs.shutdown();
        log.info("Temporal service stubs closed");
    }
}