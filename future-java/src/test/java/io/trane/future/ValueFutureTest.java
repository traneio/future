package io.trane.future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.trane.future.CheckedFutureException;
import io.trane.future.Future;
import io.trane.future.Responder;

public class ValueFutureTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  /*** map ***/

  @Test
  public void map() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).map(i -> i + 1);
    assertEquals(new Integer(2), get(future));
  }

  @Test(expected = ArithmeticException.class)
  public void mapException() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).map(i -> i / 0);
    get(future);
  }

  /*** flatMap ***/

  @Test
  public void flatMap() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).flatMap(i -> Future.value(i + 1));
    assertEquals(new Integer(2), get(future));
  }

  @Test(expected = ArithmeticException.class)
  public void flatMapException() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).flatMap(i -> Future.value(i / 0));
    get(future);
  }

  /*** filter ***/

  @Test
  public void filter() throws CheckedFutureException {
    assertEquals(new Integer(1), get(Future.value(1).filter(i -> i == 1)));
  }

  @Test(expected = NoSuchElementException.class)
  public void filterNoSuchElement() throws CheckedFutureException {
    assertEquals(new Integer(1), get(Future.value(1).filter(i -> i == 2)));
  }

  /*** transform ***/

  @Test
  public void transform() throws CheckedFutureException {
    Transformer<Integer, Integer> t = new Transformer<Integer, Integer>() {
      @Override
      public Integer onException(Throwable ex) {
        return null;
      }

      @Override
      public Integer onValue(Integer value) {
        assertEquals(new Integer(1), value);
        return value + 1;
      }
    };
    assertEquals(new Integer(2), get(Future.value(1).transform(t)));
  }

  @Test(expected = TestException.class)
  public void transformException() throws CheckedFutureException {
    Transformer<Integer, Integer> t = new Transformer<Integer, Integer>() {
      @Override
      public Integer onException(Throwable ex) {
        return null;
      }

      @Override
      public Integer onValue(Integer value) {
        assertEquals(new Integer(1), value);
        throw new TestException();
      }
    };
    get(Future.value(1).transform(t));
  }

  /*** transformWith ***/

  @Test
  public void transformWith() throws CheckedFutureException {
    Transformer<Integer, Future<Integer>> t = new Transformer<Integer, Future<Integer>>() {
      @Override
      public Future<Integer> onException(Throwable ex) {
        return null;
      }

      @Override
      public Future<Integer> onValue(Integer value) {
        assertEquals(new Integer(1), value);
        return Future.value(value + 1);
      }
    };
    assertEquals(new Integer(2), get(Future.value(1).transformWith(t)));
  }

  @Test(expected = TestException.class)
  public void transformWithException() throws CheckedFutureException {
    Transformer<Integer, Future<Integer>> t = new Transformer<Integer, Future<Integer>>() {
      @Override
      public Future<Integer> onException(Throwable ex) {
        return null;
      }

      @Override
      public Future<Integer> onValue(Integer value) {
        assertEquals(new Integer(1), value);
        throw new TestException();
      }
    };
    get(Future.value(1).transformWith(t));
  }

  /*** biMap ***/

  @Test
  public void biMap() throws CheckedFutureException {
    Future<Integer> f = Future.value(1).biMap(Future.value(2), (a, b) -> a + b);
    assertEquals(new Integer(3), get(f));
  }

  @Test(expected = ArithmeticException.class)
  public void biMapException() throws CheckedFutureException {
    Future<Integer> f = Future.value(1).biMap(Future.value(2), (a, b) -> 1 / 0);
    get(f);
  }

  /*** biFlatMap ***/

  @Test
  public void biFlatMap() throws CheckedFutureException {
    Future<Integer> f = Future.value(1).biFlatMap(Future.value(2), (a, b) -> Future.value(a + b));
    assertEquals(new Integer(3), get(f));
  }

  @Test(expected = ArithmeticException.class)
  public void biFlatMapException() throws CheckedFutureException {
    Future<Integer> f = Future.value(1).biFlatMap(Future.value(2), (a, b) -> Future.apply(() -> 1 / 0));
    get(f);
  }

  /*** onSuccess ***/

  @Test
  public void onSuccess() throws CheckedFutureException {
    AtomicInteger result = new AtomicInteger(0);
    Future<Integer> future = Future.value(1).onSuccess(i -> result.set(i));
    assertEquals(1, result.get());
    assertEquals(new Integer(1), get(future));
  }

  @Test
  public void onSuccessException() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).onSuccess(i -> {
      throw new RuntimeException();
    });
    assertEquals(new Integer(1), get(future));
  }

  /*** onFailure ***/

  @Test
  public void onFailure() throws CheckedFutureException {
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Future<Integer> future = Future.value(1).onFailure(exception::set);
    assertEquals(null, exception.get());
    assertEquals(new Integer(1), get(future));
  }

  @Test
  public void onFailureException() throws CheckedFutureException {
    Future<Integer> future = Future.value(1).onFailure(ex -> {
      throw new RuntimeException();
    });
    assertEquals(new Integer(1), get(future));
  }

  /*** respond ***/

  @Test
  public void respond() throws CheckedFutureException {
    AtomicInteger result = new AtomicInteger(-1);
    AtomicBoolean failure = new AtomicBoolean(false);
    final Responder<Integer> r = new Responder<Integer>() {
      @Override
      public void onException(Throwable ex) {
        failure.set(true);
      }

      @Override
      public void onValue(Integer value) {
        result.set(value);
      }
    };
    Future<Integer> f = Future.value(1).respond(r);
    assertEquals(new Integer(1), get(f));
    assertEquals(1, result.get());
    assertFalse(failure.get());
  }

  @Test
  public void respondException() throws CheckedFutureException {
    AtomicInteger result = new AtomicInteger(-1);
    AtomicBoolean failure = new AtomicBoolean(false);
    final Responder<Integer> r = new Responder<Integer>() {
      @Override
      public void onException(Throwable ex) {
        failure.set(true);
        throw new NullPointerException();
      }

      @Override
      public void onValue(Integer value) {
        result.set(value);
        throw new NullPointerException();
      }
    };
    Future.value(1).respond(r);
    assertEquals(1, result.get());
    assertFalse(failure.get());
  }

  /*** handle ***/

  @Test
  public void handle() {
    Future<Integer> future = Future.value(1);
    assertEquals(future, future.handle(t -> 2));
  }

  /*** rescue ***/

  @Test
  public void rescue() {
    Future<Integer> future = Future.value(1);
    assertEquals(future, future.rescue(t -> Future.value(2)));
  }

  /*** voided ***/

  @Test
  public void voided() throws CheckedFutureException {
    Future<Void> future = Future.value(1).voided();
    assertNull(get(future));
  }

  /*** get ***/

  @Test
  public void get() throws CheckedFutureException {
    Future<Integer> future = Future.value(1);
    assertEquals(new Integer(1), future.get(1, TimeUnit.MILLISECONDS));
  }

  @Test
  public void getZeroTimeout() throws CheckedFutureException {
    Future<Integer> future = Future.value(1);
    assertEquals(new Integer(1), future.get(0, TimeUnit.MILLISECONDS));
  }

  @Test
  public void getNegativeTimeout() throws CheckedFutureException {
    Future<Integer> future = Future.value(1);
    assertEquals(new Integer(1), future.get(-1, TimeUnit.MILLISECONDS));
  }

  /*** toString ***/

  @Test
  public void toStringInt() {
    String s = Future.value(1).toString();
    assertEquals("ValueFuture(1)", s);
  }

  @Test
  public void toStringNull() {
    String s = Future.value(null).toString();
    assertEquals("ValueFuture(null)", s);
  }

  /*** hashCode ***/

  @Test
  public void testHashCode() {
    assertEquals(Future.value(1).hashCode(), Future.value(1).hashCode());
  }

  @Test
  public void testHashCodeNotEquals() {
    assertNotEquals(Future.value(1).hashCode(), Future.value(2).hashCode());
  }

  @Test
  public void testHashCodeNull() {
    assertEquals(Future.value(null).hashCode(), Future.value(null).hashCode());
  }

  @Test
  public void testHashCodeNullNotEquals() {
    assertNotEquals(Future.value(null).hashCode(), Future.value(1).hashCode());
  }

  /*** equals ***/

  @Test
  public void testEquals() {
    assertEquals(Future.value(1), Future.value(1));
  }

  @Test
  public void testEqualsNotEquals() {
    assertNotEquals(Future.value(1), Future.value(2));
  }

  @Test
  public void testEqualsNotEqualsNull() {
    assertNotEquals(Future.value(1), null);
  }

  @Test
  public void testEqualsNotEqualsOtherClass() {
    assertNotEquals(Future.value(1), "s");
  }

  @Test
  public void testEqualsNull() {
    assertEquals(Future.value(null), Future.value(null));
  }

  @Test
  public void testEqualsNullNotEquals() {
    assertNotEquals(Future.value(null), Future.value(1));
  }
}
