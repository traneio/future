package io.trane.future;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class NoFutureTest {

  Exception ex = new TestException();

  Future<Integer> noFuture = new NoFuture<Integer>();

  @Test
  public void raise() {
    noFuture.raise(ex);
  }

  @Test
  public void map() {
    assertEquals(noFuture, noFuture.map(i -> i + 1));
  }

  @Test
  public void flatMap() {
    assertEquals(noFuture, noFuture.flatMap(i -> Future.value(1)));
  }

  @Test
  public void transform() {
    assertEquals(noFuture, noFuture.transform(new Transformer<Integer, Integer>() {
      @Override
      public Integer onException(Throwable ex) {
        return null;
      }

      @Override
      public Integer onValue(Integer value) {
        return null;
      }
    }));
  }

  @Test
  public void transformWith() {
    assertEquals(noFuture, noFuture.transformWith(new Transformer<Integer, Future<Integer>>() {
      @Override
      public Future<Integer> onException(Throwable ex) {
        return null;
      }

      @Override
      public Future<Integer> onValue(Integer value) {
        return null;
      }
    }));
  }

  @Test
  public void biMap() {
    assertEquals(noFuture, noFuture.biMap(Future.value(1), (i, j) -> i + j));
  }

  @Test
  public void biFlatMap() {
    assertEquals(noFuture, noFuture.biFlatMap(Future.value(1), (i, j) -> Future.value(i + j)));
  }

  @Test
  public void ensure() {
    assertEquals(noFuture, noFuture.ensure(() -> {
    }));
  }

  @Test
  public void onSuccess() {
    assertEquals(noFuture, noFuture.onSuccess(i -> {
    }));
  }

  @Test
  public void onFailure() {
    assertEquals(noFuture, noFuture.onFailure(ex -> {
    }));
  }

  @Test
  public void respond() {
    assertEquals(noFuture, noFuture.respond(new Responder<Integer>() {
      @Override
      public void onException(Throwable ex) {
      }

      @Override
      public void onValue(Integer value) {
      }
    }));
  }

  @Test
  public void rescue() {
    assertEquals(noFuture, noFuture.rescue(ex -> Future.value(1)));
  }

  @Test
  public void handle() {
    assertEquals(noFuture, noFuture.handle(ex -> 1));
  }
  
  @Test
  public void interruptible() {
    Future<Integer> future = Future.value(1);
    assertEquals(future, future.interruptible());
  }

  @Test
  public void isDefined() {
    assertFalse(noFuture.isDefined());
  }

  @Test(expected = TimeoutException.class)
  public void get() throws CheckedFutureException {
    long start = System.currentTimeMillis();
    long timeout = 10;
    try {
      noFuture.get(timeout, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      assertTrue(System.currentTimeMillis() - start >= timeout);
      throw ex;
    }
  }
  
  @Test
  public void getInterrupted() throws CheckedFutureException, InterruptedException {
    AtomicReference<Throwable> cause = new AtomicReference<>();
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          noFuture.get(10, TimeUnit.DAYS);
        } catch (CheckedFutureException e) {
          cause.set(e.getCause());
        }
      }
    };
    t.start();
    t.interrupt();
    t.join();
    assertTrue(cause.get() instanceof InterruptedException);
  }

  @Test
  public void join() throws CheckedFutureException {
    long start = System.currentTimeMillis();
    long timeout = 10;
    noFuture.join(timeout, TimeUnit.MILLISECONDS);
    assertTrue(System.currentTimeMillis() - start >= timeout);
  }

  @Test
  public void voided() {
    assertEquals(noFuture, noFuture.voided());
  }
  
  @Test
  public void delayed() {
    assertEquals(noFuture, noFuture.delayed(10, TimeUnit.MILLISECONDS, null));
  }

  @Test(expected = TimeoutException.class)
  public void proxyTo() throws CheckedFutureException {
    Promise<Integer> p = Promise.apply();
    noFuture.proxyTo(p);
    p.get(10, TimeUnit.MILLISECONDS);
  }

  @Test
  public void within() {
    assertEquals(noFuture, noFuture.within(10, TimeUnit.MILLISECONDS, null));
  }

}
