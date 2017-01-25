package io.futures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Test;

public class SatisfiedFutureTest {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Exception ex = new TestException();

  @After
  public void shutdownScheduler() {
    scheduler.shutdown();
  }

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  /*** ensure ***/

  @Test
  public void ensure() {
    AtomicBoolean called = new AtomicBoolean(false);
    Future.value(1).ensure(() -> called.set(true));
    assertTrue(called.get());
  }

  @Test
  public void ensureException() throws CheckedFutureException {
    Future<String> future = Future.value("s").ensure(() -> {
      throw new RuntimeException();
    });
    assertEquals("s", get(future));
  }

  /*** raise ***/

  @Test
  public void raise() {
    Future.value(1).raise(new Throwable());
  }

  /*** isDefined ***/

  @Test
  public void isDefinedValue() {
    assertTrue(Future.value(1).isDefined());
  }

  @Test
  public void isDefinedException() {
    assertTrue(Future.exception(ex).isDefined());
  }

  /*** proxyTo ***/

  @Test(expected = IllegalStateException.class)
  public void proxy() {
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

  /*** delayed ***/

  @Test
  public void delayed() throws CheckedFutureException {
    Future<Integer> future = Future.value(1);
    long delay = 10;
    long start = System.currentTimeMillis();
    int result = future.delayed(delay, TimeUnit.MILLISECONDS, scheduler).get(20, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= delay);
    assertEquals(1, result);
  }

  /*** within ***/

  @Test(expected = TestException.class)
  public void withinFailure() throws CheckedFutureException {
    Future<Integer> f = Future.<Integer>exception(ex).within(1, TimeUnit.MILLISECONDS, scheduler);
    get(f);
  }

  @Test
  public void withinSuccess() throws CheckedFutureException {
    Future<Integer> f = Future.value(1).within(1, TimeUnit.MILLISECONDS, scheduler);
    assertEquals(new Integer(1), get(f));
  }

  /*** join ***/

  @Test
  public void join() throws CheckedFutureException {
    Future<Integer> future = Future.exception(ex);
    future.join(0, TimeUnit.MILLISECONDS);
  }
}
