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

  /*** new ***/

  @Test
  public void newPromise() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void newPromiseWithHandler() throws CheckedFutureException {
    AtomicReference<Throwable> interrupt = new AtomicReference<Throwable>();
    InterruptHandler handler = interrupt::set;
    Promise<Integer> p = new Promise<>(handler);
    p.raise(ex);
    assertEquals(ex, interrupt.get());
  }

  @Test
  public void newPromiseWithHandlers() throws CheckedFutureException {
    AtomicReference<Throwable> interrupt1 = new AtomicReference<Throwable>();
    AtomicReference<Throwable> interrupt2 = new AtomicReference<Throwable>();
    InterruptHandler handler1 = interrupt1::set;
    InterruptHandler handler2 = interrupt2::set;
    Promise<Integer> p = new Promise<>(Arrays.asList(handler1, handler2));
    p.raise(ex);
    assertEquals(ex, interrupt1.get());
    assertEquals(ex, interrupt2.get());
  }

  /*** update ***/

  @Test
  public void updateSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.update(Future.value(1));
    assertEquals(new Integer(1), get(p));
  }

  @Test(expected = IllegalStateException.class)
  public void updateFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.update(Future.value(1));
    p.update(Future.value(1));
  }

  /*** updateIfEmpty ***/

  @Test
  public void updateIfEmptyContinuation() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p2.updateIfEmpty(p1.map(i -> i + 1));
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(2), get(p2));
  }
  
  @Test
  public void updateIfEmptyTwoContinuations() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Integer> f1 = p1.map(i -> i + 1);
    Future<Integer> f2 = p2.map(i -> i + 1);
    p2.updateIfEmpty(f1);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(2), get(p2));
    assertEquals(new Integer(2), get(f1));
    assertEquals(new Integer(3), get(f2));
  }

  @Test
  public void updateIfEmptySatisfied() {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    assertFalse(p.updateIfEmpty(Future.value(1)));
  }

  @Test
  public void updateIfEmptyLinked() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.become(p2);
    assertTrue(p2.updateIfEmpty(Future.value(1)));
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void updateIfEmptyLinkedSatisfied() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p2.setValue(1);
    p1.become(p2);
    assertFalse(p2.updateIfEmpty(Future.value(1)));
  }

  @Test
  public void updateIfEmptyWaiting() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> c = p.map(i -> i + 1);
    assertTrue(p.updateIfEmpty(Future.value(1)));
    assertEquals(new Integer(1), get(p));
    assertEquals(new Integer(2), get(c));
  }

  @Test
  public void updateIfEmptyLocals() {
    Local<Integer> l = new Local<Integer>();

    l.set(Optional.of(1));
    Promise<Integer> p = new Promise<>();
    l.set(Optional.empty());

    p.map(i -> {
      assertEquals(l.get(), Optional.of(1));
      return i + 1;
    });

    assertTrue(p.updateIfEmpty(Future.value(1)));
    assertEquals(l.get(), Optional.empty());
  }

  @Test
  public void updateIfEmptyConcurrent() throws CheckedFutureException {
    ExecutorService es = Executors.newFixedThreadPool(10);
    try {
      Promise<Integer> p = new Promise<>();
      AtomicInteger expected = new AtomicInteger(-1);
      AtomicBoolean start = new AtomicBoolean();
      for (int i = 0; i < 10; i++) {
        final int ii = i;
        es.submit(() -> {
          while (true) {
            if (start.get())
              break;
          }
          if (p.updateIfEmpty(Future.value(ii)))
            expected.set(ii);
        });
      }
      start.set(true);
      int result = p.get(100000, TimeUnit.MILLISECONDS);
      assertEquals(expected.get(), result);
    } finally {
      es.shutdown();
    }
  }

  @Test
  public void updateIfEmptyWithPromise() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Future<Integer> f = p1.map(i -> i + 1);
    assertTrue(p1.updateIfEmpty(p2));
    p2.setValue(1);
    assertEquals(new Integer(2), get(f));
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }
  
  @Test(expected = NullPointerException.class)
  public void updateIfEmptyError() {
    Promise<Integer> p = new Promise<Integer>();
    p.map(i -> i + 1);
    p.updateIfEmpty(null);
  }

  /*** setValue ***/

  @Test
  public void setValueSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test(expected = IllegalStateException.class)
  public void setValueFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    p.setValue(1);
  }

  /*** setException ***/

  @Test(expected = TestException.class)
  public void setExceptionSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.setException(ex);
    get(p);
  }

  @Test(expected = IllegalStateException.class)
  public void setExceptionFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.setException(ex);
    p.setException(ex);
  }

  /*** raise ***/

  @Test
  public void raise() {
    AtomicReference<Throwable> interrupt = new AtomicReference<>();
    Promise<Integer> p = new Promise<>(interrupt::set);
    p.raise(ex);
    assertEquals(ex, interrupt.get());
  }

  @Test
  public void raiseDone() {
    AtomicReference<Throwable> interrupt = new AtomicReference<>();
    Promise<Integer> p = new Promise<>(interrupt::set);
    p.setValue(1);
    p.raise(ex);
    assertNull(interrupt.get());
  }

  @Test
  public void raiseLinked() {
    AtomicReference<Throwable> interrupt = new AtomicReference<>();
    Promise<Integer> p1 = new Promise<>(interrupt::set);
    Promise<Integer> p2 = new Promise<>();
    p1.become(p2);
    p2.raise(ex);
    assertEquals(ex, interrupt.get());
  }

  @Test
  public void raiseNoHandler() {
    Promise<Integer> p = new Promise<>();
    p.raise(ex);
  }

  /*** become ***/

  @Test
  public void becomeAPromise() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.become(p2);
    p2.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void becomeAContinuation() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.become(p2.map(i -> i + 1));
    p2.setValue(1);
    assertEquals(new Integer(2), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test
  public void becomeASatisfiedFuture() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.become(Future.value(1));
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void becomeLinkedChain() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Promise<Integer> p3 = new Promise<>();
    p2.become(p1);
    p3.become(p2);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(1), get(p3));
  }

  @Test
  public void becomeDoubleLinked() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    Promise<Integer> p3 = new Promise<>();
    p1.become(p3);
    p2.become(p3);
    p3.setValue(1);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
    assertEquals(new Integer(1), get(p3));
  }

  @Test
  public void becomeAlreadySatisfiedSuccess() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.setValue(1);
    p2.setValue(1);
    p1.become(p2);
    assertEquals(new Integer(1), get(p1));
    assertEquals(new Integer(1), get(p2));
  }

  @Test(expected = IllegalStateException.class)
  public void becomeAlreadySatisfiedFailure() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.setValue(1);
    p2.setValue(2);
    p1.become(p2);
  }

  /*** isDefined ***/

  @Test
  public void isDefinedDone() {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    assertTrue(p.isDefined());
  }

  @Test
  public void isDefinedLinkedDone() {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p2.become(p1);
    p1.setValue(1);
    assertTrue(p1.isDefined());
  }

  @Test
  public void isDefinedLinkedWaiting() {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p2.become(p1);
    assertFalse(p2.isDefined());
  }

  @Test
  public void isDefinedWaiting() {
    Promise<Integer> p = new Promise<>();
    assertFalse(p.isDefined());
  }

  /*** voided ***/

  @Test
  public void voidedSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Void> future = p.voided();
    p.setValue(1);
    assertNull(get(future));
  }

  @Test(expected = TestException.class)
  public void voidedFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Void> future = p.voided();
    p.setException(ex);
    get(future);
  }

  /*** delayed ***/

  @Test
  public void delayed() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
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
    Promise<Integer> p = new Promise<>();
    long delay = 10;
    long start = System.currentTimeMillis();
    Future<Integer> delayed = p.delayed(delay, TimeUnit.MILLISECONDS, scheduler).delayed(delay, TimeUnit.MILLISECONDS, scheduler);
    p.setValue(1);
    int result = delayed.get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  @Test
  public void delayedInterrupt() {
    AtomicReference<Throwable> intr = new AtomicReference<>();
    Promise<Integer> p = new Promise<>(intr::set);
    Future<Integer> future = p.delayed(10, TimeUnit.MILLISECONDS, scheduler);

    future.raise(ex);
    assertEquals(ex, intr.get());
  }

  /*** proxyTo ***/

  @Test(expected = IllegalStateException.class)
  public void proxySatisified() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p2.setValue(1);
    p1.proxyTo(p2);
  }

  @Test
  public void proxyToSuccess() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.proxyTo(p2);
    p1.setValue(1);
    assertEquals(new Integer(1), get(p2));
  }

  @Test(expected = TestException.class)
  public void proxyToFailure() throws CheckedFutureException {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.proxyTo(p2);
    p1.setException(ex);
    get(p2);
  }

  /*** within ***/

  @Test
  public void withinMaxLongWait() {
    Future<Integer> future = new Promise<>();
    assertEquals(future, future.within(Long.MAX_VALUE, TimeUnit.MILLISECONDS, scheduler, ex));
  }

  @Test
  public void withinPromiseSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, scheduler, ex);
    p.setValue(1);
    assertEquals(new Integer(1), get(future));
  }

  @Test(expected = TestException.class)
  public void withinPromiseFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, scheduler, ex);
    p.setException(ex);
    get(future);
  }

  @Test(expected = TimeoutException.class)
  public void withinPromiseTimeout() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    Future<Integer> future = p.within(10, TimeUnit.MILLISECONDS, scheduler, ex);
    get(future);
  }

  /*** get ***/

  @Test
  public void getSuccess() throws InterruptedException {
    ExecutorService es = Executors.newCachedThreadPool();
    try {
      Promise<Integer> p = new Promise<>();
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
    (new Promise<Integer>()).get(10, TimeUnit.MILLISECONDS);
  }

  @Test
  public void getInterrupted() throws CheckedFutureException, InterruptedException {
    AtomicReference<Throwable> cause = new AtomicReference<>();
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          (new Promise<Integer>()).get(10, TimeUnit.DAYS);
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
  public void map() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.map(i -> i + 1);
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void flatMap() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.flatMap(i -> Future.value(i + 1));
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void ensure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    AtomicBoolean called = new AtomicBoolean(false);
    p.ensure(() -> called.set(true));
    p.setValue(1);
    assertTrue(called.get());
  }

  @Test
  public void onSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    AtomicInteger result = new AtomicInteger();
    p.onSuccess(result::set);
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void onFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    AtomicReference<Throwable> result = new AtomicReference<>();
    p.onFailure(result::set);
    p.setException(ex);
    assertEquals(ex, result.get());
  }

  @Test
  public void respondSuccess() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
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
    p.respond(r);
    p.setValue(1);
    assertEquals(1, result.get());
    assertFalse(failure.get());
  }

  @Test
  public void respondFailure() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
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
    p.respond(r);
    p.setException(ex);
    assertEquals(ex, failure.get());
    assertFalse(success.get());
  }

  @Test
  public void rescue() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
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
  public void handle() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Future<Integer> f = p.handle(t -> {
      exception.set(t);
      return 2;
    });
    p.setException(ex);
    assertEquals(ex, exception.get());
    assertEquals(new Integer(2), get(f));
  }
  
  /*** toString ***/
  
  private String hexHashCode(Promise<?> p) {
    return Integer.toHexString(p.hashCode());
  }

  @Test
  public void toStringSatisfied() {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    assertEquals("Promise(ValueFuture(1))@" + hexHashCode(p), p.toString());
  }

  @Test
  public void toStringLinked() {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p1.become(p2);
    assertEquals("Promise(Linked(" + p1.toString() + "))@" + hexHashCode(p2), p2.toString());
  }
  
  @Test
  public void toStringWaiting() {
    Promise<Integer> p = new Promise<>();
    assertEquals("Promise(Waiting)@" + hexHashCode(p), p.toString());
  }
  
  @Test
  public void toStringWaitingNonEmptyQueue() {
    Promise<Integer> p = new Promise<>();
    p.map(i -> i + 1);
    assertEquals("Promise(Waiting)@" + hexHashCode(p), p.toString());
  }
}
