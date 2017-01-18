package io.futures;

import static org.junit.Assert.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class LocalTest {

  Local<Integer> l = new Local<>();

  @Test
  public void getEmpty() {
    assertEquals(Optional.empty(), l.get());
  }

  @Test
  public void getEmptyFreshThread() throws InterruptedException {
    AtomicReference<Optional<Integer>> result = new AtomicReference<>();
    Thread t = new Thread() {
      @Override
      public void run() {
        result.set(l.get());
      }
    };
    t.start();
    t.join();
    assertEquals(Optional.empty(), result.get());
  }

  @Test
  public void saveAndRestore() {
    l.update(1);
    assertEquals(Optional.of(1), l.get());

    Optional<?>[] saved = Local.save();

    l.update(2);
    assertEquals(Optional.of(2), l.get());

    Local.restore(saved);

    l.update(1);
    assertEquals(Optional.of(1), l.get());
  }

  @Test
  public void updateAndGet() {
    l.update(1);
    assertEquals(Optional.of(1), l.get());
  }

  @Test
  public void setAndGetDefined() {
    l.set(Optional.of(1));
    assertEquals(Optional.of(1), l.get());
  }

  @Test
  public void setAndGetEmpty() {
    l.set(Optional.empty());
    assertEquals(Optional.empty(), l.get());
  }

  @Test
  public void setAndGetNull() {
    l.set(null);
    assertEquals(Optional.empty(), l.get());
  }

  @Test
  public void letSuccess() {
    l.update(1);
    int result = l.let(2, () -> {
      assertEquals(Optional.of(2), l.get());
      return 2;
    });
    assertEquals(2, result);
    assertEquals(Optional.of(1), l.get());
  }
  
  @Test(expected = IllegalStateException.class)
  public void letFailure() {
    l.update(1);
    l.let(2, () -> {
      assertEquals(Optional.of(2), l.get());
      throw new IllegalStateException();
    });
  }

}
