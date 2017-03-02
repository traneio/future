package io.trane.future;

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

/**
 * Future is an abstraction to deal with asynchronicity without having to use
 * callbacks directly or blocking threads. From the user perspective, a future
 * can be in three states:
 *
 * 1. Uncompleted
 * 
 * 2. Completed with a value
 * 
 * 3. Completed with an exception
 *
 * Instead of exposing this state to the user, future provides combinators to
 * express computations that run once the future completes. The results of these
 * combinators are future instances that can be used to perform other
 * transformations, giving the user a powerful tool to express complex chains of
 * asynchronous transformations.
 *
 * @param <T>
 *          the type of the asynchronous computation result
 */
public interface Future<T> extends InterruptHandler {

  /**
   * A constant void future. Useful to represent completed side effects.
   */
  static final Future<Void> VOID = Future.value((Void) null);

  /**
   * @return a future that is never satisfied.
   * @param <T>
   *          the type of the never satisfied future.
   */
  public static <T> Future<T> never() {
    return FutureConstants.NEVER.unsafeCast();
  }

  /**
   * Creates a future with the result of the supplier. Note that the supplier is
   * executed by the current thread. Use FuturePool.async to execute the
   * supplier on a separate thread.
   *
   * @param s
   *          a supplier that may throw an exception
   * @param <T>
   *          the type of the value returned by the Supplier.
   * @return a satisfied future if s doesn't throw or else a failed future with
   *         the supplier exception.
   */
  public static <T> Future<T> apply(final Supplier<T> s) {
    try {
      return new ValueFuture<>(s.get());
    } catch (final Throwable ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  /**
   * Creates a successful future.
   *
   * @param v
   *          the value that satisfies the future.
   * @param <T>
   *          the type of the value.
   * @return the successful future.
   */
  public static <T> Future<T> value(final T v) {
    return new ValueFuture<>(v);
  }

  /**
   * Creates a failed future.
   *
   * @param ex
   *          the failure.
   * @param <T>
   *          the type of the failed future.
   * @return the failed future.
   */
  public static <T> Future<T> exception(final Throwable ex) {
    return new ExceptionFuture<>(ex);
  }

  /**
   * Flattens a nested future. The usage of this method indicates a code smell:
   * a map may have been used instead of flatMap. There are genuine scenarios
   * where flatten is required, though.
   *
   * @param fut
   *          the nested future
   * @param <T>
   *          the type of the future result
   * @return the flat future
   */
  public static <T> Future<T> flatten(final Future<Future<T>> fut) {
    return fut.flatMap(f -> f);
  }

  /**
   * @return a satisfied future with an immutable empty list.
   * 
   * @param <T>
   *          the list type
   */
  public static <T> Future<List<T>> emptyList() {
    return FutureConstants.EMPTY_LIST.unsafeCast();
  }

  /**
   * @return a satisfied future with an empty optional.
   * 
   * @param <T>
   *          the optional type
   */
  public static <T> Future<Optional<T>> emptyOptional() {
    return FutureConstants.EMPTY_OPIONAL.unsafeCast();
  }

  /**
   * Transforms a list of futures into a future of a list.
   *
   * @param list
   *          the futures to collect from
   * @return a future that is satisfied with the future results.
   */
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

  /**
   * This method is similar to collect, but it discards the result of the
   * futures. It's useful to wait for a list of pending future side effects.
   *
   * @param list
   *          the futures to wait for
   * @return a void future that indicates that all futures are satisfied.
   */
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

  /**
   * Selects the index of the first satisfied future.
   *
   * @param list
   *          the list of futures to select from
   * @return a future with the index of the first satisfied future of the list.
   */
  public static <T> Future<Integer> selectIndex(final List<Future<T>> list) {

    switch (list.size()) {

    case 0:
      return Future.exception(new IllegalArgumentException("Can't select from empty list."));

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

  /**
   * Finds the first future that completes.
   *
   * @param list
   *          futures to select from.
   * @return a future that is satisfied with the result of the first future to
   *         complete.
   */
  public static <T> Future<T> firstCompletedOf(final List<Future<T>> list) {
    switch (list.size()) {

    case 0:
      return Future.exception(new IllegalArgumentException("Can't select first completed future from empty list."));

    case 1:
      return list.get(0);

    default:
      final FirstCompletedOfPromise<T> p = new FirstCompletedOfPromise<>(list);
      for (final Future<T> f : list) {

        if (f instanceof SatisfiedFuture)
          return f;

        f.respond(p);
      }
      return p;
    }
  }

  /**
   * Executes the supplier function while the condition is valid.
   *
   * @param cond
   *          a supplier that determines if the while should stop or not
   * @param f
   *          the body of the while that is executed on each asynchronous
   *          iteration
   * @return a void future that is satisfied when the while stops.
   */
  public static <T> Future<Void> whileDo(final Supplier<Boolean> cond, final Supplier<Future<T>> f) {
    return Tailrec.apply(() -> {
      if (cond.get())
        return f.get().flatMap(r -> whileDo(cond, f));
      else
        return VOID;
    });
  }

  /**
   * Maps the result of this future to another value.
   *
   * @param f
   *          the mapping function.
   * @return a future transformed by the mapping function.
   */
  <R> Future<R> map(Function<? super T, ? extends R> f);

  /**
   * Maps the result of this future to another future and flattens the result.
   *
   * @param f
   *          the mapping function that returns another future instance.
   * @return a mapped and flattened future with the transformed result.
   */
  <R> Future<R> flatMap(Function<? super T, ? extends Future<R>> f);

  /**
   * Maps the result of this future using the provided Transformer. If this
   * future completes successfully, transformer.onValue is called. If this
   * future completes with an exception, transformer.onException is called.
   *
   * @param t
   *          a Transformer that applies the transformation.
   * @return a future tranformed by the Transformer.
   */
  <R> Future<R> transform(Transformer<? super T, ? extends R> t);

  /**
   * Maps the result of this future using a Transformer that returns another
   * future and flattens the result. If this future completes successfully,
   * transformer.onValue is called. If this future completes with an exception,
   * transformer.onException is called.
   *
   * @param t
   *          a Transformer that applies the transformation.
   * @return a future tranformed by the Transformer.
   */
  <R> Future<R> transformWith(Transformer<? super T, ? extends Future<R>> t);

  /**
   * Waits for this and other (running in parallel) and then maps the result
   * with f.
   *
   * @param other
   *          The other future that becomes the second parameter of f.
   * @param f
   *          the mapping function. The first parameter is the result of this
   *          and the second the result of other.
   * @return the mapped future.
   */
  <U, R> Future<R> biMap(Future<U> other, BiFunction<? super T, ? super U, ? extends R> f);

  /**
   * Waits for this and other (running in parallel), maps the result with f, and
   * flattens the result.
   *
   * @param other
   *          The other future that becomes the second parameter of f.
   * @param f
   *          the mapping function. The first parameter is the result of this
   *          and the second the result of other.
   * @return the mapped and flattened future.
   */
  <U, R> Future<R> biFlatMap(Future<U> other, BiFunction<? super T, ? super U, ? extends Future<R>> f);

  /**
   * Runs r when this future completes.
   *
   * @param r
   *          the Runnable to be executed.
   * @return a future that completes with the result of this after r is
   *         executed.
   */
  Future<T> ensure(Runnable r);

  /**
   * Executes the Consumer if this future completes successfully.
   *
   * @param c
   *          the Consumer to be executed.
   * @return a future that completes with the result of this after c is
   *         executed, if applicable.
   */
  Future<T> onSuccess(Consumer<? super T> c);

  /**
   * Executes the Consumer if this future completes with an exception.
   *
   * @param c
   *          the Consumer to be executed.
   * @return a future that completes with the result of this after c is
   *         executed, if applicable.
   */
  Future<T> onFailure(Consumer<Throwable> c);

  /**
   * Executes the Responder once this future completes. If this future completes
   * successfully, responder.onValue is called. If this future completes with an
   * exception, responder.onException is called.
   *
   * @param r
   *          the Responder to be executed.
   * @return a future that completes with the result of this after r is
   *         executed.
   */
  Future<T> respond(Responder<? super T> r);

  /**
   * If this future completes with an exception, applies the provided rescue
   * function and flattens the result.
   * 
   * Note that it's possible to return a Future.exception from f if the
   * exception can't be recovered.
   * 
   * @param f
   *          the function to be executed.
   * @return a future with the result of f.
   */
  Future<T> rescue(Function<Throwable, ? extends Future<T>> f);

  /**
   * Creates a future that will be completed with an exception if an interrupt
   * is received.
   * 
   * @return the interruptible future.
   */
  Future<T> interruptible();

  /**
   * Checks if this future is completed.
   * 
   * @return true if completed, false if not completed.
   */
  boolean isDefined();

  /**
   * Blocks the current thread until this future is satisfied and gets its
   * result. This method normally only useful for tests, **avoid it for
   * production code**.
   * 
   * @param timeout
   *          for how long the thread should wait for the result
   * @param unit
   *          time unit of the timeout
   * @return the result if the future completes successfully or an exception if
   *         the future completes with an exception.
   * @throws CheckedFutureException
   *           wrapper exception used when the result is a checked exception. If
   *           the exception is unchecked, it's thrown without this wrapper.
   */
  T get(long timeout, TimeUnit unit) throws CheckedFutureException;

  /**
   * Blocks the current thread until this future is satisfied. This method
   * normally only useful for tests, **avoid it for production code**.
   *
   * This method is similar to get but it doesn't return the future value nor
   * throws if the future completes with an exception.
   * 
   * @param timeout
   *          for how long the thread should wait for the result
   * @param unit
   *          time unit of the timeout
   * @throws CheckedFutureException
   *           wrapper exception used when the result is a checked exception. If
   *           the exception is unchecked, it's thrown without this wrapper.
   */
  void join(long timeout, TimeUnit unit) throws CheckedFutureException;

  /**
   * Creates a future that is satisfied with void when this future completes.
   * 
   * @return the voided future.
   */
  Future<Void> voided();

  /**
   * Delays the result of this future. It method doesn't take in consideration
   * how long this future takes to be completed. It assumes the state of this
   * future after delay, being it completed or not.
   * 
   * @param delay
   *          for how long the future must be delayed
   * @param timeUnit
   *          the time unit for delay
   * @param scheduler
   *          used to schedule an internal task to be executed after delay.
   * @return a future that assumes the state of this future after delay.
   */
  Future<T> delayed(final long delay, final TimeUnit timeUnit, final ScheduledExecutorService scheduler);

  /**
   * Proxies the result of this future, successful or not, to a Promise.
   * 
   * @param p
   *          the Promise to be updated once this future completes.
   */
  void proxyTo(final Promise<T> p);

  /**
   * Creates a future that fails with a TimeoutException if this future isn't
   * completed within the timeout.
   * 
   * @param timeout
   *          how long to wait for the result
   * @param timeUnit
   *          the time unit of timeout.
   * @param scheduler
   *          used to schedule an internal task after the timeout.
   * @return a future that completes with the result of this future within the
   *         timeout, a failed future otherwise.
   */
  default Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    return within(timeout, timeUnit, scheduler, TimeoutException.stackless);
  }

  /**
   * Creates a future that fails with a exception if this future isn't completed
   * within the timeout.
   * 
   * @param timeout
   *          how long to wait for the result
   * @param timeUnit
   *          the time unit of timeout.
   * @param scheduler
   *          used to schedule an internal task after the timeout.
   * @return a future that completes with the result of this future within the
   *         timeout, a failed future otherwise.
   */
  Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception);

  /**
   * Casts the result of this future. **Avoid this method** since it's unsafe
   * and can produce a future failed with a ClassCastException.
   * 
   * @return the casted future.
   */
  @SuppressWarnings("unchecked")
  default <R> Future<R> unsafeCast() {
    return (Future<R>) this;
  }
}

final class FutureConstants {

  private FutureConstants() {
  }

  static final Future<?> NEVER = new NoFuture<>();
  static final Future<? extends List<?>> EMPTY_LIST = Future.value(Collections.unmodifiableList(new ArrayList<>(0)));
  static final Future<? extends Optional<?>> EMPTY_OPIONAL = Future.value(Optional.empty());
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
