package io.futures;

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
}
