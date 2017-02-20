package io.trane.future;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class NoFuture<T> implements Future<T> {

  @Override
  public void raise(Throwable ex) {
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> map(Function<? super T, ? extends R> f) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> flatMap(Function<? super T, ? extends Future<R>> f) {
    return (Future<R>) this;
  }

  @Override
  public Future<T> filter(Predicate<? super T> p) {
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> transform(Transformer<? super T, ? extends R> t) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> Future<R> transformWith(Transformer<? super T, ? extends Future<R>> t) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, R> Future<R> biMap(Future<U> other, BiFunction<? super T, ? super U, ? extends R> f) {
    return (Future<R>) this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, R> Future<R> biFlatMap(Future<U> other, BiFunction<? super T, ? super U, ? extends Future<R>> f) {
    return (Future<R>) this;
  }

  @Override
  public Future<T> ensure(Runnable r) {
    return this;
  }

  @Override
  public Future<T> onSuccess(Consumer<? super T> c) {
    return this;
  }

  @Override
  public Future<T> onFailure(Consumer<Throwable> c) {
    return this;
  }

  @Override
  public Future<T> respond(Responder<? super T> r) {
    return this;
  }

  @Override
  public Future<T> rescue(Function<Throwable, ? extends Future<T>> f) {
    return this;
  }

  @Override
  public Future<T> handle(Function<Throwable, ? extends T> f) {
    return this;
  }

  @Override
  public Future<T> fallbackTo(Future<T> other) {
    return this;
  }

  @Override
  public boolean isDefined() {
    return false;
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws CheckedFutureException {
    join(timeout, unit);
    throw new TimeoutException();
  }

  @Override
  public void join(long timeout, TimeUnit unit) throws CheckedFutureException {
    try {
      Thread.sleep(unit.toMillis(timeout));
    } catch (InterruptedException e) {
      throw new CheckedFutureException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Future<Void> voided() {
    return (Future<Void>) this;
  }

  @Override
  public Future<T> delayed(long delay, TimeUnit timeUnit, ScheduledExecutorService scheduler) {
    return this;
  }

  @Override
  public void proxyTo(Promise<T> p) {
  }

  @Override
  public Future<T> within(long timeout, TimeUnit timeUnit, ScheduledExecutorService scheduler, Throwable exception) {
    return this;
  }
}
