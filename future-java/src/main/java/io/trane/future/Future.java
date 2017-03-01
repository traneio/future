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
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * `Future` is an abstraction to deal with asynchronicity without having to use
 * callbacks directly or blocking threads. From the user perspective, a `Future`
 * can be in three states:
 * 
 * 1. Uncompleted 2. Completed with a value 3. Completed with an exception
 * 
 * Instead of exposing this state to the user, `Future` provides combinators to
 * express computations that run once the `Future` completes. The results of
 * these combinators are `Future` instances that can be used to perform other
 * transformations, giving the user a powerful tool to express complex chains of
 * asynchronous transformations.
 * 
 * Let's say that we need to call a remote service to get the username given an
 * id:
 * 
 * Future<User> user = userService.get(userId);
 * 
 * It's possible to apply the `map` transformation that produces a `Future` for
 * the username string:
 * 
 * Future<String> username = user.map(user -> user.username);
 * 
 * Let's say that now we need to call a service to validate the username string.
 * This is the result if we use the `map` combinator for it:
 * 
 * Future<Future<Boolean>> isValid = username.map(username ->
 * usernameService.isValid(username));
 * 
 * Given that the lambda expression calls another service and returns a
 * `Future`, the produced result is a nested future (`Future<Future<Boolean>>`).
 * One alternative to flatten this nested result is using `Future.flatten`:
 * 
 * Future<Boolean> isValidFlat = Future.flatten(isValid);
 * 
 * There's a convenient combinator called `flatMap` that applies both `map` and
 * `Future.flatten` at once:
 * 
 * Future<Boolean> isValid = username.flatMap(username ->
 * usernameService.isValid(username));
 * 
 * The API contains many other useful operators to deal with exceptions,
 * collections of futures, and others.
 *
 * @param <T>
 *          the type of the asynchronous computation result
 */
public interface Future<T> extends InterruptHandler {

  /**
   * A constant void `Future`. Useful to represent completed side effects.
   */
  static final Future<Void> VOID = Future.value((Void) null);

  /**
   * @return a `Future` that is never satisfied.
   */
  public static <T> Future<T> never() {
    return FutureConstants.NEVER.unsafeCast();
  }

  /**
   * Creates a future with the result of the supplier. Note that the supplier is
   * executed by the current thread. Use `FuturePool.async` to execute the
   * supplier on a separate thread.
   * 
   * @param s
   *          a supplier that may throw an exception
   * @return a satisfied future if `s` doesn't throw or else a failed future
   *         with the supplier exception.
   */
  public static <T> Future<T> apply(final Supplier<T> s) {
    try {
      return new ValueFuture<>(s.get());
    } catch (final Throwable ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  /**
   * Creates a successful `Future`.
   * 
   * @param v
   *          the value that satisfies the `Future`.
   * @return the successful `Future`.
   */
  public static <T> Future<T> value(final T v) {
    return new ValueFuture<>(v);
  }

  /**
   * Creates a failed `Future`.
   * 
   * @param ex
   *          the failure.
   * @return the failed `Future`.
   */
  public static <T> Future<T> exception(final Throwable ex) {
    return new ExceptionFuture<>(ex);
  }

  /**
   * Flattens a nested `Future<Future<T>>` to `Future<T>`. The usage of this
   * method indicates a code smell: a `map` may have been used instead of
   * `flatMap`. There are genuine scenarios where `flatten` is required, though.
   * 
   * @param fut
   *          the nested `Future`
   * @return the flat `Future`
   */
  public static <T> Future<T> flatten(final Future<Future<T>> fut) {
    return fut.flatMap(f -> f);
  }

  /**
   * @return a satisfied `Future` with an immutable empty list.
   */
  public static <T> Future<List<T>> emptyList() {
    return FutureConstants.EMPTY_LIST.unsafeCast();
  }

  /**
   * @return a satisfied `Future` with an empty optional.
   */
  public static <T> Future<Optional<T>> emptyOptional() {
    return FutureConstants.EMPTY_OPIONAL.unsafeCast();
  }

  /**
   * Transforms a list of `Future`s (`List<Future<T>>`) into a `Future` of a
   * list (`Future<List<T>>`).
   * 
   * @param list
   *          the futures to collect from
   * @return a `Future` that is satisfied with the `Future` results.
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
   * This method is similar to `collect`, but it discards the result of the
   * `Future`s. It's useful to wait for a list of pending `Future` side effects.
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
   * Selects the index of the first satisfied `Future`.
   * 
   * @param list
   *          the list of futures to select from
   * @return a `Future` with the index of the first satisfied `Future` of the
   *         list.
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
   * @return a `Future` that is satisfied with the result of the first future to
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
   * Maps the result of this `Future` to another value (Future<T> => Future<R>).
   * 
   * @param f
   *          the mapping function.
   * @return a future transformed by the mapping function.
   */
  <R> Future<R> map(Function<? super T, ? extends R> f);

  /**
   * Maps the result of this `Future` to another `Future` and then flattens the
   * result.
   * 
   * @param f
   *          the mapping function that returns another future instance.
   * @return a mapped and flattened future with the transformed result.
   */
  <R> Future<R> flatMap(Function<? super T, ? extends Future<R>> f);

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

  Future<T> interruptible();

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
