package io.futures;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

abstract class SatisfiedFuture<T> extends Future<T> {
  @Override
  final Future<T> ensure(final Runnable r) {
    try {
      r.run();
    } catch (final Throwable ex) {
      // TODO logging
      NonFatalException.verify(ex);
    }
    return this;
  }

  @Override
  public final void raise(final Throwable ex) {
  }

  @Override
  final boolean isDefined() {
    return true;
  }

  @Override
  final void proxyTo(Promise<T> p) {
    if (!p.updateIfEmpty(this))
      throw new IllegalStateException("Cannot call proxyTo on an already satisfied Promise.");
  }

  @Override
  final Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    return this;
  }
}
