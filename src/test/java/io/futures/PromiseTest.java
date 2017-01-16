package io.futures;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

import org.junit.Test;

public class PromiseTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  Exception ex = new TestException();

  /*** new ***/

  @Test
  public void newPromise() throws CheckedFutureException {
    Promise<Integer> p = new Promise<>();
    p.setValue(1);
    assertEquals(new Integer(1), get(p));
  }

  @Test
  public void newPromiseWithHandler() throws CheckedFutureException {
    AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
    InterruptHandler handler = thrown::set;
    Promise<Integer> p = new Promise<>(handler);
    p.raise(ex);
    assertEquals(ex, thrown.get());
  }

  /*** updateIfEmpty ***/

  @Test(expected = IllegalArgumentException.class)
  public void updateIfEmptyContinuation() {
    Promise<Integer> p1 = new Promise<>();
    Promise<Integer> p2 = new Promise<>();
    p2.updateIfEmpty(p1.map(i -> i + 1));
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

    Future<Integer> c = p.map(i -> {
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
}
