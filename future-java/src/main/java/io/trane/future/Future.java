package io.trane.future;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface Future<T> extends InterruptHandler {

  /*** static ***/

  public static Future<Void> VOID = Future.value((Void) null);

  public static <T> Future<T> never() {
    return new NoFuture<>();
  }

  public static <T> Future<T> apply(final Supplier<T> s) {
    try {
      return new ValueFuture<>(s.get());
    } catch (final Throwable ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  public static <T> Future<T> value(final T v) {
    return new ValueFuture<>(v);
  }

  public static <T> Future<T> exception(final Throwable ex) {
    return new ExceptionFuture<>(ex);
  }

  public static <T> Future<T> flatten(final Future<Future<T>> fut) {
    return fut.flatMap(f -> f);
  }

  static Future<? extends List<?>> emptyListInstance = Future.value(Collections.unmodifiableList(new ArrayList<>(0)));

  public static <T> Future<List<T>> emptyList() {
    return emptyListInstance.unsafeCast();
  }

  static Future<? extends Optional<?>> emptyOptionalInstance = Future.value(Optional.empty());

  public static <T> Future<Optional<T>> emptyOptional() {
    return emptyOptionalInstance.unsafeCast();
  }

  public static <T> Future<List<T>> collect(final List<? extends Future<T>> list) {

    switch (list.size()) {

    case 0:
      return emptyList();

    case 1:
      return list.get(0).map(Arrays::asList);

    case 2:
      return list.get(0).biMap(list.get(1), Arrays::asList);

    default:
      final CollectPromise<T> p = new CollectPromise<>(list);

      int i = 0;
      for (final Future<T> f : list) {

        if (f instanceof ExceptionFuture)
          return f.unsafeCast();

        if (f instanceof ValueFuture)
          p.collect(((ValueFuture<T>) f).value, i);
        else {
          final int ii = i;
          final Responder<T> responder = new Responder<T>() {
            @Override
            public final void onException(final Throwable ex) {
              p.setException(ex);
            }

            @Override
            public final void onValue(final T value) {
              p.collect(value, ii);
            }
          };

          f.respond(responder);
        }
        i++;
      }
      return p;
    }
  }

  public static <T> Future<Void> join(final List<? extends Future<T>> list) {

    switch (list.size()) {

    case 0:
      return VOID;

    case 1:
      return list.get(0).voided();

    default:
      final JoinPromise<T> p = new JoinPromise<>(list);

      for (final Future<T> f : list) {

        if (f instanceof ExceptionFuture)
          return f.voided();

        f.respond(p);
      }
      return p;
    }
  }

  public static <T> Future<Integer> selectIndex(final List<Future<T>> list) {

    switch (list.size()) {

    case 0:
      throw new IllegalArgumentException("Can't select from empty list.");

    case 1:
      return list.get(0).map(v -> 0);

    default:
      final Promise<Integer> p = Promise.apply(list);
      int i = 0;
      for (final Future<?> f : list) {

        if (f instanceof SatisfiedFuture)
          return Future.value(i);

        final int ii = i;
        f.ensure(() -> p.becomeIfEmpty(Future.value(ii)));
        i++;
      }
      return p;
    }
  }

  public static <T> Future<T> firstCompletedOf(final List<Future<T>> list) {
    FirstCompletedOfPromise<T> p = new FirstCompletedOfPromise<>(list);
    for (final Future<T> f : list)
      f.respond(p);
    return p;
  }

  public static <T> Future<Void> whileDo(final Supplier<Boolean> cond, final Supplier<Future<T>> f) {
    return Tailrec.apply(() -> {
      if (cond.get())
        return f.get().flatMap(r -> whileDo(cond, f));
      else
        return VOID;
    });
  }

  public static <T> List<Future<T>> parallel(final int n, final Supplier<Future<T>> f) {
    final List<Future<T>> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++)
      result.add(f.get());
    return result;
  }

  /*** methods ***/

  <R> Future<R> map(Function<? super T, ? extends R> f);

  <R> Future<R> flatMap(Function<? super T, ? extends Future<R>> f);

  Future<T> filter(Predicate<? super T> p);

  <R> Future<R> transform(Transformer<? super T, ? extends R> t);

  <R> Future<R> transformWith(Transformer<? super T, ? extends Future<R>> t);

  <U, R> Future<R> biMap(Future<U> other, BiFunction<? super T, ? super U, ? extends R> f);

  <U, R> Future<R> biFlatMap(Future<U> other, BiFunction<? super T, ? super U, ? extends Future<R>> f);

  Future<T> ensure(Runnable r);

  Future<T> onSuccess(Consumer<? super T> c);

  Future<T> onFailure(Consumer<Throwable> c);

  Future<T> respond(Responder<? super T> r);

  Future<T> rescue(Function<Throwable, ? extends Future<T>> f);

  Future<T> handle(Function<Throwable, ? extends T> f);

  Future<T> fallbackTo(Future<T> other);

  boolean isDefined();

  T get(long timeout, TimeUnit unit) throws CheckedFutureException;

  void join(long timeout, TimeUnit unit) throws CheckedFutureException;

  Future<Void> voided();

  Future<T> delayed(final long delay, final TimeUnit timeUnit, final ScheduledExecutorService scheduler);

  void proxyTo(final Promise<T> p);

  default Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    return within(timeout, timeUnit, scheduler, TimeoutException.stackless);
  }

  Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception);

  @SuppressWarnings("unchecked")
  default <R> Future<R> unsafeCast() {
    return (Future<R>) this;
  }
}

final class CollectPromise<T> extends Promise<List<T>> {

  private final Object[] results;
  private final AtomicInteger count;
  private final List<? extends Future<T>> list;

  public CollectPromise(final List<? extends Future<T>> list) {
    final int size = list.size();
    this.results = new Object[size];
    this.count = new AtomicInteger(size);
    this.list = list;
  }

  @SuppressWarnings("unchecked")
  public final void collect(final T value, final int i) {
    results[i] = value;
    if (count.decrementAndGet() == 0)
      setValue((List<T>) Arrays.asList(results));
  }

  @Override
  protected final InterruptHandler getInterruptHandler() {
    return InterruptHandler.apply(list);
  }
}

final class JoinPromise<T> extends Promise<Void> implements Responder<T> {
  private final AtomicInteger count;
  private final List<? extends Future<T>> list;

  public JoinPromise(final List<? extends Future<T>> list) {
    this.list = list;
    this.count = new AtomicInteger(list.size());
  }

  @Override
  public final void onException(final Throwable ex) {
    setException(ex);
  }

  @Override
  public final void onValue(final T value) {
    if (count.decrementAndGet() == 0)
      become(Future.VOID);
  }

  @Override
  protected InterruptHandler getInterruptHandler() {
    return InterruptHandler.apply(list);
  }
}

final class FirstCompletedOfPromise<T> extends Promise<T> implements Responder<T> {

  private final List<? extends Future<T>> list;

  public FirstCompletedOfPromise(final List<? extends Future<T>> list) {
    this.list = list;
  }

  @Override
  public final void onException(final Throwable ex) {
    becomeIfEmpty(Future.exception(ex));
  }

  @Override
  public final void onValue(final T value) {
    becomeIfEmpty(Future.value(value));
  }

  @Override
  protected InterruptHandler getInterruptHandler() {
    return InterruptHandler.apply(list);
  }
}
