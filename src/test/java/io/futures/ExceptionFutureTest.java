package io.futures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class ExceptionFutureTest {

  private <T> T get(Future<T> future) throws InterruptedException {
    return future.get(0, TimeUnit.MILLISECONDS);
  }

  private RuntimeException ex = new RuntimeException();

  /*** map ***/

  @Test
  public void map() throws InterruptedException {
    Future<Integer> future = Future.exception(ex);
    assertEquals(future, future.map(i -> i + 1));
  }

  /*** flatMap ***/

  @Test
  public void flatMap() throws InterruptedException {
    Future<Integer> future = Future.exception(ex);
    assertEquals(future, future.flatMap(i -> Future.value(i + 1)));
  }

  /*** onSuccess ***/

  @Test
  public void onSuccess() throws InterruptedException {
    Future<Integer> future = Future.exception(ex);
    assertEquals(future, future.onSuccess(i -> {
    }));
  }

  /*** onFailure ***/

  @Test(expected = RuntimeException.class)
  public void onFailure() throws InterruptedException {
    AtomicReference<RuntimeException> exception = new AtomicReference<>();
    Future<Integer> future = Future.<Integer>exception(ex).onFailure(exception::set);
    assertEquals(ex, exception.get());
    get(future);
  }

  @Test(expected = RuntimeException.class)
  public void onFailureException() throws InterruptedException {
    Future<Integer> future = Future.<Integer>exception(ex).onFailure(ex -> {
      throw new NullPointerException();
    });
    get(future);
  }

  /*** get ***/

  @Test(expected = RuntimeException.class)
  public void get() throws InterruptedException {
    Future<Integer> future = Future.exception(ex);
    future.get(1, TimeUnit.MILLISECONDS);
  }

  @Test(expected = RuntimeException.class)
  public void getZeroTimeout() throws InterruptedException {
    Future<Integer> future = Future.exception(ex);
    assertEquals(new Integer(1), future.get(0, TimeUnit.MILLISECONDS));
  }

  @Test(expected = RuntimeException.class)
  public void getNegativeTimeout() throws InterruptedException {
    Future<Integer> future = Future.exception(ex);
    assertEquals(new Integer(1), future.get(-1, TimeUnit.MILLISECONDS));
  }

  /*** hashCode ***/

  @Test
  public void testHashCode() {
    assertEquals(Future.exception(ex).hashCode(), Future.exception(ex).hashCode());
  }

  @Test
  public void testHashCodeNotEquals() {
    assertNotEquals(Future.exception(ex).hashCode(), Future.exception(new NullPointerException()).hashCode());
  }

  @Test
  public void testHashCodeNull() {
    assertEquals(Future.exception(null).hashCode(), Future.exception(null).hashCode());
  }

  @Test
  public void testHashCodeNullNotEquals() {
    assertNotEquals(Future.exception(null).hashCode(), Future.exception(ex).hashCode());
  }

  /*** equals ***/

  @Test
  public void testEquals() {
    assertEquals(Future.exception(ex), Future.exception(ex));
  }

  @Test
  public void testEqualsNotEquals() {
    assertNotEquals(Future.exception(ex), Future.exception(new NullPointerException()));
  }

  @Test
  public void testEqualsNotEqualsNull() {
    assertNotEquals(Future.exception(ex), null);
  }

  @Test
  public void testEqualsNotEqualsOtherClass() {
    assertNotEquals(Future.exception(ex), "s");
  }

  @Test
  public void testEqualsNull() {
    assertEquals(Future.exception(null), Future.exception(null));
  }

  @Test
  public void testEqualsNullNotEquals() {
    assertNotEquals(Future.exception(null), Future.exception(ex));
  }
}
