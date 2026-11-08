package com.hieulc.insightragretrieval.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.hieulc.insightragretrieval.annotations.LoadTest;
import com.hieulc.insightragretrieval.config.RateLimitCacheConfig;
import com.hieulc.insightragretrieval.config.properties.RateLimitProperties;
import com.hieulc.insightragretrieval.service.security.RateLimitingService;
import io.github.bucket4j.ConsumptionProbe;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {RateLimitingService.class, RateLimitCacheConfig.class})
@EnableConfigurationProperties(RateLimitProperties.class)
@LoadTest
public class RateLimitingServiceConcurrencyTest {

  @Autowired private RateLimitingService rateLimitingService;

  private final String USER_ID = "user-test-id";
  private final int CAPACITY = 3;

  @Test
  void tryConsume_should_be_threadSafe_under_concurrent_loads() throws InterruptedException {
    int threads = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    // threads + 1 is for the main test thread to wait at the barrier
    CyclicBarrier barrier = new CyclicBarrier(threads + 1);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger rejectedCount = new AtomicInteger(0);

    for (int i = 0; i < threads; i++) {
      executor.submit(
          () -> {
            try {
              // wait for all the thread reach this line
              barrier.await();

              ConsumptionProbe probe = rateLimitingService.tryConsume(USER_ID);
              if (probe.isConsumed()) {
                successCount.incrementAndGet();
              } else {
                rejectedCount.incrementAndGet();
              }

            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              try {
                // wait the main thread
                barrier.await();
              } catch (Exception ignored) {
              }
            }
          });
    }

    try {
      // fires all the threads
      barrier.await();

      // waits for all the threads to hit the finally-block barrier
      barrier.await();
    } catch (Exception e) {
      fail("Concurrency barrier failed: " + e.getMessage());
    }

    assertEquals(CAPACITY, successCount.get());
    assertEquals(threads - CAPACITY, rejectedCount.get());

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.SECONDS);
  }
}
