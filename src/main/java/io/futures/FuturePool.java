package io.futures;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public class FuturePool {

  private final ExecutorService executor;

  public FuturePool(final ExecutorService executor) {
    this.executor = executor;
  }

  public <T> Future<T> isolate(final Supplier<Future<T>> s) {
    return Future.flatten(async(s));
  }

  public <T> Future<T> async(final Supplier<T> s) {
    try {
      final AsyncPromise<T> p = new AsyncPromise<>(s);
      executor.submit(p);
      return p;
    } catch (final RejectedExecutionException ex) {
      return Future.exception(ex);
    }
  }
}

class AsyncPromise<T> extends Promise<T> implements Runnable {
  private final Supplier<T> s;
  public AsyncPromise(Supplier<T> s) {
    super();
    this.s = s;
  }
  @Override
  public void run() {
    setValue(s.get());
  }
}