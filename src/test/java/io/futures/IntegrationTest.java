package io.futures;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Test;

import com.twitter.util.CountDownLatch;

public class IntegrationTest {

  private static final Random random = new Random(1);
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

  @AfterClass
  public static void shutdownScheduler() {
    scheduler.shutdown();
  }

  @Test
  public void integrationTest() throws CheckedFutureException {
    Future<?> f = Future.value(1);
    for (int i = 0; i < 5000; i++) {

      switch (random.nextInt(11)) {

      case 1:
        f = f.flatMap(v -> Future.VOID);
        break;

      case 2:
        f = f.flatMap(v -> Future.apply(() -> 1));
        break;

      case 3:
        f = f.flatMap(v -> Future.apply(() -> 1 / 0));
        break;

      case 4:
        f = f.flatMap(v -> Future.value(2));
        break;

      case 5:
        break;

      case 6:
        break;

      case 7:
        break;

      case 8:
        break;

      case 9:
        break;

      case 10:
        break;

      case 11:
        break;
      }
    }
    f.join(20, TimeUnit.DAYS);
  }

  // f = f.delayed(random.nextInt(5) + 1, TimeUnit.MILLISECONDS, scheduler);
}
