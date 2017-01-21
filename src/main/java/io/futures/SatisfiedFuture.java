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
  default void proxyTo(Promise<T> p) {
    if (!p.updateIfEmpty(this))
      throw new IllegalStateException("Cannot call proxyTo on an already satisfied Promise.");
  }

  @Override
  default Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    return this;
  }
}
