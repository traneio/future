package io.trane.future;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public final class FuturePool {

  private final ExecutorService executor;

  public FuturePool(final ExecutorService executor) {
    this.executor = executor;
  }

  public final <T> Future<T> isolate(final Supplier<Future<T>> s) {
    return Future.flatten(async(s));
  }

  public final <T> Future<T> async(final Supplier<T> s) {
    try {
      final AsyncPromise<T> p = new AsyncPromise<>(s);
      executor.submit(p);
      return p;
    } catch (final RejectedExecutionException ex) {
      return Future.exception(ex);
    }
  }
}

final class AsyncPromise<T> extends Promise<T> implements Runnable {
  private final Supplier<T> s;

  public AsyncPromise(final Supplier<T> s) {
    super();
    this.s = s;
  }

  @Override
  public final void run() {
    setValue(s.get());
  }
}