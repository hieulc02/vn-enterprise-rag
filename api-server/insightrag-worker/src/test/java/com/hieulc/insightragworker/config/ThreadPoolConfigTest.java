package com.hieulc.insightragworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * This test is about `Codifying Architectural Invariants` which serves as an executable tripwire to
 * prevent future me from breaking the CDC contract
 */
class ThreadPoolConfigTest {

  @Test
  void debeziumThreadExecutor_executors_must_be_single_threaded() {

    ThreadPoolConfig config = new ThreadPoolConfig();

    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.debeziumThreadExecutor();

    assertThat(executor.getCorePoolSize())
        .as("Debezium must run on exactly 1 core thread to prevent offset corruption")
        .isEqualTo(1);

    assertThat(executor.getMaxPoolSize())
        .as("Debezium must never scale beyond 1 thread")
        .isEqualTo(1);

    // A queue capacity of 0 forces SynchronousQueue behavior
    // This ensures that if a second start request comes in while the engine is running
    // it is immediately rejected rather than queued up.
    // Since Debezium is a stateful, singleton background daemon. So it is impossible
    // to have two CDC engines running on the same database in the same worker
    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(0);

    // Rejection policy
    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
        .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);

    // Observability
    assertThat(executor.getThreadNamePrefix()).isEqualTo("debezium.worker");
  }
}
