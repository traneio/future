package io.trane.future;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class Tailrec {

  private static final int defaultBatchSize = Optional
      .ofNullable(System.getProperty("io.trane.future.defaultBatchSize")).map(Integer::parseInt).orElseGet(() -> 512);

  private static final ThreadLocal<Tailrec> local = new ThreadLocal<Tailrec>() {
    @Override
    public Tailrec initialValue() {
      return new Tailrec();
    }
  };

  private List<Runnable> tasks = null;
  private int syncPermits = 0;

  private boolean running = false;

  private final boolean runSync() {
    return syncPermits-- > 0;
  }

  private final void submit(final Runnable r, final int batchSize) {
    syncPermits = batchSize;
    if (tasks == null)
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
      final List<Runnable> pending = tasks;
      tasks = null;
      for (int i = 0; i < pending.size(); i++)
        pending.get(i).run();
    }
    running = false;
  }

  public static final <T> Future<T> apply(final Supplier<Future<T>> sup) {
    return apply(defaultBatchSize, sup);
  }

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
