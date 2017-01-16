package io.futures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

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
