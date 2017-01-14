package io.futures;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

final class ExceptionFuture<T> extends SatisfiedFuture<T> {

  private final RuntimeException ex;

  ExceptionFuture(final RuntimeException ex) {
    this.ex = ex;
  }

  @Override
  final <R> Future<R> map(final Function<T, R> f) {
    return this.cast();
  }

  @Override
  final <R> Future<R> flatMap(final Function<T, Future<R>> f) {
    return this.cast();
  }

  @Override
  Future<T> onSuccess(final Consumer<T> c) {
    return this;
  }

  @Override
  Future<T> onFailure(final Consumer<RuntimeException> c) {
    try {
      c.accept(ex);
    } catch (final RuntimeException ex) {
      // TODO logging
    }
    return this;
  }

  @Override
  Future<T> rescue(Function<RuntimeException, Future<T>> f) {
    try {
      return f.apply(ex);
    } catch (RuntimeException ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  @Override
  Future<T> handle(Function<RuntimeException, T> f) {
    try {
      return Future.value(f.apply(ex));
    } catch (RuntimeException ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  @Override
  protected final T get(final long timeout, final TimeUnit unit) {
    throw ex;
  }

  @SuppressWarnings("unchecked")
  private final <R> Future<R> cast() {
    return (Future<R>) this;
  }

  @Override
  public final int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((ex == null) ? 0 : ex.hashCode());
    return result;
  }

  @Override
  public final boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final ExceptionFuture<?> other = (ExceptionFuture<?>) obj;
    if (ex == null) {
      if (other.ex != null)
        return false;
    } else if (!ex.equals(other.ex))
      return false;
    return true;
  }
}
