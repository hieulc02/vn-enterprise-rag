package com.hieulc.insightragretrieval.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "taskExecutor")
  public Executor taskExecutor(
      @Value("${spring.lifecycle.timeout-per-shutdown-phase:30s}") Duration shutdownTimeout) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(10); // Aligned Hikari connection pool
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("TokenLogger-");

    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(
        (int) shutdownTimeout.toSeconds()); // less than or equal to the spring.lifecycle timeout
    executor.initialize();
    return executor;
  }

  @Bean(name = "virtualExecutor")
  public AsyncTaskExecutor virtualExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
