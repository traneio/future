package io.futures;

import java.util.Optional;
import java.util.function.Supplier;

public final class Local<T> {

  private static final Optional<?>[] EMPTY = new Optional<?>[0];
  private static ThreadLocal<Optional<?>[]> local = null;
  private static int size = 0;

  protected static Optional<?>[] save() {
    if (local == null)
      return EMPTY;
    else
      return local.get();
  }

  protected static void restore(final Optional<?>[] saved) {
    if (local != null)
      local.set(saved);
  }

  private synchronized static int newPosition() {
    if (local == null)
      local = new ThreadLocal<>();
    return size++;
  }

  private final int position = newPosition();

  public void update(final T value) {
    set(Optional.of(value));
  }

  public void set(final Optional<T> opt) {
    Optional<?>[] ctx = local.get();

    if (ctx == null)
      ctx = new Optional<?>[size];
    else {
      final Optional<?>[] oldCtx = ctx;
      ctx = new Optional<?>[size];
      System.arraycopy(oldCtx, 0, ctx, 0, oldCtx.length);
    }

    ctx[position] = opt;
    local.set(ctx);
  }

  @SuppressWarnings("unchecked")
  public Optional<T> get() {
    final Optional<?>[] ctx = local.get();
    if (ctx == null || ctx.length <= position)
      return Optional.empty();

    final Optional<?> v = ctx[position];
    if (v == null)
      return Optional.empty();
    else
      return (Optional<T>) v;
  }

  public <U> U let(final T value, final Supplier<U> s) {
    final Optional<T> saved = get();
    set(Optional.of(value));
    try {
      return s.get();
    } finally {
      set(saved);
    }
  }
}
