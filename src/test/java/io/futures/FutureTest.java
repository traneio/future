package io.futures;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Test;

public class FutureTest {

  private <T> T get(Future<T> future) throws InterruptedException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  /*** apply ***/

  @Test
  public void applyValue() throws InterruptedException {
    Integer value = 1;
    Future<Integer> future = Future.apply(() -> value);
    assertEquals(value, get(future));
  }

  @Test(expected = ArithmeticException.class)
  public void applyException() throws InterruptedException {
    Future<Integer> future = Future.apply(() -> 1 / 0);
    get(future);
  }

  /*** value ***/

  @Test
  public void value() throws InterruptedException {
    Integer value = 1;
    Future<Integer> future = Future.value(value);
    assertEquals(value, get(future));
  }

  /*** exception ***/

  @Test(expected = RuntimeException.class)
  public void exception() throws InterruptedException {
    RuntimeException ex = new RuntimeException();
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
  public void tailrec() throws InterruptedException {
    assertEquals(new Integer(10), get(tailrecLoop(200000)));
  }

  Future<Integer> nonTailrecLoop(int i) {
    if (i == 0)
      return Future.value(10);
    else
      return nonTailrecLoop(i - 1);
  }

  @Test(expected = StackOverflowError.class)
  public void nonTailrec() throws InterruptedException {
    nonTailrecLoop(200000);
  }

  /*** emptyList ***/

  @Test
  public void emptyList() throws InterruptedException {
    Future<List<String>> future = Future.emptyList();
    assertTrue(get(future).isEmpty());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void emptyListIsUnmodifiable() throws InterruptedException {
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
  public void collectSatisfiedFutures() throws InterruptedException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.value(2)));
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = RuntimeException.class)
  public void collectSatisfiedFuturesException() throws InterruptedException {
    Future<List<Integer>> future = Future
        .collect(Arrays.asList(Future.value(1), Future.exception(new RuntimeException())));
    get(future);
  }

  @Test
  public void collectPromises() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = RuntimeException.class)
  public void collectPromisesException() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(new RuntimeException());
    get(future);
  }

  @Test
  public void collectMixed() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = RuntimeException.class)
  public void collectMixedException() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new RuntimeException());
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test
  public void collectConcurrentResults() throws InterruptedException {
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
      List<Integer> result = future.get(10, TimeUnit.MILLISECONDS);
      assertArrayEquals(expected.toArray(), result.toArray());
    } finally {
      ex.shutdown();
    }
  }

  @Test
  public void collectInterrupts() {
    RuntimeException ex = new RuntimeException();
    AtomicReference<Exception> p1Intr = new AtomicReference<>();
    AtomicReference<Exception> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void collectTimeout() throws InterruptedException {
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
  public void joinSatisfiedFutures() throws InterruptedException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.value(2)));
    get(future);
  }

  @Test(expected = RuntimeException.class)
  public void joinSatisfiedFuturesException() throws InterruptedException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.exception(new RuntimeException())));
    get(future);
  }

  @Test
  public void joinPromises() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = RuntimeException.class)
  public void joinPromisesException() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(new RuntimeException());
    get(future);
  }

  @Test
  public void joinMixed() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = RuntimeException.class)
  public void joinMixedException() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new RuntimeException());
    get(future);
  }

  @Test
  public void joinConcurrentResults() throws InterruptedException {
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
    RuntimeException ex = new RuntimeException();
    AtomicReference<Exception> p1Intr = new AtomicReference<>();
    AtomicReference<Exception> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<Void> future = Future.join(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void joinTimeout() throws InterruptedException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    future.get(10, TimeUnit.MILLISECONDS);
  }

  /*** delayed ***/

  @Test
  public void delayed() throws InterruptedException {
    Timer timer = new Timer();
    Future<Integer> future = Future.value(1);
    long delay = 10;
    long start = System.currentTimeMillis();
    int result = future.delayed(delay, timer).get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  @Test
  public void delayedInterrupt() {
    Timer timer = new Timer();
    RuntimeException ex = new RuntimeException();
    AtomicReference<Exception> intr = new AtomicReference<>();
    Promise<Integer> p = new Promise<>(intr::set);

    Future<Integer> future = p.delayed(10, timer);

    future.raise(ex);

    assertEquals(ex, intr.get());
  }
}
