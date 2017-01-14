package io.futures;

abstract class SatisfiedFuture<T> extends Future<T> {
  @Override
  final Future<T> ensure(final Runnable r) {
    try {
      r.run();
    } catch (final RuntimeException ex) {
      // TODO logging
    }
    return this;
  }

  @Override
  public final void raise(final Exception ex) {
  }
  

  @Override
  final boolean isDefined() {
    return true;
  }
}
