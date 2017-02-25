package io.trane.future;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
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
    return future.get(1, TimeUnit.SECONDS);
  }

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Exception ex = new TestException();

  @After
  public void shutdownScheduler() {
    scheduler.shutdown();
  }

  /*** never ***/

  @Test
  public void never() {
    assertTrue(Future.never() instanceof NoFuture);
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
    return Tailrec.apply(() -> {
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
    return Tailrec.apply(() -> {
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
    assertEquals(new Integer(0), tailrecLoopDelayed(Future.value(20000)).get(10, TimeUnit.SECONDS));
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

  /*** emptyOptional ***/

  @Test
  public void emptyOptional() throws CheckedFutureException {
    Future<Optional<String>> future = Future.emptyOptional();
    assertFalse(get(future).isPresent());
  }

  /*** collect ***/

  @Test
  public void collectEmpty() {
    Future<List<String>> future = Future.collect(new ArrayList<>());
    assertEquals(Future.emptyList(), future);
  }

  @Test
  public void collectOne() throws CheckedFutureException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1)));
    Integer[] expected = { 1 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test
  public void collectTwo() throws CheckedFutureException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.value(2)));
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test
  public void collectSatisfiedFutures() throws CheckedFutureException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.value(2), Future.value(3)));
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = TestException.class)
  public void collectSatisfiedFuturesException() throws CheckedFutureException {
    Future<List<Integer>> future = Future
        .collect(Arrays.asList(Future.value(1), Future.exception(ex), Future.value(3)));
    get(future);
  }

  @Test
  public void collectPromises() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Promise<Integer> p3 = Promise.apply();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, p3));
    p1.setValue(1);
    p2.setValue(2);
    p3.setValue(3);
    Integer[] expected = { 1, 2, 3 };
    Object[] result = get(future).toArray();
    assertArrayEquals(expected, result);
  }

  @Test(expected = TestException.class)
  public void collectPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Promise<Integer> p3 = Promise.apply();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, p3));
    p1.setValue(1);
    p2.setException(ex);
    p3.setValue(3);
    get(future);
  }

  @Test
  public void collectMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = TestException.class)
  public void collectMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
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
      List<Promise<Integer>> promises = Stream.generate(() -> Promise.<Integer>apply()).limit(20000).collect(toList());
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
    AtomicReference<Throwable> p3Intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(p1Intr::set);
    Promise<Integer> p2 = Promise.apply(p2Intr::set);
    Promise<Integer> p3 = Promise.apply(p3Intr::set);

    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, p3));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
    assertEquals(ex, p3Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void collectTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
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
  public void joinOne() throws CheckedFutureException {
    Future<Integer> f = Future.value(1);
    assertEquals(f.voided(), Future.join(Arrays.asList(f)));
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
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = TestException.class)
  public void joinPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(ex);
    get(future);
  }

  @Test
  public void joinMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = TestException.class)
  public void joinMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(ex);
    get(future);
  }

  @Test
  public void joinConcurrentResults() throws CheckedFutureException {
    List<Promise<Integer>> promises = Stream.generate(() -> Promise.<Integer>apply()).limit(20000).collect(toList());
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
    Promise<Integer> p1 = Promise.apply(p1Intr::set);
    Promise<Integer> p2 = Promise.apply(p2Intr::set);

    Future<Void> future = Future.join(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void joinTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    future.get(10, TimeUnit.MILLISECONDS);
  }

  /*** selectIndex **/

  @Test(expected = IllegalArgumentException.class)
  public void selectIndexEmpty() throws CheckedFutureException {
    get(Future.selectIndex(new ArrayList<>()));
  }

  @Test
  public void selectIndexOne() throws CheckedFutureException {
    Future<Integer> f = Future.selectIndex(Arrays.asList(Future.value(1)));
    assertEquals(new Integer(0), get(f));
  }

  @Test
  public void selectIndexSatisfiedFutures() throws CheckedFutureException {
    Future<Integer> future = Future.selectIndex(Arrays.asList(Future.value(1), Future.value(2)));
    assertEquals(new Integer(0), get(future));
  }

  @Test
  public void selectIndexPromises() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    p2.setValue(2);
    assertEquals(new Integer(1), get(future));
  }

  @Test
  public void selectIndexPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    p1.setException(new Throwable());
    assertEquals(new Integer(0), get(future));
  }

  @Test
  public void selectIndexMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    assertEquals(new Integer(2), get(future));
  }

  @Test
  public void selectIndexMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    assertEquals(new Integer(2), get(future));
  }

  @Test
  public void selectIndexInterrupts() {
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(p1Intr::set);
    Promise<Integer> p2 = Promise.apply(p2Intr::set);

    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void selectIndexTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    future.get(10, TimeUnit.MILLISECONDS);
  }
  
  /*** firstCompletedOf **/

  @Test(expected = IllegalArgumentException.class)
  public void firstCompletedOfEmpty() throws CheckedFutureException {
    get(Future.firstCompletedOf(new ArrayList<>()));
  }

  @Test
  public void firstCompletedOfOne() throws CheckedFutureException {
    Future<Integer> f = Future.firstCompletedOf(Arrays.asList(Future.value(1)));
    assertEquals(new Integer(1), get(f));
  }

  @Test
  public void firstCompletedOfSatisfiedFutures() throws CheckedFutureException {
    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(Future.value(1), Future.value(2)));
    assertEquals(new Integer(1), get(future));
  }

  @Test
  public void firstCompletedOfPromises() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(p1, p2));
    p2.setValue(2);
    assertEquals(new Integer(2), get(future));
  }

  @Test(expected = TestException.class)
  public void firstCompletedOfPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(p1, p2));
    p1.setException(new TestException());
    get(future);
  }

  @Test
  public void firstCompletedOfMixed() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    assertEquals(new Integer(3), get(future));
  }

  @Test
  public void firstCompletedOfMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    assertEquals(new Integer(3), get(future));
  }

  @Test
  public void firstCompletedOfInterrupts() {
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(p1Intr::set);
    Promise<Integer> p2 = Promise.apply(p2Intr::set);

    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void firstCompletedOfTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> future = Future.firstCompletedOf(Arrays.asList(p1, p2));
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
    Future<Integer> f = (Promise.<Integer>apply()).within(1, TimeUnit.MILLISECONDS, scheduler);
    get(f);
  }

  @Test
  public void withinDefaultExceptionSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.<Integer>apply();
    Future<Integer> f = p.within(10, TimeUnit.MILLISECONDS, scheduler);
    p.setValue(1);
    assertEquals(new Integer(1), get(f));
  }
}
