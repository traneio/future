package io.trane.future;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class NoFuture<T> implements Future<T> {

  @Override
  public void raise(final Throwable ex) {
  }

  @Override
  public Future<T> interruptible() {
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> map(final Function<? super T, ? extends R> f) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> flatMap(final Function<? super T, ? extends Future<R>> f) {
    return (Future<R>) this;
  }

  @Override
  public Future<T> filter(final Predicate<? super T> p) {
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> transform(final Transformer<? super T, ? extends R> t) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> transformWith(final Transformer<? super T, ? extends Future<R>> t) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, R> Future<R> biMap(final Future<U> other, final BiFunction<? super T, ? super U, ? extends R> f) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, R> Future<R> biFlatMap(final Future<U> other,
      final BiFunction<? super T, ? super U, ? extends Future<R>> f) {
    return (Future<R>) this;
  }

  @Override
  public Future<T> ensure(final Runnable r) {
    return this;
  }

  @Override
  public Future<T> onSuccess(final Consumer<? super T> c) {
    return this;
  }

  @Override
  public Future<T> onFailure(final Consumer<Throwable> c) {
    return this;
  }

  @Override
  public Future<T> respond(final Responder<? super T> r) {
    return this;
  }

  @Override
  public Future<T> rescue(final Function<Throwable, ? extends Future<T>> f) {
    return this;
  }

  @Override
  public Future<T> handle(final Function<Throwable, ? extends T> f) {
    return this;
  }

  @Override
  public boolean isDefined() {
    return false;
  }

  @Override
  public T get(final long timeout, final TimeUnit unit) throws CheckedFutureException {
    join(timeout, unit);
    throw new TimeoutException();
  }

  @Override
  public void join(final long timeout, final TimeUnit unit) throws CheckedFutureException {
    try {
      Thread.sleep(unit.toMillis(timeout));
    } catch (final InterruptedException e) {
      throw new CheckedFutureException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Future<Void> voided() {
    return (Future<Void>) this;
  }

  @Override
  public Future<T> delayed(final long delay, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    return this;
  }

  @Override
  public void proxyTo(final Promise<T> p) {
  }

  @Override
  public Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    return this;
  }
}
