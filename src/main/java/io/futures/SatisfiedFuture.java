package io.futures;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

interface SatisfiedFuture<T> extends Future<T> {
  @Override
  default Future<T> ensure(final Runnable r) {
    try {
      r.run();
    } catch (final Throwable ex) {
      // TODO logging
      NonFatalException.verify(ex);
    }
    return this;
  }

  @Override
  default void raise(final Throwable ex) {
  }

  @Override
  default boolean isDefined() {
    return true;
  }

  @Override
  default void proxyTo(final Promise<T> p) {
    if (!p.becomeIfEmpty(this))
      throw new IllegalStateException("Cannot call proxyTo on an already satisfied Promise.");
  }

  @Override
  default Future<T> delayed(final long delay, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    final DelayedSatisfiedFuture<T> p = new DelayedSatisfiedFuture<>(this);
    scheduler.schedule(p, delay, timeUnit);
    return p;
  }

  @Override
  default Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    return this;
  }
}

class DelayedSatisfiedFuture<T> extends Promise<T> implements Runnable {

  private final SatisfiedFuture<T> result;

  public DelayedSatisfiedFuture(final SatisfiedFuture<T> result) {
    super(result);
    this.result = result;
  }

  @Override
  public void run() {
    become(result);
  }
}
