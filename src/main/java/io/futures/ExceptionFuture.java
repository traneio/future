package io.futures;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

final class ExceptionFuture<T> implements SatisfiedFuture<T> {

  private final Throwable ex;

  ExceptionFuture(final Throwable ex) {
    NonFatalException.verify(ex);
    this.ex = ex;
  }

  @Override
  public final <R> Future<R> map(final Function<T, R> f) {
    return cast();
  }

  @Override
  public final <R> Future<R> flatMap(final Function<T, Future<R>> f) {
    return cast();
  }

  @Override
  public final Future<T> onSuccess(final Consumer<T> c) {
    return this;
  }

  @Override
  public final Future<T> onFailure(final Consumer<Throwable> c) {
    try {
      c.accept(ex);
    } catch (final Throwable ex) {
      // TODO logging
      NonFatalException.verify(ex);
    }
    return this;
  }

  @Override
  public final Future<T> rescue(final Function<Throwable, Future<T>> f) {
    try {
      return f.apply(ex);
    } catch (final Throwable ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  @Override
  public final Future<T> handle(final Function<Throwable, T> f) {
    try {
      return Future.value(f.apply(ex));
    } catch (final Throwable ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  @Override
  public final Future<Void> voided() {
    return cast();
  }
  
  @Override
  public final Future<T> delayed(long delay, TimeUnit timeUnit, ScheduledExecutorService scheduler) {
    final Promise<T> p = new Promise<>(this);
    scheduler.schedule(() -> p.setException(ex), delay, timeUnit);
    return p;
  }

  @Override
  public final T get(final long timeout, final TimeUnit unit) throws CheckedFutureException {
    if (ex instanceof RuntimeException)
      throw (RuntimeException) ex;
    if (ex instanceof Error)
      throw (Error) ex;
    else
      throw new CheckedFutureException(ex);
  }

  @SuppressWarnings("unchecked")
  private final <R> Future<R> cast() {
    return (Future<R>) this;
  }
  
  @Override
  public String toString() {
    return String.format("ExceptionFuture(%s)", ex);
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
