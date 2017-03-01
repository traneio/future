package io.trane.future;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ExceptionFuture<T> implements SatisfiedFuture<T> {

  private static final Logger logger = Logger.getLogger(ExceptionFuture.class.getName());

  private final Throwable ex;

  protected ExceptionFuture(final Throwable ex) {
    NonFatalException.verify(ex);
    this.ex = ex;
  }

  @Override
  public final <R> Future<R> map(final Function<? super T, ? extends R> f) {
    return unsafeCast();
  }

  @Override
  public final <R> Future<R> flatMap(final Function<? super T, ? extends Future<R>> f) {
    return unsafeCast();
  }

  @Override
  public <R> Future<R> transform(final Transformer<? super T, ? extends R> t) {
    try {
      return new ValueFuture<>(t.onException(ex));
    } catch (final Throwable error) {
      return new ExceptionFuture<>(error);
    }
  }

  @Override
  public <R> Future<R> transformWith(final Transformer<? super T, ? extends Future<R>> t) {
    try {
      return t.onException(ex);
    } catch (final Throwable error) {
      return new ExceptionFuture<>(error);
    }
  }

  @Override
  public <U, R> Future<R> biMap(final Future<U> other, final BiFunction<? super T, ? super U, ? extends R> f) {
    return unsafeCast();
  }

  @Override
  public <U, R> Future<R> biFlatMap(final Future<U> other,
      final BiFunction<? super T, ? super U, ? extends Future<R>> f) {
    return unsafeCast();
  }

  @Override
  public final Future<T> onSuccess(final Consumer<? super T> c) {
    return this;
  }

  @Override
  public final Future<T> onFailure(final Consumer<Throwable> c) {
    try {
      c.accept(ex);
    } catch (final Throwable error) {
      logger.log(Level.WARNING, "Error executing `onFailure` callback " + c + "for" + this, ex);
      NonFatalException.verify(error);
    }
    return this;
  }

  @Override
  public final Future<T> respond(final Responder<? super T> r) {
    try {
      r.onException(ex);
    } catch (final Throwable error) {
      logger.log(Level.WARNING, "Error executing `respond` callback " + r + "for" + this, ex);
      NonFatalException.verify(error);
    }
    return this;
  }

  @Override
  public final Future<T> rescue(final Function<Throwable, ? extends Future<T>> f) {
    try {
      return f.apply(ex);
    } catch (final Throwable error) {
      return new ExceptionFuture<>(error);
    }
  }

  @Override
  public final Future<T> handle(final Function<Throwable, ? extends T> f) {
    try {
      return Future.value(f.apply(ex));
    } catch (final Throwable error) {
      return new ExceptionFuture<>(error);
    }
  }

  @Override
  public final Future<Void> voided() {
    return unsafeCast();
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

  @Override
  public final String toString() {
    return String.format("ExceptionFuture(%s)", ex);
  }

  @Override
  public final int hashCode() {
    return (ex == null) ? 0 : ex.hashCode();
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
