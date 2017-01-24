package io.futures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

interface Future<T> extends InterruptHandler {

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

        final int ii = i;
        final Responder<T> responder = new Responder<T>() {
          @Override
          public void onException(final Throwable ex) {
            p.setException(ex);
          }

          @Override
          public void onValue(final T value) {
            results[ii] = value;
            if (count.decrementAndGet() == 0)
              p.setValue((List<T>) Arrays.asList(results));
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
    if (list.isEmpty())
      return VOID;
    else {
      final Promise<Void> p = new Promise<>(list);
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

    if (list.isEmpty())
      throw new IllegalArgumentException("Can't select from empty list.");

    final Promise<Integer> p = new Promise<>(list);
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

class TailrecPromise<T> extends Promise<T> implements Runnable {
  private final Supplier<Future<T>> sup;

  public TailrecPromise(final Supplier<Future<T>> sup) {
    super();
    this.sup = sup;
  }

  @Override
  public void run() {
    become(sup.get());
  }
}

class JoinResponder<T> extends AtomicInteger implements Responder<T> {
  private static final long serialVersionUID = 5763037150431433940L;

  private final Promise<Void> p;

  public JoinResponder(final Promise<Void> p, final int size) {
    super(size);
    this.p = p;
  }

  @Override
  public void onException(final Throwable ex) {
    p.setException(ex);
  }

  @Override
  public void onValue(final T value) {
    if (decrementAndGet() == 0)
      p.become(Future.VOID);
  }
}
