package io.futures;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;

public class IntegrationTest {

  private static final Random random = new Random(1);
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  @After
  public static void shutdownScheduler() {
    scheduler.shutdown();
  }

  // @Test
  public static void main(String[] args) throws CheckedFutureException {
    Future<Integer> f = Future.value(1);
    ArrayList<Future<Integer>> l = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      int j = random.nextInt(3);
      if (j == 1)
        f = f.delayed(10, TimeUnit.MILLISECONDS, scheduler);
      l.add(f);
//      else if (j == 2)
//        f = f.map(v -> v + 1);
//      else
//        f = f.flatMap(v -> Future.value(v + 1));
    }
    f.get(20, TimeUnit.SECONDS);
    scheduler.shutdown();
  }
}
