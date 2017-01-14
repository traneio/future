package io.futures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class SatisfiedFutureTest {
  
  private <T> T get(Future<T> future) throws InterruptedException {
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
  public void ensureException() throws InterruptedException {
    Future<String> future = Future.value("s").ensure(() -> {
      throw new RuntimeException();
    });
    assertEquals("s", get(future));
  }
  
  /*** raise ***/
  
  @Test
  public void raise() {
    Future.value(1).raise(new RuntimeException());
  }
}
