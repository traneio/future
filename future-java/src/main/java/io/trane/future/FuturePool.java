package io.trane.future;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * A `FuturePool` isolates portions of a `Future` composition on a 
 * separate thread pool.
 * 
 * Example:
 * 
 * ```java
 * FuturePool futurePool = FuturePool.apply(Executors.newCachedThreadPool());
 * 
 * Future<List<Token>> user = 
 *   documentService.get(docId)
 *     .flatMap(doc -> futurePool.async(tokenize(doc)))
 * ```
 * 
 * This feature useful to isolate cpu-intensive tasks and blocking 
 * operations. Please refer to the Java documentation to decide 
 * which type of executor is the best for the kind of task that 
 * needs to be performed. For instance, a `ForkJoinPool` is useful 
 * for cpu-intensive tasks, but can't be used for blocking operations.
 * 
 * The `FuturePool` also has the method `isolate` that isolates 
 * the execution of a `Future`:
 * 
 * ```java
 * FuturePool futurePool = FuturePool.apply(Executors.newCachedThreadPool());
 * 
 * Future<User> user = futurePool.isolate(userRepo.get(userId));
 * ```
 * 
 * `isolate` is just a shortcut for `async` + `Future.flatten`.
 */
public final class FuturePool {

  private final ExecutorService executor;

  /**
   * Creates a new future pool.
   * @param executor  the executor used to schedule tasks.
   * @return          the new future pool.
   */
  public static FuturePool apply(final ExecutorService executor) {
    return new FuturePool(executor);
  }

  private FuturePool(final ExecutorService executor) {
    this.executor = executor;
  }

  /**
   * Isolates the execution of a future on this future pool.
   * @param s the supplier that creates the future.
   * @return  the isolated future.
   */
  public final <T> Future<T> isolate(final Supplier<Future<T>> s) {
    return Future.flatten(async(s));
  }

  /**
   * Isolates the execution of the supplier on this future pool.
   * @param s  the supplier.
   * @return   a future with the result of the supplier.
   */
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
    try {
      setValue(s.get());
    } catch (final Throwable error) {
      NonFatalException.verify(error);
      setException(error);
    }
  }
}