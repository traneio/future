package io.trane.future;

import java.lang.reflect.Field;

interface Unsafe {

  static sun.misc.Unsafe instance = getDeclaredStaticField(sun.misc.Unsafe.class, "theUnsafe");

  @SuppressWarnings("unchecked")
  static <T> T getDeclaredStaticField(final Class<?> cls, final String name) {
    try {
      final Field f = cls.getDeclaredField(name);
      f.setAccessible(true);
      return (T) f.get(null);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  static long objectFieldOffset(final Class<?> cls, final String name) {
    try {
      return instance.objectFieldOffset(cls.getDeclaredField(name));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  static boolean compareAndSwapObject(final Object inst, final long fieldOffset, final Object oldValue,
      final Object newValue) {
    return instance.compareAndSwapObject(inst, fieldOffset, oldValue, newValue);
  }
}
