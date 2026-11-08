package com.hieulc.insightragworker.infrastructure.cdc.debezium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class DebeziumWorkerTest {

  @Mock private DebeziumEngine<ChangeEvent<String, String>> engine;
  @Mock private TaskExecutor taskExecutor;

  @InjectMocks private DebeziumWorker worker;

  @BeforeEach
  void setUp() {
    assertThat(worker.isRunning()).isFalse();
  }

  @Test
  void should_execute_engine_and_sets_running_state_on_start() {
    worker.start();

    verify(taskExecutor).execute(engine);
    assertThat(worker.isRunning()).isTrue();
  }

  @Test
  void should_close_engine_and_clear_running_state_on_stop() throws IOException {
    worker.start();
    assertThat(worker.isRunning()).isTrue();
    worker.stop();
    verify(engine).close();
    assertThat(worker.isRunning()).isFalse();
  }

  @Test
  void should_handle_io_exception_gracefully_during_shutdown() throws IOException {
    worker.start();
    doThrow(new IOException("Disk offline")).when(engine).close();

    assertThatCode(() -> worker.stop()).doesNotThrowAnyException();
    assertThat(worker.isRunning()).isFalse();
  }
}
