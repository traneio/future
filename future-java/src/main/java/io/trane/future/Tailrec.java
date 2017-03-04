package io.trane.future;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * `Tailrec` e ensures that recursive futures are stack-safe. 
 * 
 * Given the optimization that this library implements to 
 * avoid thread context switch, compositions are not 
 * stack-safe by default. It is necessary to wrap recursive 
 * computations with a `Tailrec` call:
 * 
 * ```java
 * public Future<Integer> factorial(Integer i) {
 *   Tailrec.apply(() ->
 *     if (i ==0) return Future.value(1);
 *     else factorial(i - 1).map(j -> i * j);
 *   )
 * }
 * ```
 * 
 * This is just an example, there's no reason to use `Future`s 
 * to implement a factorial function. Requiring the `Tailrec` 
 * call for recursive computations is a reasonable compromise 
 * since recursive futures are uncommon.
 * 
 * Even though the computation is wrapped by `Tailrec`, the 
 * execution still leverages the synchronous execution 
 * optimizations in batches. It executes the composition 
 * synchronously until it reaches the batch size and then uses 
 * a `Promise` to unwind the stack and then run the next batch.
 * 
 * The default batch size is defined by the system property 
 * "io.trane.future.defaultBatchSize", which is `512` by default. 
 * Alternatively, it is possible to set the batch size when 
 * calling `Tailrec.apply`:
 * 
 * ```java
 * public Future<Integer> factorial(Integer i) {
 *   Tailrec.apply(1024, () ->
 *     if (i ==0) return Future.value(1);
 *     else factorial(i - 1).map(j -> i * j);
 *   )
 * }
 * ```
 * 
 * Note that the first parameter defines the batch size as 
 * `1024`. Typically, the users do not need to tune this 
 * parameter unless a `StackOverflowException` is thrown or 
 * the user wants to increase the batch size for performance 
 * reasons. Larger batches tend to improve performance but 
 * increase the risk of a `StackOverflowException`.
 */
public final class Tailrec {

  private static final int DEFAULT_BATCH_SIZE = Optional
      .ofNullable(System.getProperty("io.trane.future.defaultBatchSize")).map(Integer::parseInt).orElse(512);

  private static final ThreadLocal<Tailrec> local = new ThreadLocal<Tailrec>() {
    @Override
    public Tailrec initialValue() {
      return new Tailrec();
    }
  };

  private ArrayList<Runnable> tasks = null;
  private int syncPermits = 0;

  private boolean running = false;

  private final boolean runSync() {
    return syncPermits-- > 0;
  }

  private final void submit(final Runnable r, final int batchSize) {
    syncPermits = batchSize;
    tasks = new ArrayList<>(1);
    tasks.add(r);
    if (!running) {
      run();
      syncPermits = 0;
    }
  }

  private final void run() {
    running = true;
    while (tasks != null) {
      final ArrayList<Runnable> pending = tasks;
      tasks = null;
      for (int i = 0; i < pending.size(); i++)
        pending.get(i).run();
    }
    running = false;
  }

  /**
   * Runs the recursive future using the default batch size.
   * 
   * @param sup  the supplier to be called on each recursion.
   * @return     the stack-safe recursive future.
   */
  public static final <T> Future<T> apply(final Supplier<Future<T>> sup) {
    return apply(DEFAULT_BATCH_SIZE, sup);
  }

  /**
   * Runs the recursive future using the custom batch size.
   * 
   * @param batchSize  the custom batch size.
   * @param sup        the supplier to be called on each recursion.
   * @return           the stack-safe recursive future.
   */
  public static final <T> Future<T> apply(final int batchSize, final Supplier<Future<T>> sup) {
    final Tailrec scheduler = local.get();
    if (scheduler.runSync())
      return sup.get();
    else {
      final TailrecPromise<T> p = new TailrecPromise<>(sup);
      scheduler.submit(p, batchSize);
      return p;
    }
  }
}

final class TailrecPromise<T> extends Promise<T> implements Runnable {
  private final Supplier<Future<T>> sup;

  protected TailrecPromise(final Supplier<Future<T>> sup) {
    super();
    this.sup = sup;
  }

  @Override
  public final void run() {
    become(sup.get());
  }
}
