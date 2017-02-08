package io.trane.future;

import java.util.Optional;
import java.util.function.Supplier;

public final class Local<T> {

  public static final <T> Local<T> apply() {
    return new Local<>();
  }

  private static final Optional<?>[] EMPTY = new Optional<?>[0];
  private static ThreadLocal<Optional<?>[]> threadLocal = null;
  private static int size = 0;

  private Local() {
  }

  protected static final Optional<?>[] save() {
    if (threadLocal == null)
      return EMPTY;
    else
      return threadLocal.get();
  }

  protected static final void restore(final Optional<?>[] saved) {
    if (threadLocal != null)
      threadLocal.set(saved);
  }

  private synchronized static final int newPosition() {
    if (threadLocal == null)
      threadLocal = new ThreadLocal<>();
    return size++;
  }

  private final int position = newPosition();

  public final void update(final T value) {
    set(Optional.of(value));
  }

  public final void set(final Optional<T> opt) {
    Optional<?>[] ctx = threadLocal.get();

    if (ctx == null)
      ctx = new Optional<?>[size];
    else {
      final Optional<?>[] oldCtx = ctx;
      ctx = new Optional<?>[size];
      System.arraycopy(oldCtx, 0, ctx, 0, oldCtx.length);
    }

    ctx[position] = opt;
    threadLocal.set(ctx);
  }

  @SuppressWarnings("unchecked")
  public final Optional<T> get() {
    final Optional<?>[] ctx = threadLocal.get();
    if (ctx == null || ctx.length <= position)
      return Optional.empty();

    final Optional<?> v = ctx[position];
    if (v == null)
      return Optional.empty();
    else
      return (Optional<T>) v;
  }

  public final <U> U let(final T value, final Supplier<U> s) {
    final Optional<T> saved = get();
    set(Optional.of(value));
    try {
      return s.get();
    } finally {
      set(saved);
    }
  }
}
