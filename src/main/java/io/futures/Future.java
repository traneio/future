package io.futures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

interface Future<T> extends InterruptHandler {

  public static <T> Promise<T> promise() {
    final Optional<?>[] savedContext = Local.save();
    return new Promise<T>() {
      @Override
      protected final Optional<?>[] getSavedContext() {
        return savedContext;
      }
    };
  }

  public static <T> Promise<T> promise(final InterruptHandler handler) {
    final Optional<?>[] savedContext = Local.save();
    return new Promise<T>() {
      @Override
      protected final Optional<?>[] getSavedContext() {
        return savedContext;
      }

      @Override
      protected final InterruptHandler getInterruptHandler() {
        return handler;
      }
    };
  }

  public static <T> Promise<T> promise(final InterruptHandler h1, final InterruptHandler h2) {
    return promise(InterruptHandler.apply(h1, h2));
  }

  public static <T> Promise<T> promise(final List<? extends InterruptHandler> handlers) {
    return promise(InterruptHandler.apply(handlers));
  }

  /*** static ***/

  public static Future<Void> VOID = Future.value((Void) null);

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

  public static <T> Future<T> tailrec(final Supplier<Future<T>> sup) {
    final TailrecPromise<T> p = new TailrecPromise<>(sup);
    Scheduler.submit(p);
    return p;
  }

  static Future<? extends List<?>> emptyListInstance = Future.value(Collections.unmodifiableList(new ArrayList<>(0)));

  @SuppressWarnings("unchecked")
  public static <T> Future<List<T>> emptyList() {
    return (Future<List<T>>) emptyListInstance;
  }

  @SuppressWarnings("unchecked")
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
          return (Future<List<T>>) f;

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
        i++;
      }
      return p;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> Future<Void> join(final List<? extends Future<T>> list) {

    switch (list.size()) {

    case 0:
      return VOID;

    case 1:
      return list.get(0).voided();

    default:
      final Promise<Void> p = Future.promise(list);
      final JoinResponder<T> responder = new JoinResponder<>(p, list.size());

      for (final Future<T> f : list) {

        if (f instanceof ExceptionFuture)
          return (Future<Void>) f;

        f.respond(responder);
      }
      return p;
    }
  }

  public static <T> Future<Integer> selectIndex(final List<Future<T>> list) {

    switch (list.size()) {

    case 0:
      throw new IllegalArgumentException("Can't select from empty list.");

    case 1:
      list.get(0).map(v -> 0);

    default:
      final Promise<Integer> p = Future.promise(list);
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

  public static <T> Future<Void> whileDo(final Supplier<Boolean> cond, final Supplier<Future<T>> f) {
    return tailrec(() -> {
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

  <U, R> Future<R> biMap(Future<U> other, BiFunction<? super T, ? super U, ? extends R> f);

  <U, R> Future<R> biFlatMap(Future<U> other, BiFunction<? super T, ? super U, ? extends Future<R>> f);

  Future<T> ensure(Runnable r);

  Future<T> onSuccess(Consumer<? super T> c);

  Future<T> onFailure(Consumer<Throwable> c);

  Future<T> respond(Responder<? super T> r);

  Future<T> rescue(Function<Throwable, ? extends Future<T>> f);

  Future<T> handle(Function<Throwable, ? extends T> f);

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
}

final class TailrecPromise<T> extends Promise<T> implements Runnable {
  private final Supplier<Future<T>> sup;

  public TailrecPromise(final Supplier<Future<T>> sup) {
    super();
    this.sup = sup;
  }

  @Override
  public final void run() {
    become(sup.get());
  }
}

final class CollectPromise<T> extends Promise<List<T>> {

  private final Object[] results;
  private final AtomicInteger count;
  private final InterruptHandler interruptHandler;

  public CollectPromise(final List<? extends Future<T>> list) {
    final int size = list.size();
    results = new Object[size];
    count = new AtomicInteger(size);
    interruptHandler = InterruptHandler.apply(list);
  }

  @SuppressWarnings("unchecked")
  public final void collect(final T value, final int i) {
    results[i] = value;
    if (count.decrementAndGet() == 0)
      setValue((List<T>) Arrays.asList(results));
  }

  @Override
  protected final InterruptHandler getInterruptHandler() {
    return interruptHandler;
  }
};

final class JoinResponder<T> extends AtomicInteger implements Responder<T> {
  private static final long serialVersionUID = 5763037150431433940L;

  private final Promise<Void> p;

  public JoinResponder(final Promise<Void> p, final int size) {
    super(size);
    this.p = p;
  }

  @Override
  public final void onException(final Throwable ex) {
    p.setException(ex);
  }

  @Override
  public final void onValue(final T value) {
    if (decrementAndGet() == 0)
      p.become(Future.VOID);
  }
}
