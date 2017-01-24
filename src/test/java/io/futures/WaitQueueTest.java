//package io.futures;
//
//import org.junit.Test;
//import static org.junit.Assert.*;
//
//import java.util.concurrent.TimeUnit;
//
//public class WaitQueueTest {
//
//  private <T> T get(Future<T> future) throws CheckedFutureException {
//    return future.get(0, TimeUnit.MILLISECONDS);
//  }
//
//  private <T> Continuation<T, T> newContinuation() {
//    InterruptHandler handler = ex -> {
//    };
//    return new Continuation<T, T>(handler) {
//      @Override
//      Future<T> apply(Future<T> result) {
//        return result;
//      }
//    };
//  }
//
//  @Test
//  public void addAndFlush1() throws CheckedFutureException {
//    Continuation<Integer, Integer> c = newContinuation();
//
//    WaitQueue<Integer> queue = WaitQueue.create(c);
//
//    queue.flush(Future.value(1));
//
//    assertEquals(new Integer(1), get(c));
//  }
//
//  @Test
//  public void addAndFlush2() throws CheckedFutureException {
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//
//    WaitQueue<Integer> queue = WaitQueue.create(c1).add(c2);
//
//    queue.flush(Future.value(1));
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//  }
//
//  @Test
//  public void addAndFlush3() throws CheckedFutureException {
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//
//    WaitQueue<Integer> queue = WaitQueue.create(c1).add(c2).add(c3);
//
//    queue.flush(Future.value(1));
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//  }
//
//  @Test
//  public void addAndFlush4() throws CheckedFutureException {
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//    Continuation<Integer, Integer> c4 = newContinuation();
//
//    WaitQueue<Integer> queue = WaitQueue.create(c1).add(c2).add(c3);
//
//    queue.flush(Future.value(1));
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//    assertEquals(new Integer(1), get(c4));
//  }
//
//  @Test
//  public void addAndFlush5() throws CheckedFutureException {
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//    Continuation<Integer, Integer> c4 = newContinuation();
//    Continuation<Integer, Integer> c5 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//    queue = WaitQueue.add(queue, c3);
//    queue = WaitQueue.add(queue, c4);
//    queue = WaitQueue.add(queue, c5);
//
//    queue.flush(Future.value(1));
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//    assertEquals(new Integer(1), get(c4));
//    assertEquals(new Integer(1), get(c5));
//  }
//
//  @Test
//  public void addAndFlush6() throws CheckedFutureException {
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//    Continuation<Integer, Integer> c4 = newContinuation();
//    Continuation<Integer, Integer> c5 = newContinuation();
//    Continuation<Integer, Integer> c6 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//    queue = WaitQueue.add(queue, c3);
//    queue = WaitQueue.add(queue, c4);
//    queue = WaitQueue.add(queue, c5);
//    queue = WaitQueue.add(queue, c6);
//
//    queue.flush(Future.value(1));
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//    assertEquals(new Integer(1), get(c4));
//    assertEquals(new Integer(1), get(c5));
//    assertEquals(new Integer(1), get(c6));
//  }
//
//  @Test
//  public void addAndForward() throws CheckedFutureException {
//    Promise<Integer> p = new Promise<>();
//    Continuation<Integer, Integer> c = newContinuation();
//
//    Object queue = WaitQueue.add(null, c);
//
//    WaitQueue.forward(queue, p);
//    p.setValue(1);
//
//    assertEquals(new Integer(1), get(c));
//  }
//
//  @Test
//  public void addAndForward2() throws CheckedFutureException {
//    Promise<Integer> p = new Promise<>();
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//
//    WaitQueue.forward(queue, p);
//    p.setValue(1);
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//  }
//
//  @Test
//  public void addAndForward3() throws CheckedFutureException {
//    Promise<Integer> p = new Promise<>();
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//    queue = WaitQueue.add(queue, c3);
//
//    WaitQueue.forward(queue, p);
//    p.setValue(1);
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//  }
//
//  @Test
//  public void addAndForward4() throws CheckedFutureException {
//    Promise<Integer> p = new Promise<>();
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//    Continuation<Integer, Integer> c4 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//    queue = WaitQueue.add(queue, c3);
//    queue = WaitQueue.add(queue, c4);
//
//    WaitQueue.forward(queue, p);
//    p.setValue(1);
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//    assertEquals(new Integer(1), get(c4));
//  }
//
//  @Test
//  public void addAndForward5() throws CheckedFutureException {
//    Promise<Integer> p = new Promise<>();
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//    Continuation<Integer, Integer> c4 = newContinuation();
//    Continuation<Integer, Integer> c5 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//    queue = WaitQueue.add(queue, c3);
//    queue = WaitQueue.add(queue, c4);
//    queue = WaitQueue.add(queue, c5);
//
//    WaitQueue.forward(queue, p);
//    p.setValue(1);
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//    assertEquals(new Integer(1), get(c4));
//    assertEquals(new Integer(1), get(c5));
//  }
//
//  @Test
//  public void addAndForward6() throws CheckedFutureException {
//    Promise<Integer> p = new Promise<>();
//    Continuation<Integer, Integer> c1 = newContinuation();
//    Continuation<Integer, Integer> c2 = newContinuation();
//    Continuation<Integer, Integer> c3 = newContinuation();
//    Continuation<Integer, Integer> c4 = newContinuation();
//    Continuation<Integer, Integer> c5 = newContinuation();
//    Continuation<Integer, Integer> c6 = newContinuation();
//
//    Object queue = null;
//    queue = WaitQueue.add(queue, c1);
//    queue = WaitQueue.add(queue, c2);
//    queue = WaitQueue.add(queue, c3);
//    queue = WaitQueue.add(queue, c4);
//    queue = WaitQueue.add(queue, c5);
//    queue = WaitQueue.add(queue, c6);
//
//    WaitQueue.forward(queue, p);
//    p.setValue(1);
//
//    assertEquals(new Integer(1), get(c1));
//    assertEquals(new Integer(1), get(c2));
//    assertEquals(new Integer(1), get(c3));
//    assertEquals(new Integer(1), get(c4));
//    assertEquals(new Integer(1), get(c5));
//    assertEquals(new Integer(1), get(c6));
//  }
//}
