package io.futures;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

public class IntegrationTest {

  private final Random random = new Random(1);
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

  @After
  public void shutdownScheduler() {
    scheduler.shutdown();
  }

//  @Test
  public void test() throws CheckedFutureException {
    Future<Integer> f = Future.value(1);
    for (int i = 0; i < 40; i++) {
      int j = random.nextInt(3);
      if (j == 1)
        f = f.delayed(10, TimeUnit.MILLISECONDS, scheduler);
      else if (j == 2)
        f = f.map(v -> v + 1);
      else
        f = f.flatMap(v -> Future.value(v + 1));
    }
    f.get(20, TimeUnit.SECONDS);
  }
}
