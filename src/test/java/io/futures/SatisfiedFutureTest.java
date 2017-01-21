package io.futures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class SatisfiedFutureTest {
  
  private final Exception ex = new TestException();
  
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
}
