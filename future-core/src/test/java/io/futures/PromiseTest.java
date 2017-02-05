package io.futures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

public class PromiseTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Exception ex = new TestException();

  @After
  public void shutdownScheduler() {
    scheduler.shutdown();
  }

  /*** apply ***/

  @Test
  public void apply() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void applyWithLocal() {
    AtomicReference<Optional<Integer>> localValue = new AtomicReference<>();
    Local<Integer> l = Local.apply();
    l.update(1);
    Promise<Integer> p = Promise.apply();
    l.update(2);
    p.ensure(() -> localValue.set(l.get()));
    p.setValue(1);
    assertEquals(Optional.of(1), localValue.get());
  }

  @Test
  public void applyHandler() throws CheckedFutureException {
    AtomicReference<Throwable> interrupt = new AtomicReference<Throwable>();
    InterruptHandler handler = interrupt::set;
    Promise<Integer> p = Promise.apply(handler);
    p.raise(ex);
    assertEquals(ex, interrupt.get());
  }
  
  @Test
  public void applyHandlerWithLocal() {
    AtomicReference<Optional<Integer>> localValue = new AtomicReference<>();
    Local<Integer> l = Local.apply();
    l.update(1);
    Promise<Integer> p = Promise.apply((ex) -> {});
    l.update(2);
    p.ensure(() -> localValue.set(l.get()));
    p.setValue(1);
    assertEquals(Optional.of(1), localValue.get());
  }

  @Test
  public void applyTwoHandlers() throws CheckedFutureException {
    AtomicReference<Throwable> interrupt1 = new AtomicReference<Throwable>();
    AtomicReference<Throwable> interrupt2 = new AtomicReference<Throwable>();
    InterruptHandler handler1 = interrupt1::set;
    InterruptHandler handler2 = interrupt2::set;
    Promise<Integer> p = Promise.apply(handler1, handler2);
    p.raise(ex);
    assertEquals(ex, interrupt1.get());
    assertEquals(ex, interrupt2.get());
  }
  
  @Test
  public void applyTwoHandlersWithLocal() {
    AtomicReference<Optional<Integer>> localValue = new AtomicReference<>();
    Local<Integer> l = Local.apply();
    l.update(1);
    Promise<Integer> p = Promise.apply((ex) -> {}, (ex) -> {});
    l.update(2);
    p.ensure(() -> localValue.set(l.get()));
    p.setValue(1);
    assertEquals(Optional.of(1), localValue.get());
  }

  @Test
  public void applyHandlers() throws CheckedFutureException {
    AtomicReference<Throwable> interrupt1 = new AtomicReference<Throwable>();
    AtomicReference<Throwable> interrupt2 = new AtomicReference<Throwable>();
    InterruptHandler handler1 = interrupt1::set;
    InterruptHandler handler2 = interrupt2::set;
    Promise<Integer> p = Promise.apply(Arrays.asList(handler1, handler2));
    p.raise(ex);
    assertEquals(ex, interrupt1.get());
    assertEquals(ex, interrupt2.get());
  }
  
  @Test
  public void applyHandlersWithLocal() {
    AtomicReference<Optional<Integer>> localValue = new AtomicReference<>();
    Local<Integer> l = Local.apply();
    l.update(1);
    Promise<Integer> p = Promise.apply(Arrays.asList((ex) -> {}, (ex) -> {}));
    l.update(2);
    p.ensure(() -> localValue.set(l.get()));
    p.setValue(1);
    assertEquals(Optional.of(1), localValue.get());
  }

  /*** becomeIfEmpty ***/

  @Test
  public void becomeIfEmptyContinuation() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p2.becomeIfEmpty(p1.map(i -> i + 1));
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(2), get(p2));
  }

  @Test
  public void becomeIfEmptyTwoContinuations() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> f1 = p1.map(i -> i + 1);
    Future<Integer> f2 = p2.map(i -> i + 1);
    p2.becomeIfEmpty(f1);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(2), get(p2));
    assertEquals(new Integer(2), get(f1));
    assertEquals(new Integer(3), get(f2));
  }

  @Test
  public void becomeIfEmptySatisfied() {
    Promise<Integer> p = Promise.apply();
    p.setValue(1);
    assertFalse(p.becomeIfEmpty(Future.value(1)));
  }

  @Test
  public void becomeIfEmptyLinked() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.become(p2);
    assertTrue(p2.becomeIfEmpty(Future.value(1)));
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void becomeIfEmptyLinkedSatisfied() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p2.setValue(1);
    p1.become(p2);
    assertFalse(p2.becomeIfEmpty(Future.value(1)));
  }

  @Test
  public void becomeIfEmptyNestedLinked() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.become(p2);
    assertTrue(p2.becomeIfEmpty(Future.value(1)));
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void becomeIfEmptyWaiting() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> c = p.map(i -> i + 1);
    assertTrue(p.becomeIfEmpty(Future.value(1)));
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(2), get(c));
  }

  @Test
  public void becomeIfEmptyLocals() {
    AtomicReference<Optional<Integer>> restoredLocal = new AtomicReference<>();
    Local<Integer> l = Local.apply();

    l.set(Optional.of(1));
    Promise<Integer> p = Promise.apply();
    l.set(Optional.empty());

    p.map(i -> {
      restoredLocal.set(l.get());
      return i + 1;
    });

    assertTrue(p.becomeIfEmpty(Future.value(1)));
    assertEquals(Optional.of(1), restoredLocal.get());
    assertEquals(Optional.empty(), l.get());
  }

  @Test
  public void becomeIfEmptyConcurrent() throws CheckedFutureException, InterruptedException {
    ExecutorService es = Executors.newFixedThreadPool(10);
    try {
      Promise<Integer> p = Promise.apply();
      AtomicInteger expected = new AtomicInteger(-1);
      AtomicBoolean start = new AtomicBoolean();
      for (int i = 0; i < 10; i++) {
        final int ii = i;
        es.submit(() -> {
          while (true) {
            if (start.get())
              break;
          }
          if (p.becomeIfEmpty(Future.value(ii)))
            expected.set(ii);
        });
      }
      start.set(true);
      int result = p.get(100, TimeUnit.MILLISECONDS);
      Thread.sleep(10);
      assertEquals(expected.get(), result);
    } finally {
      es.shutdown();
    }
  }

  @Test
  public void becomeIfEmptyWithPromise() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> f = p1.map(i -> i + 1);
    assertTrue(p1.becomeIfEmpty(p2));
    p2.setValue(1);
    assertEquals(new Integer(2), get(f));
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test(expected = NullPointerException.class)
  public void becomeIfEmptyError() {
    Promise<Integer> p = Promise.<Integer>apply();
    p.map(i -> i + 1);
    p.becomeIfEmpty(null);
  }

  @Test(expected = StackOverflowError.class)
  public void becomeIfEmptyStakOverflow() {
    Promise<Integer> p = Promise.<Integer>apply();
    Future<Integer> f = p;
    for (int i = 0; i < 20000; i++)
      f = f.map(v -> v + 1);
    p.becomeIfEmpty(Future.value(1));
  }

  /*** setValue ***/

  @Test
  public void setValueSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test(expected = IllegalStateException.class)
  public void setValueFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.setValue(1);
    p.setValue(1);
  }

  /*** setException ***/

  @Test(expected = TestException.class)
  public void setExceptionSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.setException(ex);
    get(p);
  }

  @Test(expected = IllegalStateException.class)
  public void setExceptionFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.setException(ex);
    p.setException(ex);
  }

  /*** raise ***/

  @Test
  public void raise() {
    AtomicReference<Throwable> interrupt = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(interrupt::set);
    p.raise(ex);
    assertEquals(ex, interrupt.get());
  }

  @Test
  public void raiseDone() {
    AtomicReference<Throwable> interrupt = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(interrupt::set);
    p.setValue(1);
    p.raise(ex);
    assertNull(interrupt.get());
  }

  @Test
  public void raiseLinked() {
    AtomicReference<Throwable> interrupt = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(interrupt::set);
    Promise<Integer> p2 = Promise.apply();
    p1.become(p2);
    p2.raise(ex);
    assertEquals(ex, interrupt.get());
  }
  
  @Test
  public void raiseLinkedContinuation() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(intr::set);
    Promise<Integer> p2 = Promise.apply();
    @SuppressWarnings("unchecked")
    Continuation<Integer, Integer> c = (Continuation<Integer, Integer>) p1.map(i -> i + 1);
    c.become(p2);
    p2.raise(ex);
    assertEquals(ex, intr.get());
  }
  
  @Test
  public void raiseNoHandler() {
    Promise<Integer> p = Promise.apply();
    p.raise(ex);
  }

  /*** become ***/

  @Test
  public void becomeSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.become(Future.value(1));
    assertEquals(new Integer(1), get(p));
  }

  @Test(expected = IllegalStateException.class)
  public void becomeFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.become(Future.value(1));
    p.become(Future.value(1));
  }

  @Test
  public void becomeAPromise() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.become(p2);
    p2.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void becomeAContinuation() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.become(p2.map(i -> i + 1));
    p2.setValue(1);
    assertEquals(new Integer(2), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void becomeASatisfiedFuture() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    p.become(Future.value(1));
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void becomeLinkedChain() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Promise<Integer> p3 = Promise.apply();
    p2.become(p1);
    p3.become(p2);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(1), get(p3));
  }

  @Test
  public void becomeDoubleLinked() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Promise<Integer> p3 = Promise.apply();
    p1.become(p3);
    p2.become(p3);
    p3.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(1), get(p3));
  }

  @Test
  public void becomeLinkedWhenWaiting() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> f = p2.map(i -> i + 1);
    p1.become(p2);
    p2.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(2), get(f));
  }

  @Test
  public void becomeCompress() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Promise<Integer> p3 = Promise.apply();
    p2.become(p1);
    p3.become(p2);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(1), get(p3));
  }

  @Test(expected = IllegalStateException.class)
  public void becomeAlreadySatisfiedFailure() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.setValue(1);
    p2.setValue(2);
    p1.become(p2);
  }

  /*** isDefined ***/

  @Test
  public void isDefinedDone() {
    Promise<Integer> p = Promise.apply();
    p.setValue(1);
    assertTrue(p.isDefined());
  }

  @Test
  public void isDefinedLinkedDone() {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p2.become(p1);
    p1.setValue(1);
    assertTrue(p1.isDefined());
  }

  @Test
  public void isDefinedLinkedWaiting() {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p2.become(p1);
    assertFalse(p2.isDefined());
  }
  
  @Test
  public void isDefinedLinkedContinuationDone() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(intr::set);
    Promise<Integer> p2 = Promise.apply();
    @SuppressWarnings("unchecked")
    Continuation<Integer, Integer> c = (Continuation<Integer, Integer>) p1.map(i -> i + 1);
    c.become(p2);
    p2.setValue(1);
    assertTrue(p2.isDefined());
  }
  
  @Test
  public void isDefinedLinkedContinuationWaiting() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(intr::set);
    Promise<Integer> p2 = Promise.apply();
    @SuppressWarnings("unchecked")
    Continuation<Integer, Integer> c = (Continuation<Integer, Integer>) p1.map(i -> i + 1);
    c.become(p2);
    assertFalse(p2.isDefined());
  }

  @Test
  public void isDefinedWaiting() {
    Promise<Integer> p = Promise.apply();
    assertFalse(p.isDefined());
  }

  /*** voided ***/

  @Test
  public void voidedSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Void> future = p.voided();
    p.setValue(1);
    assertNull(get(future));
  }

  @Test(expected = TestException.class)
  public void voidedFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Void> future = p.voided();
    p.setException(ex);
    get(future);
  }
  
  @Test
  public void voidedInterrupt() throws CheckedFutureException {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Void> f = p.voided();
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  /*** delayed ***/

  @Test
  public void delayed() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    long delay = 10;
    long start = System.currentTimeMillis();
    Future<Integer> delayed = p.delayed(delay, TimeUnit.MILLISECONDS, scheduler);
    p.setValue(1);
    int result = delayed.get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  @Test
  public void doubleDelayed() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    long delay = 10;
    long start = System.currentTimeMillis();
    Future<Integer> delayed = p.delayed(delay, TimeUnit.MILLISECONDS, scheduler).delayed(delay, TimeUnit.MILLISECONDS,
        scheduler);
    p.setValue(1);
    int result = delayed.get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  @Test
  public void delayedInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> future = p.delayed(10, TimeUnit.MILLISECONDS, scheduler);

    future.raise(ex);
    assertEquals(ex, intr.get());
  }

  /*** proxyTo ***/

  @Test(expected = IllegalStateException.class)
  public void proxySatisified() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p2.setValue(1);
    p1.proxyTo(p2);
  }

  @Test
  public void proxyToSuccess() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.proxyTo(p2);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p2));
  }

  @Test(expected = TestException.class)
  public void proxyToFailure() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.proxyTo(p2);
    p1.setException(ex);
    get(p2);
  }

  /*** within ***/

  @Test
  public void withinMaxLongWait() {
    Future<Integer> future = Promise.apply();
    assertEquals(future, future.within(Long.MAX_VALUE, TimeUnit.MILLISECONDS, scheduler, ex));
  }

  @Test
  public void withinPromiseSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, scheduler, ex);
    p.setValue(1);
    assertEquals(new Integer(1), get(future));
  }

  @Test(expected = TestException.class)
  public void withinPromiseFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, scheduler, ex);
    p.setException(ex);
    get(future);
  }

  @Test(expected = TimeoutException.class)
  public void withinPromiseTimeout() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, scheduler, ex);
    get(future);
  }
  
  @Test
  public void withinInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.within(10, TimeUnit.MILLISECONDS, scheduler);
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  /*** get ***/

  @Test
  public void getSuccess() throws InterruptedException {
    ExecutorService es = Executors.newCachedThreadPool();
    try {
      Promise<Integer> p = Promise.apply();
      CountDownLatch latch = new CountDownLatch(1);
      es.submit(() -> {
        try {
          p.get(100, TimeUnit.MILLISECONDS);
          latch.countDown();
        } catch (CheckedFutureException e) {
        }
      });
      es.submit(() -> {
        p.setValue(1);
      });
      latch.await();
    } finally {
      es.shutdown();
    }
  }

  @Test(expected = TimeoutException.class)
  public void getTimeout() throws CheckedFutureException {
    (Promise.<Integer>apply()).get(10, TimeUnit.MILLISECONDS);
  }

  @Test
  public void getInterrupted() throws CheckedFutureException, InterruptedException {
    AtomicReference<Throwable> cause = new AtomicReference<>();
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          (Promise.<Integer>apply()).get(10, TimeUnit.DAYS);
        } catch (CheckedFutureException e) {
          cause.set(e.getCause());
        }
      }
    };
    t.start();
    Thread.sleep(100);
    t.interrupt();
    t.join();
    assertTrue(cause.get() instanceof InterruptedException);
  }

  /*** continuation ***/

  @Test
  public void multiple() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> f1 = p.map(i -> i + 1);
    Future<Integer> f2 = p.map(i -> i + 2);
    Future<Integer> f3 = p.map(i -> i + 3);
    p.setValue(1);
    assertEquals(new Integer(2), get(f1));
    assertEquals(new Integer(3), get(f2));
    assertEquals(new Integer(4), get(f3));
  }
  
  @Test
  public void linkedContinuation() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    @SuppressWarnings("unchecked")
    Continuation<Integer, Integer> c = (Continuation<Integer, Integer>) p1.map(i -> i + 1);
    c.become(p2);
    Future<Integer> f = p2.map(i -> i + 1);
    p2.setValue(1);
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(2), get(f));
  }

  @Test
  public void map() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> f = p.map(i -> i + 1);
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(2), get(f));
  }
  
  @Test
  public void mapInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.map(i -> i + 1);
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test
  public void flatMap() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> f = p.flatMap(i -> Future.value(i + 1));
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(2), get(f));
  }
  
  @Test
  public void flatMapInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.flatMap(i -> Future.value(i + 1));
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test
  public void ensure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicBoolean called = new AtomicBoolean(false);
    Future<Integer> f = p.ensure(() -> called.set(true));
    p.setValue(1);
    assertTrue(called.get());
    assertEquals(new Integer(1), get(f));
  }
  
  @Test
  public void ensureInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.ensure(() -> {});
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test
  public void biMap() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> f = p.biMap(Future.value(1), (a, b) -> a + b);
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(2), get(f));
  }
  
  @Test
  public void biMapInterrupt() {
    AtomicReference<Throwable> intr1 = new AtomicReference<>();
    AtomicReference<Throwable> intr2 = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(intr1::set);
    Promise<Integer> p2 = Promise.apply(intr2::set);
    Future<Integer> f = p1.biMap(p2, (a, b) -> a + b);
    f.raise(ex);
    assertEquals(ex, intr1.get());
    assertEquals(ex, intr2.get());
  }

  @Test
  public void biMapAnotherPromise() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> f = p1.biMap(p2, (a, b) -> a + b);
    p1.setValue(1);
    p2.setValue(2);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(2), get(p2));
    assertEquals(new Integer(3), get(f));
  }

  @Test
  public void biFlatMap() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    Future<Integer> f = p.biFlatMap(Future.value(1), (a, b) -> Future.value(a + b));
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(2), get(f));
  }
  
  @Test
  public void biFlatMapInterrupt() {
    AtomicReference<Throwable> intr1 = new AtomicReference<>();
    AtomicReference<Throwable> intr2 = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(intr1::set);
    Promise<Integer> p2 = Promise.apply(intr2::set);
    Future<Integer> f = p1.biFlatMap(p2, (a, b) -> Future.value(a + b));
    f.raise(ex);
    assertEquals(ex, intr1.get());
    assertEquals(ex, intr2.get());
  }

  @Test
  public void biFlatMapAnotherPromise() throws CheckedFutureException {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    Future<Integer> f = p1.biFlatMap(p2, (a, b) -> Future.value(a + b));
    p1.setValue(1);
    p2.setValue(2);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(2), get(p2));
    assertEquals(new Integer(3), get(f));
  }

  @Test
  public void onSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicInteger result = new AtomicInteger();
    Future<Integer> f = p.onSuccess(result::set);
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(1), get(f));
  }
  
  @Test
  public void onSuccessInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.onSuccess(i -> {});
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test(expected = TestException.class)
  public void onFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicReference<Throwable> result = new AtomicReference<>();
    Future<Integer> f = p.onFailure(result::set);
    p.setException(ex);
    assertEquals(ex, result.get());
    get(f);
  }
  
  @Test
  public void onFailureInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.onFailure(i -> {});
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test
  public void respondSuccess() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicInteger result = new AtomicInteger(-1);
    AtomicBoolean failure = new AtomicBoolean(false);
    Responder<Integer> r = new Responder<Integer>() {
      @Override
      public void onException(Throwable ex) {
        failure.set(true);
      }

      @Override
      public void onValue(Integer value) {
        result.set(value);
      }
    };
    Future<Integer> f = p.respond(r);
    p.setValue(1);
    assertEquals(1, result.get());
    assertFalse(failure.get());
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(1), get(f));
  }

  @Test(expected = TestException.class)
  public void respondFailure() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicBoolean success = new AtomicBoolean(false);
    AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
    Responder<Integer> r = new Responder<Integer>() {
      @Override
      public void onException(Throwable ex) {
        failure.set(ex);
      }

      @Override
      public void onValue(Integer value) {
        success.set(true);
      }
    };
    Future<Integer> f = p.respond(r);
    p.setException(ex);
    assertEquals(ex, failure.get());
    assertFalse(success.get());
    get(f);
  }
  
  @Test
  public void respondInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Responder<Integer> r = new Responder<Integer>() {
      @Override
      public void onException(Throwable ex) {
      }
      @Override
      public void onValue(Integer value) {
      }
    };
    Future<Integer> f = p.respond(r);
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test
  public void rescue() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Future<Integer> f = p.rescue(t -> {
      exception.set(t);
      return Future.value(2);
    });
    p.setException(ex);
    assertEquals(ex, exception.get());
    assertEquals(new Integer(2), get(f));
  }
  
  @Test
  public void rescueInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.rescue(ex -> Future.value(1));
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  @Test
  public void handle() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Future<Integer> f = p.handle(t -> {
      exception.set(t);
      return 2;
    });
    p.setException(ex);
    assertEquals(ex, exception.get());
    assertEquals(new Integer(2), get(f));
  }
  
  @Test
  public void handleInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = Promise.apply(intr::set);
    Future<Integer> f = p.handle(ex -> 1);
    f.raise(ex);
    assertEquals(ex, intr.get());
  }

  /*** toString ***/

  private String hexHashCode(Future<?> p) {
    return Integer.toHexString(p.hashCode());
  }

  @Test
  public void toStringSatisfied() {
    Promise<Integer> p = Promise.apply();
    p.setValue(1);
    assertEquals("Promise(ValueFuture(1))@" + hexHashCode(p), p.toString());
  }

  @Test
  public void toStringLinked() {
    Promise<Integer> p1 = Promise.apply();
    Promise<Integer> p2 = Promise.apply();
    p1.become(p2);
    assertEquals("Promise(Linked(" + p1.toString() + "))@" + hexHashCode(p2), p2.toString());
  }
  
  @Test
  public void toStringLinkedContinuationWaiting() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p1 = Promise.apply(intr::set);
    Promise<Integer> p2 = Promise.apply();
    @SuppressWarnings("unchecked")
    Continuation<Integer, Integer> c = (Continuation<Integer, Integer>) p1.map(i -> i + 1);
    c.become(p2);
    assertEquals("Promise(Linked(" + c.toString() + "))@" + hexHashCode(p2), p2.toString());
  }
  
  @Test
  public void toStringWaiting() {
    Promise<Integer> p = Promise.apply();
    assertEquals("Promise(Waiting)@" + hexHashCode(p), p.toString());
  }

  @Test
  public void toStringWaitingNonEmptyQueue() {
    Promise<Integer> p = Promise.apply();
    p.map(i -> i + 1);
    assertEquals("Promise(Waiting)@" + hexHashCode(p), p.toString());
  }

  @Test
  public void toStringContinuation() {
    Future<Integer> p = (Promise.<Integer>apply()).map(i -> i + 1);
    assertEquals("Continuation(Waiting)@" + hexHashCode(p), p.toString());
  }
}
