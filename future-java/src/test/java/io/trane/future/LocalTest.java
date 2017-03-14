package io.trane.future;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class LocalTest {

  Local<Integer> l = Local.apply();

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
    try {
      int result = l.let(2, () -> {
        assertEquals(Optional.of(2), l.get());
        return 2;
      });
      assertEquals(2, result);
      assertEquals(Optional.of(1), l.get());
    } finally {
      assertEquals(Optional.of(1), l.get());
    }
  }

  @Test(expected = IllegalStateException.class)
  public void letFailure() {
    l.update(1);
    try {
      l.let(2, () -> {
        assertEquals(Optional.of(2), l.get());
        throw new IllegalStateException();
      });
    } finally {
      assertEquals(Optional.of(1), l.get());
    }
  }
  
  @Test
  public void getFresh() {
    assertEquals(Optional.empty(), (Local.<Integer>apply()).get());
  }

  @Test
  public void multipleLocals() {
    Local<Integer> l1 = Local.apply();
    Local<Integer> l2 = Local.apply();
    Local<Integer> l3 = Local.apply();
    Local<Integer> l4 = Local.apply();

    l1.set(Optional.of(1));
    l2.set(Optional.of(2));
    l3.set(Optional.of(3));
    l4.set(Optional.of(4));

    assertEquals(Optional.of(1), l1.get());
    assertEquals(Optional.of(2), l2.get());
    assertEquals(Optional.of(3), l3.get());
    assertEquals(Optional.of(4), l4.get());
  }
}
