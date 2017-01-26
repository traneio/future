package io.futures;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

public class WaitQueueTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  private <T> Continuation<T, T> newContinuation() {
    InterruptHandler handler = ex -> {
    };
    return new Continuation<T, T>(handler) {
      @Override
      Future<T> apply(Future<T> result) {
        return result;
      }
    };
  }

  @Test
  public void addAndFlush1() throws CheckedFutureException {
    Continuation<Integer, Integer> c = newContinuation();
    
    c.flush(Future.value(1));
    
    assertEquals(new Integer(1), get(c));
  }

  @Test
  public void addAndFlush2() throws CheckedFutureException {
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();

    c1.add(c2).flush(Future.value(1));
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
  }

  @Test
  public void addAndFlush3() throws CheckedFutureException {
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    
    c1.add(c2).add(c3).flush(Future.value(1));
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
  }

  @Test
  public void addAndFlush4() throws CheckedFutureException {
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    Continuation<Integer, Integer> c4 = newContinuation();
    
    c1.add(c2).add(c3).add(c4).flush(Future.value(1));
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
    assertEquals(new Integer(1), get(c4));
  }

  @Test
  public void addAndFlush5() throws CheckedFutureException {
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    Continuation<Integer, Integer> c4 = newContinuation();
    Continuation<Integer, Integer> c5 = newContinuation();
    
    c1.add(c2).add(c3).add(c4).add(c5).flush(Future.value(1));
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
    assertEquals(new Integer(1), get(c4));
    assertEquals(new Integer(1), get(c5));
  }

  @Test
  public void addAndFlush6() throws CheckedFutureException {
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    Continuation<Integer, Integer> c4 = newContinuation();
    Continuation<Integer, Integer> c5 = newContinuation();
    Continuation<Integer, Integer> c6 = newContinuation();
    
    c1.add(c2).add(c3).add(c4).add(c5).add(c6).flush(Future.value(1));
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
    assertEquals(new Integer(1), get(c4));
    assertEquals(new Integer(1), get(c5));
    assertEquals(new Integer(1), get(c6));
  }

  @Test
  public void addAndForward() throws CheckedFutureException {
    Promise<Integer> p = Future.promise();
    Continuation<Integer, Integer> c = newContinuation();
    
    c.forward(p);
    p.setValue(1);
    
    assertEquals(new Integer(1), get(c));
  }
  
  @Test
  public void addAndForward2() throws CheckedFutureException {
    Promise<Integer> p = Future.promise();
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    
    c1.add(c2).forward(p);
    
    p.setValue(1);
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
  }
  
  @Test
  public void addAndForward3() throws CheckedFutureException {
    Promise<Integer> p = Future.promise();
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    
    c1.add(c2).add(c3).forward(p);
    
    p.setValue(1);
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
  }

  @Test
  public void addAndForward4() throws CheckedFutureException {
    Promise<Integer> p = Future.promise();
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    Continuation<Integer, Integer> c4 = newContinuation();
    
    c1.add(c2).add(c3).add(c4).forward(p);
    
    p.setValue(1);
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
    assertEquals(new Integer(1), get(c4));
  }

  @Test
  public void addAndForward5() throws CheckedFutureException {
    Promise<Integer> p = Future.promise();
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    Continuation<Integer, Integer> c4 = newContinuation();
    Continuation<Integer, Integer> c5 = newContinuation();
    
    c1.add(c2).add(c3).add(c4).add(c5).forward(p);
    
    p.setValue(1);
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
    assertEquals(new Integer(1), get(c4));
    assertEquals(new Integer(1), get(c5));
  }

  @Test
  public void addAndForward6() throws CheckedFutureException {
    Promise<Integer> p = Future.promise();
    Continuation<Integer, Integer> c1 = newContinuation();
    Continuation<Integer, Integer> c2 = newContinuation();
    Continuation<Integer, Integer> c3 = newContinuation();
    Continuation<Integer, Integer> c4 = newContinuation();
    Continuation<Integer, Integer> c5 = newContinuation();
    Continuation<Integer, Integer> c6 = newContinuation();
    
    c1.add(c2).add(c3).add(c4).add(c5).add(c6).forward(p);
    
    p.setValue(1);
    
    assertEquals(new Integer(1), get(c1));
    assertEquals(new Integer(1), get(c2));
    assertEquals(new Integer(1), get(c3));
    assertEquals(new Integer(1), get(c4));
    assertEquals(new Integer(1), get(c5));
    assertEquals(new Integer(1), get(c6));
  }
}
