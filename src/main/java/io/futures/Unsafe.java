package io.futures;

import java.lang.reflect.Field;

public final class Unsafe {

  private static final sun.misc.Unsafe instance = getUnsafe();

  private static final sun.misc.Unsafe getUnsafe() {
    try {
      final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      return (sun.misc.Unsafe) f.get(null);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static final long objectFieldOffset(final Class<?> cls, final String name) {
    try {
      return instance.objectFieldOffset(cls.getDeclaredField(name));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static final boolean compareAndSwapObject(final Object inst, final long fieldOffset, final Object oldValue,
      final Object newValue) {
    return instance.compareAndSwapObject(inst, fieldOffset, oldValue, newValue);
  }
}
