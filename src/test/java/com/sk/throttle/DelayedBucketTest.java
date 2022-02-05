package com.sk.throttle;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log4j2
class DelayedBucketTest {
  ThrottledBucket<String> throttledBucket;

  @BeforeEach
  void setUp() {
  }

  @Test
  void testReset() {
    throttledBucket = new ThrottledBucket<>(1, 6000);
    throttledBucket.reset();
  }

  @Test
  void testTryConsume() {
    throttledBucket = new ThrottledBucket<>(1, 100);
    boolean result = throttledBucket.tryConsume("taskString");
    Assertions.assertEquals(true, result);
  }

  @Test
  void testTryConsumeMakeOnlyOneGetProcessed() {
    throttledBucket = new ThrottledBucket<>(1, 6000);
    int slots = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(slots);

    for (int i = 0; i < 10; i++) {
      int finalI = i;
      executorService.submit(
          () -> {
            if (throttledBucket.tryConsume(String.valueOf(finalI))) {
              log.info("Pass Through Throttle " + finalI );
            } else {
              log.info("Placed in bucket to be resent later" + throttledBucket.retrieve());
            }
          });
    }
    try {
      if(executorService.awaitTermination(1000, TimeUnit.MICROSECONDS))
         executorService.shutdown();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Assertions.assertFalse(throttledBucket.retrieve().isEmpty());
  }

  @Test
  void testTryConsumeMakeGetAllProcessed() {
    throttledBucket = new ThrottledBucket<>(10, 6000);
    int slots = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(slots);

    for (int i = 0; i < 10; i++) {
      int finalI = i;
      executorService.submit(
              () -> {
                if (throttledBucket.tryConsume(String.valueOf(finalI))) {
                  log.info("Pass Through Throttle " + finalI );
                } else {
                  log.info("Placed in bucket to be resent later" + throttledBucket.retrieve());
                }
              });
    }
    try {
      if(executorService.awaitTermination(1000, TimeUnit.MICROSECONDS))
        executorService.shutdown();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Assertions.assertTrue(throttledBucket.retrieve().isEmpty());
  }
}
