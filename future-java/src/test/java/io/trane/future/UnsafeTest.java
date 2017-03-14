package io.trane.future;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UnsafeTest {

  class TestClass {
    private volatile Object field = null;

    public Object getField() {
      return field;
    }
  }

  @Test
  public void unsafeInstance() {
    assertEquals(Unsafe.instance, Unsafe.getDeclaredStaticField(sun.misc.Unsafe.class, "theUnsafe"));
  }
  
  @Test(expected = RuntimeException.class)
  public void getDeclaredStaticField() {
    Unsafe.getDeclaredStaticField(TestClass.class, "wrongField");
  }

  @Test
  public void objectFieldOffset() throws NoSuchFieldException, SecurityException {
    long actual = Unsafe.objectFieldOffset(TestClass.class, "field");
    long expected = Unsafe.instance.objectFieldOffset(TestClass.class.getDeclaredField("field"));
    assertEquals(actual, expected);
  }

  @Test(expected = RuntimeException.class)
  public void objectFieldOffsetException() {
    Unsafe.objectFieldOffset(TestClass.class, "wrongField");
  }
}
