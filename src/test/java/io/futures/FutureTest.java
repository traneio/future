package io.futures;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Test;

public class FutureTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  Timer timer = new Timer();
  Exception ex = new TestException();

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

  @Test(expected = Throwable.class)
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

  Future<Integer> tailrecLoop(int i) {
    return Future.tailrec(() -> {
      if (i == 0)
        return Future.value(10);
      else
        return tailrecLoop(i - 1);
    });
  }

  @Test
  public void tailrec() throws CheckedFutureException {
    assertEquals(new Integer(10), get(tailrecLoop(200000)));
  }

  Future<Integer> nonTailrecLoop(int i) {
    if (i == 0)
      return Future.value(10);
    else
      return nonTailrecLoop(i - 1);
  }

  @Test(expected = StackOverflowError.class)
  public void nonTailrec() throws CheckedFutureException {
    nonTailrecLoop(200000);
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

  @Test(expected = Throwable.class)
  public void collectSatisfiedFuturesException() throws CheckedFutureException {
    Future<List<Integer>> future = Future.collect(Arrays.asList(Future.value(1), Future.exception(new Throwable())));
    get(future);
  }

  @Test
  public void collectPromises() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = Throwable.class)
  public void collectPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(new Throwable());
    get(future);
  }

  @Test
  public void collectMixed() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test(expected = Throwable.class)
  public void collectMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    Integer[] expected = { 1, 2, 3 };
    assertArrayEquals(expected, get(future).toArray());
  }

  @Test
  public void collectConcurrentResults() throws CheckedFutureException {
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
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<List<Integer>> future = Future.collect(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void collectTimeout() throws CheckedFutureException {
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
  public void joinSatisfiedFutures() throws CheckedFutureException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.value(2)));
    get(future);
  }

  @Test(expected = Throwable.class)
  public void joinSatisfiedFuturesException() throws CheckedFutureException {
    Future<Void> future = Future.join(Arrays.asList(Future.value(1), Future.exception(new Throwable())));
    get(future);
  }

  @Test
  public void joinPromises() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = Throwable.class)
  public void joinPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2));
    p1.setValue(1);
    p2.setException(new Throwable());
    get(future);
  }

  @Test
  public void joinMixed() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    get(future);
  }

  @Test(expected = Throwable.class)
  public void joinMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Void> future = Future.join(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    get(future);
  }

  @Test
  public void joinConcurrentResults() throws CheckedFutureException {
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
    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<Void> future = Future.join(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void joinTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
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
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    p2.setValue(2);
    assertEquals(new Integer(1), get(future));
  }

  @Test
  public void selectIndexPromisesException() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));
    p1.setException(new Throwable());
    assertEquals(new Integer(0), get(future));
  }

  @Test
  public void selectIndexMixed() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setValue(2);
    assertEquals(new Integer(2), get(future));
  }

  @Test
  public void selectIndexMixedException() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2, Future.value(3)));
    p1.setValue(1);
    p2.setException(new Throwable());
    assertEquals(new Integer(2), get(future));
  }

  @Test
  public void selectIndexInterrupts() {

    AtomicReference<Throwable> p1Intr = new AtomicReference<>();
    AtomicReference<Throwable> p2Intr = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(p1Intr::set);
    Promise<Integer> p2 = new Promise<>(p2Intr::set);

    Future<Integer> future = Future.selectIndex(Arrays.asList(p1, p2));

    future.raise(ex);

    assertEquals(ex, p1Intr.get());
    assertEquals(ex, p2Intr.get());
  }

  @Test(expected = TimeoutException.class)
  public void selectIndexTimeout() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
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

  /*** proxyTo ***/

  @Test(expected = IllegalStateException.class)
  public void proxyToSatisfied() {
    Promise<Integer> p = new Promise<>();
    p.setValue(2);
    Future.value(1).proxyTo(p);
  }

  @Test
  public void proxyToSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future.value(1).proxyTo(p);
    assertEquals(new Integer(1), get(p));
  }

  @Test(expected = TestException.class)
  public void proxyToFailure() throws CheckedFutureException {

    Promise<Integer> p = new Promise<>();
    Future.<Integer>exception(ex).proxyTo(p);
    get(p);
  }

  /*** voided ***/

  @Test
  public void voidedSuccess() throws CheckedFutureException {
    Future<Void> future = Future.value(1).voided();
    assertNull(get(future));
  }

  @Test(expected = TestException.class)
  public void voidedFailure() throws CheckedFutureException {
    Future<Void> future = Future.exception(ex).voided();
    get(future);
  }

  /*** within (default exception) ***/
  
  @Test
  public void withinMaxLongWait() {
    Future<Integer> future = Future.value(1);
    assertEquals(future, future.within(Long.MAX_VALUE, TimeUnit.MILLISECONDS, timer));
  }
  
  @Test
  public void withinMaxLongWaitPromise() {
    Future<Integer> future = new Promise<>();
    assertEquals(future, future.within(Long.MAX_VALUE, TimeUnit.MILLISECONDS, timer));
  }

  @Test
  public void withinSatisfiedFutureSuccess() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).within(1, TimeUnit.MILLISECONDS, timer);
    assertEquals(new Integer(1), get(future));
  }

  @Test(expected = RuntimeException.class)
  public void withinSatisfiedFutureFailure() throws CheckedFutureException {
    Future<Integer> future = Future.<Integer>exception(ex).within(1, TimeUnit.MILLISECONDS, timer);
    get(future);
  }

  @Test
  public void withinPromiseSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, timer);
    p.setValue(1);
    assertEquals(new Integer(1), get(future));
  }

  @Test(expected = TestException.class)
  public void withinPromiseFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, timer);
    p.setException(ex);
    get(future);
  }

  @Test(expected = TimeoutException.class)
  public void withinPromiseTimeout() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, timer);
    get(future);
  }

  /*** within (custom exception) ***/

  @Test
  public void withinCustomExceptionMaxLongWaitSatisfiedFuture() {
    Future<Integer> future = Future.value(1);
    assertEquals(future, future.within(Long.MAX_VALUE, TimeUnit.MILLISECONDS, timer, ex));
  }
  
  @Test
  public void withinCustomExceptionMaxLongWaitPromise() {
    Future<Integer> future = new Promise<>();
    assertEquals(future, future.within(Long.MAX_VALUE, TimeUnit.MILLISECONDS, timer, ex));
  }
  
  @Test
  public void withinCustomExceptionSatisfiedFutureSuccess() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).within(1, TimeUnit.MILLISECONDS, timer, ex);
    assertEquals(new Integer(1), get(future));
  }

  @Test(expected = TestException.class)
  public void withinCustomExceptionSatisfiedFutureFailure() throws CheckedFutureException {
    Future<Integer> future = Future.<Integer>exception(ex).within(1, TimeUnit.MILLISECONDS, timer, ex);
    get(future);
  }

  @Test
  public void withinCustomExceptionPromiseSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, timer, ex);
    p.setValue(1);
    assertEquals(new Integer(1), get(future));
  }

  @Test(expected = TestException.class)
  public void withinCustomExceptionPromiseFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, timer, ex);
    p.setException(ex);
    get(future);
  }

  @Test(expected = TimeoutException.class)
  public void withinCustomExceptionPromiseTimeout() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, timer, ex);
    get(future);
  }

  /*** delayed ***/

  @Test
  public void delayed() throws CheckedFutureException {
    Future<Integer> future = Future.value(1);
    long delay = 10;
    long start = System.currentTimeMillis();
    int result = future.delayed(delay, TimeUnit.MILLISECONDS, timer).get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  @Test
  public void delayedInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = new Promise<>(intr::set);

    Future<Integer> future = p.delayed(10, TimeUnit.MILLISECONDS, timer);

    future.raise(ex);

    assertEquals(ex, intr.get());
  }
}
