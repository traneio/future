package io.futures;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Test;

public class IntegrationTest {

  private static final Random random = new Random(1);
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

  @AfterClass
  public static void shutdownScheduler() {
    scheduler.shutdown();
  }

  private Future<Integer> gen(int depth) {
    if (depth <= 1)
      return Future.value(1);

    switch (random.nextInt(9)) {

    case 0:
      return Future.collect(Arrays.asList(gen(depth / 2), gen(depth / 2))).map(l -> l.stream().mapToInt(v -> v).sum());
    case 1:
      return Future.join(Arrays.asList(gen(depth / 2), gen(depth / 2))).map(v -> 1);
    case 2:
      return Future.selectIndex(Arrays.asList(gen(depth / 2), gen(depth / 2)));
    case 3:
      return gen(depth / 2).flatMap(v -> gen(depth / 2));
    case 4:
      return gen(depth - 1).delayed(random.nextInt(5), TimeUnit.MILLISECONDS, scheduler);
    case 5:
      return gen(depth - 1).ensure(() -> {
      });
    case 6:
      return gen(depth - 1).handle(ex -> 1);
    case 7:
      Promise<Integer> p = Promise.apply();
      gen(depth - 1).proxyTo(p);
      return p;
    case 8:
      return gen(depth - 1).within(1, TimeUnit.SECONDS, scheduler);
    default:
      throw new IllegalStateException();
    }
  }

  @Test
  public void integrationTest() throws CheckedFutureException {
    Future<Integer> gen = gen(200);
    gen.join(20, TimeUnit.DAYS);
  }
}
