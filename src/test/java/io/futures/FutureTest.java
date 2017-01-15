package io.futures;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Test;

public class FutureTest {

  private <T> T get(Future<T> future) throws ExecutionException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  /*** apply ***/

  @Test
  public void applyValue() throws ExecutionException {
    Integer value = 1;
    Future<Integer> future = Future.apply(() -> value);
    assertEquals(value, get(future));
  }

  @Test(expected = ExecutionException.class)
  public void applyException() throws ExecutionException {
    Future<Integer> future = Future.apply(() -> 1 / 0);
    get(future);
  }

  /*** value ***/

  @Test
  public void value() throws ExecutionException {
    Integer value = 1;
    Future<Integer> future = Future.value(value);
    assertEquals(value, get(future));
  }

  /*** exception ***/

  @Test(expected = Throwable.class)
  public void exception() throws ExecutionException {
    Throwable ex = new Throwable();
    Future<Integer> future = Future.exception(ex);
    get(future);
  }

  /*** tailrec ***/

  Future<Integer> tailrecLoop(int i) {
    return Future.tailrec(() -> {
      if (i == 0)
        return Future.value(10);
      else
        return tailrecLoop(i - 1);
    });
  }

  @Test
  public void tailrec() throws ExecutionException {
    assertEquals(new Integer(10), get(tailrecLoop(200000)));
  }

  Future<Integer> nonTailrecLoop(int i) {
    if (i == 0)
      return Future.value(10);
    else
      return nonTailrecLoop(i - 1);
  }

  @Test(expected = StackOverflowError.class)
  public void nonTailrec() throws ExecutionException {
    nonTailrecLoop(200000);
  }

  /*** emptyList ***/

  @Test
  public void emptyList() throws ExecutionException {
    Future<List<String>> future = Future.emptyList();
    assertTrue(get(future).isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void emptyListIsUnmodifiable() throws ExecutionException {
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
  public void collectSatisfiedFutures() throws ExecutionException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.value(2)));
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = Throwable.class)
  public void collectSatisfiedFuturesException() throws ExecutionException {
    Future<List<Integer>> future = Future
        .collect(Arrays.asList(Future.value(1), Future.exception(new Throwable())));
    get(future);
  }

  @Test
  public void collectPromises() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = Throwable.class)
  public void collectPromisesException() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(new Throwable());
    get(future);
  }

  @Test
  public void collectMixed() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = Throwable.class)
  public void collectMixedException() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test
  public void collectConcurrentResults() throws ExecutionException {
    List<Promise<Integer>> promises = Stream.generate(() -> new Promise<Integer>()).limit(20000).collect(toList());
    ExecutorService ex = Executors.newFixedThreadPool(10);
    try {
      Future<List<Integer>> future = Future.collect(promises);
      for (Promise<Integer> p : promises) {
        ex.submit(() -> {
          p.setValue(p.hashCode());
        });
      }
      List<Integer> expected = promises.stream().map(p -> p.hashCode()).collect(toList());
      List<Integer> result = future.get(100, TimeUnit.MILLISECONDS);
      assertArrayEquals(expected.toArray(), result.toArray());
    } finally {
      ex.shutdown();
    }
  }

  @Test
  public void collectInterrupts() {
    Throwable ex = new Throwable();
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = ExecutionException.class)
  public void collectTimeout() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
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
  public void joinSatisfiedFutures() throws ExecutionException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.value(2)));
    get(future);
  }

  @Test(expected = Throwable.class)
  public void joinSatisfiedFuturesException() throws ExecutionException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.exception(new Throwable())));
    get(future);
  }

  @Test
  public void joinPromises() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = Throwable.class)
  public void joinPromisesException() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(new Throwable());
    get(future);
  }

  @Test
  public void joinMixed() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = Throwable.class)
  public void joinMixedException() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    get(future);
  }

  @Test
  public void joinConcurrentResults() throws ExecutionException {
    List<Promise<Integer>> promises = Stream.generate(() -> new Promise<Integer>()).limit(20000).collect(toList());
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
    Throwable ex = new Throwable();
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<Void> future = Future.join(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = ExecutionException.class)
  public void joinTimeout() throws ExecutionException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    future.get(10, TimeUnit.MILLISECONDS);
  }

  /*** delayed ***/

  @Test
  public void delayed() throws ExecutionException {
    Timer timer = new Timer();
    Future<Integer> future = Future.value(1);
    long delay = 10;
    long start = System.currentTimeMillis();
    int result = future.delayed(delay, TimeUnit.MILLISECONDS, timer).get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  @Test
  public void delayedInterrupt() {
    Timer timer = new Timer();
    Throwable ex = new Throwable();
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = new Promise<>(intr::set);

    Future<Integer> future = p.delayed(10, TimeUnit.MILLISECONDS, timer);

    future.raise(ex);

    assertEquals(ex, intr.get());
  }
}
