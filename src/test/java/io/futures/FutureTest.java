package io.futures;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;

public class FutureTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(100, TimeUnit.MILLISECONDS);
  }

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Exception ex = new TestException();

  @After
  public void shutdownScheduler() {
    scheduler.shutdown();
  }

  /*** apply ***/

  @Test
  public void applyValue() throws CheckedFutureException {
    Integer value = 1;
    Future<Integer> future = Future.apply(() -> value);
    assertEquals(value, get(future));
  }

  @Test(expected = ArithmeticException.class)
  public void applyException() throws CheckedFutureException {
    Future<Integer> future = Future.apply(() -> 1 / 0);
    get(future);
  }

  /*** value ***/

  @Test
  public void value() throws CheckedFutureException {
    Integer value = 1;
    Future<Integer> future = Future.value(value);
    assertEquals(value, get(future));
  }

  /*** exception ***/

  @Test(expected = TestException.class)
  public void exception() throws CheckedFutureException {

    Future<Integer> future = Future.exception(ex);
    get(future);
  }

  /*** flatten ***/

  @Test
  public void flatten() throws CheckedFutureException {
    Future<Future<Integer>> future = Future.value(Future.value(1));
    assertEquals(get(Future.flatten(future)), get(future.flatMap(f -> f)));
  }

  /*** tailrec ***/

  Future<Integer> tailrecLoop(Future<Integer> f) {
    return Future.tailrec(() -> {
      return f.flatMap(i -> {
        if (i == 0)
          return Future.value(0);
        else
          return tailrecLoop(Future.value(i - 1));
      });
    });
  }

  @Test
  public void tailrec() throws CheckedFutureException {
    assertEquals(new Integer(0), get(tailrecLoop(Future.value(20000))));
  }

  Future<Integer> tailrecLoopDelayed(Future<Integer> f) {
    return Future.tailrec(() -> {
      return f.flatMap(i -> {
        if (i == 0)
          return Future.value(0);
        else
          return tailrecLoopDelayed(Future.value(i - 1).delayed(1, TimeUnit.NANOSECONDS, scheduler));
      });
    });
  }

  @Test
  public void tailrecDelayed() throws CheckedFutureException {
    assertEquals(new Integer(0), get(tailrecLoopDelayed(Future.value(20000))));
  }

  Future<Integer> nonTailrecLoop(Future<Integer> f) {
    return f.flatMap(i -> {
      if (i == 0)
        return Future.value(0);
      else
        return nonTailrecLoop(Future.value(i - 1));
    });
  }

  @Test(expected = StackOverflowError.class)
  public void nonTailrec() throws CheckedFutureException {
    assertEquals(new Integer(0), get(nonTailrecLoop(Future.value(20000))));
  }

  Future<Integer> nonTailrecLoopDelayed(Future<Integer> f) {
    return f.flatMap(i -> {
      if (i == 0)
        return Future.value(0);
      else
        return nonTailrecLoop(Future.value(i - 1).delayed(1, TimeUnit.NANOSECONDS, scheduler));
    });
  }

  @Test(expected = StackOverflowError.class)
  public void nonTailrecDelayed() throws CheckedFutureException {
    assertEquals(new Integer(0), get(nonTailrecLoop(Future.value(20000))));
  }

  /*** emptyList ***/

  @Test
  public void emptyList() throws CheckedFutureException {
    Future<List<String>> future = Future.emptyList();
    assertTrue(get(future).isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void emptyListIsUnmodifiable() throws CheckedFutureException {
    Future<List<String>> future = Future.emptyList();
    get(future).add("s");
  }

  /*** collect ***/

  @Test
  public void collectEmpty() {
    Future<List<String>> future = Future.collect(new ArrayList<>());
    assertEquals(Future.emptyList(), future);
  }

  @Test
  public void collectSatisfiedFutures() throws CheckedFutureException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.value(2)));
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = TestException.class)
  public void collectSatisfiedFuturesException() throws CheckedFutureException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.exception(ex)));
    get(future);
  }

  @Test
  public void collectPromises() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = TestException.class)
  public void collectPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(ex);
    get(future);
  }

  @Test
  public void collectMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = TestException.class)
  public void collectMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(ex);
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test
  public void collectConcurrentResults() throws CheckedFutureException {
    ExecutorService ex = Executors.newFixedThreadPool(10);
    try {
      List<Promise<Integer>> promises = Stream.generate(() -> Future.<Integer>promise()).limit(20000).collect(toList());
      AtomicBoolean start = new AtomicBoolean();
      Future<List<Integer>> future = Future.collect(promises);
      for (Promise<Integer> p : promises) {
        ex.submit(() -> {
          while (true) {
            if (start.get())
              break;
          }
          p.setValue(p.hashCode());
        });
      }
      start.set(true);
      List<Integer> expected = promises.stream().map(p -> p.hashCode()).collect(toList());
      List<Integer> result = future.get(100, TimeUnit.MILLISECONDS);
      assertArrayEquals(expected.toArray(), result.toArray());
    } finally {
      ex.shutdown();
    }
  }

  @Test
  public void collectInterrupts() {
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = Future.promise(p1Intr::set);
    Promise<Integer> p2 = Future.promise(p2Intr::set);

    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void collectTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    future.get(10, TimeUnit.MILLISECONDS);
  }

  /*** join ***/

  @Test
  public void joinEmpty() {
    Future<Void> future = Future.join(new ArrayList<>());
    assertEquals(Future.VOID, future);
  }

  @Test
  public void joinSatisfiedFutures() throws CheckedFutureException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.value(2)));
    get(future);
  }

  @Test(expected = TestException.class)
  public void joinSatisfiedFuturesException() throws CheckedFutureException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.exception(ex)));
    get(future);
  }

  @Test
  public void joinPromises() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = TestException.class)
  public void joinPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(ex);
    get(future);
  }

  @Test
  public void joinMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = TestException.class)
  public void joinMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(ex);
    get(future);
  }

  @Test
  public void joinConcurrentResults() throws CheckedFutureException {
    List<Promise<Integer>> promises = Stream.generate(() -> Future.<Integer>promise()).limit(20000).collect(toList());
    ExecutorService ex = Executors.newFixedThreadPool(10);
    try {
      Future<Void> future = Future.join(promises);
      for (Promise<Integer> p : promises) {
        ex.submit(() -> {
          p.setValue(p.hashCode());
        });
      }
      future.get(1, TimeUnit.SECONDS);
    } finally {
      ex.shutdown();
    }
  }

  @Test
  public void joinInterrupts() {
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = Future.promise(p1Intr::set);
    Promise<Integer> p2 = Future.promise(p2Intr::set);

    Future<Void> future = Future.join(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void joinTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    future.get(10, TimeUnit.MILLISECONDS);
  }

  /*** selectIndex **/

  @Test(expected = IllegalArgumentException.class)
  public void selectIndexEmpty() {
    Future.selectIndex(new ArrayList<>());
  }

  @Test
  public void selectIndexSatisfiedFutures() throws CheckedFutureException {
    Future<Integer> future = Future.selectIndex(Arrays.asList(Future.value(1), Future.value(2)));
    assertEquals(new Integer(0), get(future));
  }

  @Test
  public void selectIndexPromises() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    p2.setValue(2);
    assertEquals(new Integer(1), get(future));
  }

  @Test
  public void selectIndexPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    p1.setException(new Throwable());
    assertEquals(new Integer(0), get(future));
  }

  @Test
  public void selectIndexMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    assertEquals(new Integer(2), get(future));
  }

  @Test
  public void selectIndexMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    assertEquals(new Integer(2), get(future));
  }

  @Test
  public void selectIndexInterrupts() {

    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = Future.promise(p1Intr::set);
    Promise<Integer> p2 = Future.promise(p2Intr::set);

    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void selectIndexTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Future.promise();
    Promise<Integer> p2 = Future.promise();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    future.get(10, TimeUnit.MILLISECONDS);
  }

  /*** whileDo ***/

  @Test
  public void whileDo() throws CheckedFutureException {
    int iterations = 200000;
    AtomicInteger count = new AtomicInteger(iterations);
    AtomicInteger callCount = new AtomicInteger(0);
    Future<Void> future = Future.whileDo(() -> count.decrementAndGet() >= 0,
        () -> Future.apply(() -> callCount.incrementAndGet()));
    get(future);
    assertEquals(-1, count.get());
    assertEquals(iterations, callCount.get());
  }

  /*** parallel ***/

  @Test
  public void parallel() {
    List<Future<Integer>> list = Arrays.asList(Future.value(1), Future.value(2));
    Iterator<Future<Integer>> iterator = list.iterator();
    List<Future<Integer>> result = Future.parallel(2, () -> iterator.next());
    assertArrayEquals(list.toArray(), result.toArray());
  }

  /*** within ***/

  @Test(expected = TimeoutException.class)
  public void withinDefaultExceptionFailure() throws CheckedFutureException {
    Future<Integer> f = (Future.<Integer>promise()).within(1, TimeUnit.MILLISECONDS, scheduler);
    get(f);
  }

  @Test
  public void withinDefaultExceptionSuccess() throws CheckedFutureException {
    Promise<Integer> p = Future.<Integer>promise();
    Future<Integer> f = p.within(10, TimeUnit.MILLISECONDS, scheduler);
    p.setValue(1);
    assertEquals(new Integer(1), get(f));
  }
}
