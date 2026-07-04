package com.hieulc.insightragworker.cdc;

import com.hieulc.insightragworker.config.properties.DebeziumProperties;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class DebeziumWorker implements SmartLifecycle {

    private boolean isRunning = false;
    private final DebeziumEngine<ChangeEvent<String, String>> engine;
    private final TaskExecutor taskExecutor;

    public DebeziumWorker(
            DebeziumProperties properties,
           @Qualifier("debeziumThreadExecutor")
           TaskExecutor taskExecutor,
            OutboxChangeConsumer consumer
    ) {
        this.engine = DebeziumEngine.create(Json.class)
                .using(properties.asProperties())
                .notifying(consumer)
                .build();
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void start() {
        log.info("Starting Debezium Engine background thread...");

        taskExecutor.execute(engine);
        isRunning = true;
    }

    @Override
    public void stop() {
        log.info("Shutting down Debezium Engine...");
        if(engine != null){
            try {
                engine.close();
            } catch (IOException e) {
               log.error("Fail to close Debezium. Offset might be corrupted", e);
            }
        }
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}
