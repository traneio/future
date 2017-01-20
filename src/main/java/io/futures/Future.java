package io.futures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

abstract class Future<T> implements InterruptHandler {

  /*** static ***/

  public static final Future<Void> VOID = Future.value((Void) null);

  public static final <T> Future<T> apply(final Supplier<T> s) {
    try {
      return new ValueFuture<>(s.get());
    } catch (final Throwable ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  public static final <T> Future<T> value(final T v) {
    return new ValueFuture<>(v);
  }

  public static final <T> Future<T> exception(final Throwable ex) {
    return new ExceptionFuture<>(ex);
  }

  public static final <T> Future<T> flatten(final Future<Future<T>> fut) {
    return fut.flatMap(f -> f);
  }

  public static final <T> Future<T> tailrec(final Supplier<Future<T>> sup) {
    final Promise<T> p = new Promise<>();
    Scheduler.submit(() -> {
      p.become(sup.get());
    });
    return p;
  }

  private static final Future<List<?>> emptyListInstance = Future
      .value(Collections.unmodifiableList(new ArrayList<>(0)));

  @SuppressWarnings("unchecked")
  public static <T> Future<List<T>> emptyList() {
    return emptyListInstance.map(l -> (List<T>) l);
  }

  @SuppressWarnings("unchecked")
  public static final <T> Future<List<T>> collect(final List<? extends Future<T>> list) {
    if (list.isEmpty())
      return emptyList();
    else {
      final int size = list.size();
      final Promise<List<T>> p = new Promise<>(list);
      final Object[] results = new Object[size];
      final AtomicInteger count = new AtomicInteger(size);

      int i = 0;
      for (final Future<T> f : list) {

        if (f instanceof ExceptionFuture)
          return (Future<List<T>>) f;

        f.onFailure(p::setException);

        final int ii = i;
        f.onSuccess(v -> {
          results[ii] = v;
          if (count.decrementAndGet() == 0)
            p.setValue((List<T>) Arrays.asList(results));
        });

        i++;
      }
      return p;
    }
  }

  @SuppressWarnings("unchecked")
  public static final <T> Future<Void> join(final List<? extends Future<T>> list) {
    if (list.isEmpty())
      return VOID;
    else {
      final AtomicInteger count = new AtomicInteger(list.size());
      final Promise<Void> p = new Promise<>(list);
      final Consumer<T> decrement = v -> {
        if (count.decrementAndGet() == 0)
          p.update(VOID);
      };
      final Consumer<Throwable> fail = ex -> {
        p.setException(ex);
      };
      for (final Future<T> f : list) {

        if (f instanceof ExceptionFuture)
          return (Future<Void>) f;

        f.onSuccess(decrement);
        f.onFailure(fail);
      }
      return p;
    }
  }

  public static final <T> Future<Integer> selectIndex(final List<Future<T>> list) {

    if (list.isEmpty())
      throw new IllegalArgumentException("Can't select from empty list.");

    final Promise<Integer> p = new Promise<>(list);
    int i = 0;
    for (final Future<?> f : list) {

      if (f instanceof SatisfiedFuture)
        return Future.value(i);

      final int ii = i;
      f.ensure(() -> p.updateIfEmpty(Future.value(ii)));
      i++;
    }
    return p;
  }

  public static final <T> Future<Void> whileDo(final Supplier<Boolean> cond, final Supplier<Future<T>> f) {
    return tailrec(() -> {
      if (cond.get())
        return f.get().flatMap(r -> whileDo(cond, f));
      else
        return VOID;
    });
  }

  public static final <T> List<Future<T>> parallel(final int n, final Supplier<Future<T>> f) {
    final List<Future<T>> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++)
      result.add(f.get());
    return result;
  }

  /*** abstract ***/

  abstract <R> Future<R> map(Function<T, R> f);

  abstract <R> Future<R> flatMap(Function<T, Future<R>> f);

  abstract Future<T> ensure(Runnable r);

  abstract Future<T> onSuccess(Consumer<T> c);

  abstract Future<T> onFailure(Consumer<Throwable> c);

  abstract Future<T> rescue(Function<Throwable, Future<T>> f);

  abstract Future<T> handle(Function<Throwable, T> f);

  abstract boolean isDefined();

  abstract T get(long timeout, TimeUnit unit) throws CheckedFutureException;

  /*** concrete ***/

  public final void proxyTo(final Promise<T> p) {
    if (p.isDefined())
      throw new IllegalStateException("Cannot call proxyTo on an already satisfied Promise.");
    onSuccess(r -> p.setValue(r));
    onFailure(ex -> p.setException(ex));
  }

  public final Future<Void> voided() {
    return flatMap(v -> VOID);
  }

  public final Future<T> delayed(final long delay, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    final Promise<T> p = new Promise<>(this);
    scheduler.schedule(() -> p.become(Future.this), delay, timeUnit);
    return p;
  }

  public final Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    return within(timeout, timeUnit, scheduler, TimeoutException.stackless);
  }

  public final Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    if (isDefined() || timeout == Long.MAX_VALUE)
      return this;

    final Promise<T> p = new Promise<>(this);

    ScheduledFuture<Boolean> task = scheduler.schedule(() -> p.updateIfEmpty(new ExceptionFuture<>(exception)), timeout,
        timeUnit);

    onSuccess(r -> {
      task.cancel(false);
      p.updateIfEmpty(new ValueFuture<>(r));
    });

    onFailure(ex -> {
      task.cancel(false);
      p.updateIfEmpty(Future.exception(ex));
    });

    return p;
  }
}
