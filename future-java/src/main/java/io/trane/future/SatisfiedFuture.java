package io.trane.future;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

interface SatisfiedFuture<T> extends Future<T> {

  static final Logger logger = Logger.getLogger(SatisfiedFuture.class.getName());

  @Override
  default Future<T> ensure(final Runnable r) {
    try {
      r.run();
    } catch (final Throwable ex) {
      logger.log(Level.WARNING, "Error when executing `respond` callback " + r + "for" + this, ex);
      NonFatalException.verify(ex);
    }
    return this;
  }

  @Override
  default void raise(final Throwable ex) {
  }

  @Override
  default Future<T> interruptible() {
    return this;
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
  default Future<T> delayed(final Duration delay, final ScheduledExecutorService scheduler) {
    final DelayedSatisfiedFuture<T> p = new DelayedSatisfiedFuture<>(this);
    scheduler.schedule(p, delay.toMillis(), TimeUnit.MILLISECONDS);
    return p;
  }

  @Override
  default Future<T> within(final Duration timeout, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    return this;
  }

  @Override
  default void join(final Duration timeout) {
  }
}

final class DelayedSatisfiedFuture<T> extends Promise<T> implements Runnable {

  private final SatisfiedFuture<T> result;

  public DelayedSatisfiedFuture(final SatisfiedFuture<T> result) {
    this.result = result;
  }

  @Override
  public final void run() {
    become(result);
  }
}
