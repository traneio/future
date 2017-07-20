package io.trane.future;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Promise is a Future that provides methods to set its result. They are useful
 * to interact with callback-based APIs like the ones that are typically
 * provided by network libraries. A promise can be created and returned
 * synchronously to the caller, but its completion is deferred until the value is
 * set, typically by a callback.
 * 
 * @param <T>
 *          the type of the asynchronous computation result
 */
public abstract class Promise<T> implements Future<T> {

  private static final long STATE_OFFSET = Unsafe.objectFieldOffset(Promise.class, "state");
  private static final Logger LOGGER = Logger.getLogger(Promise.class.getName());

  /**
   * Creates a promise that triggers the provided handlers in case it receives
   * an interrupt.
   * 
   * @param handlers  the list of handlers to be triggered.
   * @return          the new promise instance.
   */
  public static final <T> Promise<T> apply(final List<? extends InterruptHandler> handlers) {
    final Optional<?>[] savedContext = Local.save();
    if (savedContext.length == 0)
      return new Promise<T>() {
        @Override
        protected final Optional<?>[] getSavedContext() {
          return Local.EMPTY;
        }

        @Override
        protected final InterruptHandler getInterruptHandler() {
          return InterruptHandler.apply(handlers);
        }
      };
    else
      return new Promise<T>() {
        @Override
        protected final Optional<?>[] getSavedContext() {
          return savedContext;
        }

        @Override
        protected final InterruptHandler getInterruptHandler() {
          return InterruptHandler.apply(handlers);
        }
      };
  }

  /**
   * Creates a promise that triggers the provided handler in case it receives an
   * interrupt.
   * 
   * @param handler  the handler to be triggered.
   * @return         the new promise instance.
   */
  public static final <T> Promise<T> apply(final InterruptHandler handler) {
    final Optional<?>[] savedContext = Local.save();
    if (savedContext.length == 0)
      return new Promise<T>() {
        @Override
        protected final Optional<?>[] getSavedContext() {
          return Local.EMPTY;
        }

        @Override
        protected final InterruptHandler getInterruptHandler() {
          return handler;
        }
      };
    else
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

  /**
   * Creates a promise without an interrupt handler. Interrupt signals are
   * ignored by the created promise since there's no handler.
   * 
   * @return  the new promise instance.
   */
  public static final <T> Promise<T> apply() {
    final Optional<?>[] savedContext = Local.save();
    if (savedContext.length == 0)
      return new Promise<T>() {
        @Override
        protected final Optional<?>[] getSavedContext() {
          return Local.EMPTY;
        }
      };
    else
      return new Promise<T>() {
        @Override
        protected final Optional<?>[] getSavedContext() {
          return savedContext;
        }
      };
  }

  /**
   * Creates a new promise using a handler builder that it's based on the
   * promise under creation. This method allows the user to define handlers that
   * use the it's own promise.
   * 
   * @param handlerBuilder
   *          a builder that receives the new promise and returns the interrupt
   *          handler of the new promise.
   * @return the new promise.
   */
  public static final <T> Promise<T> create(final Function<Promise<T>, InterruptHandler> handlerBuilder) {
    final Optional<?>[] savedContext = Local.save();
    if (savedContext.length == 0)
      return new Promise<T>() {

        final InterruptHandler handler = handlerBuilder.apply(this);

        @Override
        protected final Optional<?>[] getSavedContext() {
          return Local.EMPTY;
        }

        @Override
        protected final InterruptHandler getInterruptHandler() {
          return handler;
        }
      };
    else
      return new Promise<T>() {

        final InterruptHandler handler = handlerBuilder.apply(this);

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

  protected static final <T> Promise<T> apply(final InterruptHandler h1, final InterruptHandler h2) {
    final Optional<?>[] savedContext = Local.save();
    return new Promise<T>() {
      @Override
      protected final Optional<?>[] getSavedContext() {
        return savedContext;
      }

      @Override
      protected final InterruptHandler getInterruptHandler() {
        return InterruptHandler.apply(h1, h2);
      }
    };
  }

  protected Promise() {
  }

  // Future<T> (Done) | Promise<T>|LinkedContinuation<?, T> (Linked) |
  // WaitQueue|Null (Pending)
  volatile Object state;

  protected InterruptHandler getInterruptHandler() {
    return null;
  }

  protected Optional<?>[] getSavedContext() {
    return null;
  }

  private final boolean cas(final Object oldState, final Object newState) {
    return Unsafe.compareAndSwapObject(this, STATE_OFFSET, oldState, newState);
  }

  /**
   * Becomes another future. This and result become the same: both are completed
   * with the same result and both receive the same interrupt signals.
   * 
   * @param result  the future to become.
   */
  public final void become(final Future<T> result) {
    if (!becomeIfEmpty(result))
      throw new IllegalStateException("Can't set result " + result + " for promise with state " + state);
  }

  /**
   * Becomes another future only if this promise is undefined. This and result
   * become the same: both are completed with the same result and both receive
   * the same interrupt signals.
   * 
   * @param result  the future to become.
   * @return        if the operation was successful
   */

  public final boolean becomeIfEmpty(final Future<T> result) {
    final Optional<?>[] savedContext = getSavedContext();
    Optional<?>[] originalContext;
    if (savedContext != null && (originalContext = Local.save()) != savedContext) {
      Local.restore(savedContext);
      try {
        return trySetState(result);
      } finally {
        Local.restore(originalContext);
      }
    } else
      return trySetState(result);
  }

  @SuppressWarnings("unchecked")
  public final boolean trySetState(final Future<T> result) {
    try {
      while (true) {
        final Object curr = state;
        if (curr instanceof SatisfiedFuture)
          return false;
        else if (curr instanceof Promise && !(curr instanceof Continuation))
          return ((Promise<T>) curr).becomeIfEmpty(result);
        else if (curr instanceof LinkedContinuation)
          return ((LinkedContinuation<?, T>) curr).becomeIfEmpty(result);
        else if (result instanceof Promise) {
          ((Promise<T>) result).compress().link(this);
          return true;
        } else if (cas(curr, result)) {
          if (curr != null)
            ((WaitQueue<T>) curr).flush(result);
          return true;
        }
      }
    } catch (final StackOverflowError ex) {
      if (!(this instanceof Continuation))
        LOGGER.log(Level.SEVERE,
            "FATAL: Stack overflow when satisfying promise, the promise and its continuations won't be satisfied. "
                + "Use `Future.tailrec` or increase the stack size (-Xss) if the future isn't recursive.",
            ex);
      throw ex;
    }
  }

  @SuppressWarnings("unchecked")
  protected final WaitQueue<T> safeFlush(final Future<T> result) {
    while (true) {
      final Object curr = state;
      if (curr instanceof Promise && !(curr instanceof Continuation))
        return ((Promise<T>) curr).safeFlush(result);
      else if (curr instanceof LinkedContinuation)
        return ((LinkedContinuation<?, T>) curr).safeFlush(result);
      else if (result instanceof Promise) {
        ((Promise<T>) result).compress().link(this);
        return null;
      } else if (cas(curr, result))
        return (WaitQueue<T>) curr;
    }
  }

  @SuppressWarnings("unchecked")
  private final void link(final Promise<T> target) {
    while (true) {
      final Object curr = state;
      if (curr instanceof SatisfiedFuture) {
        target.become((SatisfiedFuture<T>) curr);
        return;
      } else {
        Object newState;
        if (target instanceof Continuation)
          newState = new LinkedContinuation<>((Continuation<T, ?>) target);
        else
          newState = target;
        if (cas(curr, newState)) {
          if (curr != null)
            ((WaitQueue<T>) curr).forward(target);
          return;
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected final <R> Future<R> continuation(final Continuation<T, R> c) {
    while (true) {
      final Object curr = state;
      if (curr == null) {
        if (cas(curr, c))
          return c;
      } else if (curr instanceof WaitQueue) {
        if (cas(curr, ((WaitQueue<T>) curr).add(c)))
          return c;
      } else if (curr instanceof SatisfiedFuture) {
        c.flush((SatisfiedFuture<T>) curr);
        return c;
      } else if (curr instanceof Promise && !(curr instanceof Continuation))
        return ((Promise<T>) curr).continuation(c);
      else if (curr instanceof LinkedContinuation)
        return ((LinkedContinuation<?, T>) curr).continuation(c);
    }
  }

  @SuppressWarnings("unchecked")
  private final Promise<T> compress() {
    while (true) {
      final Object curr = state;
      if (curr instanceof Promise && !(curr instanceof Continuation)) { // Linked
        final Promise<T> target = ((Promise<T>) curr).compress();
        if (curr == target || cas(curr, target))
          return target;
      } else
        return this;
    }
  }

  /**
   * Completes this promise with value.
   * 
   * @param value  the result.
   */
  public final void setValue(final T value) {
    become(new ValueFuture<>(value));
  }

  /**
   * Completes this promise with a failure ex.
   * 
   * @param ex  the failure.
   */
  public final void setException(final Throwable ex) {
    become(new ExceptionFuture<>(ex));
  }

  /**
   * Raises an interrupt.
   * 
   * @param ex  the interrupt exception.
   */
  @SuppressWarnings("unchecked")
  @Override
  public final void raise(final Throwable ex) {
    InterruptHandler interruptHandler;
    final Object curr = state;
    if (curr instanceof SatisfiedFuture) // Done
      return;
    else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
      ((Promise<T>) curr).raise(ex);
    else if (curr instanceof LinkedContinuation)
      ((LinkedContinuation<?, T>) curr).raise(ex);
    else if ((interruptHandler = getInterruptHandler()) != null)
      interruptHandler.raise(ex);
  }

  @Override
  public Future<T> interruptible() {
    final Promise<T> r = Promise.create(p -> ex -> p.setException(ex));
    this.proxyTo(r);
    return r;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final boolean isDefined() {
    final Object curr = state;
    if (curr instanceof SatisfiedFuture) // Done
      return true;
    else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
      return ((Promise<T>) curr).isDefined();
    else if (curr instanceof LinkedContinuation)
      return ((LinkedContinuation<?, T>) curr).isDefined();
    else // Waiting
      return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final T get(final Duration timeout) throws CheckedFutureException {
    final Object curr = state;
    if (curr instanceof Future && !(curr instanceof Continuation) && ((Future<T>) curr).isDefined())
      return ((Future<T>) curr).get(Duration.ZERO);
    else if (curr instanceof LinkedContinuation && ((LinkedContinuation<?, T>) curr).isDefined())
      return ((LinkedContinuation<?, T>) curr).get(Duration.ZERO);
    else {
      join(timeout);
      return ((Future<T>) state).get(Duration.ZERO);
    }
  }

  // Inpired by Scala Future's CompletionLatch
  private static final class ReleaseOnRunLatch extends AbstractQueuedSynchronizer implements Runnable {

    private static final long serialVersionUID = -2448584187877095292L;

    @Override
    protected int tryAcquireShared(int arg) {
      if (getState() != 0)
        return 1;
      else
        return -1;
    }

    @Override
    protected boolean tryReleaseShared(int arg) {
      setState(1);
      return true;
    }

    @Override
    public final void run() {
      releaseShared(1);
    }
  }

  @Override
  public final void join(final Duration timeout) throws CheckedFutureException {
    final ReleaseOnRunLatch latch = new ReleaseOnRunLatch();
    ensure(latch);
    try {
      if (!latch.tryAcquireSharedNanos(1, timeout.toNanos()))
        throw new TimeoutException();
    } catch (final InterruptedException ex) {
      throw new CheckedFutureException(ex);
    }
  }

  private static class Map<T, R> extends Continuation<T, R> {
    private final Function<? super T, ? extends R> f;

    public Map(final Function<? super T, ? extends R> f) {
      this.f = f;
    }

    @Override
    final Future<R> apply(final Future<T> result) {
      return result.map(f);
    }
  }

  @Override
  public final <R> Future<R> map(final Function<? super T, ? extends R> f) {
    if (getInterruptHandler() != null)
      return continuation(new Map<T, R>(f) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new Map<T, R>(f));
  }

  private static class FlatMap<T, R> extends Continuation<T, R> {
    private final Function<? super T, ? extends Future<R>> f;

    public FlatMap(final Function<? super T, ? extends Future<R>> f) {
      this.f = f;
    }

    @Override
    final Future<R> apply(final Future<T> result) {
      return result.flatMap(f);
    }
  }

  @Override
  public final <R> Future<R> flatMap(final Function<? super T, ? extends Future<R>> f) {
    if (getInterruptHandler() != null)
      return continuation(new FlatMap<T, R>(f) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new FlatMap<T, R>(f));
  }

  private static class Transform<T, R> extends Continuation<T, R> {
    private final Transformer<? super T, ? extends R> t;

    public Transform(final Transformer<? super T, ? extends R> t) {
      this.t = t;
    }

    @Override
    final Future<R> apply(final Future<T> result) {
      return result.transform(t);
    }
  }

  @Override
  public <R> Future<R> transform(final Transformer<? super T, ? extends R> t) {
    if (getInterruptHandler() != null)
      return continuation(new Transform<T, R>(t) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new Transform<T, R>(t));
  }

  private static class TransformWith<T, R> extends Continuation<T, R> {
    private final Transformer<? super T, ? extends Future<R>> t;

    public TransformWith(final Transformer<? super T, ? extends Future<R>> t) {
      this.t = t;
    }

    @Override
    final Future<R> apply(final Future<T> result) {
      return result.transformWith(t);
    }
  }

  @Override
  public <R> Future<R> transformWith(final Transformer<? super T, ? extends Future<R>> t) {
    if (getInterruptHandler() != null)
      return continuation(new TransformWith<T, R>(t) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new TransformWith<T, R>(t));
  }

  private static class BiMap<T, U, R> extends Continuation<T, R> {
    private final BiFunction<? super T, ? super U, ? extends R> f;
    private final Future<U> other;

    public BiMap(final Future<U> other, final BiFunction<? super T, ? super U, ? extends R> f) {
      this.other = other;
      this.f = f;
    }

    @Override
    final Future<R> apply(final Future<T> result) {
      return result.biMap(other, f);
    }
  }

  @Override
  public <U, R> Future<R> biMap(final Future<U> other, final BiFunction<? super T, ? super U, ? extends R> f) {
    if (getInterruptHandler() != null)
      return continuation(new BiMap<T, U, R>(other, f) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return InterruptHandler.apply(Promise.this, other);
        }
      });
    else
      return continuation(new BiMap<T, U, R>(other, f));
  }

  private static class BiFlatMap<T, U, R> extends Continuation<T, R> {
    private final BiFunction<? super T, ? super U, ? extends Future<R>> f;
    private final Future<U> other;

    public BiFlatMap(final Future<U> other, final BiFunction<? super T, ? super U, ? extends Future<R>> f) {
      this.other = other;
      this.f = f;
    }

    @Override
    final Future<R> apply(final Future<T> result) {
      return result.biFlatMap(other, f);
    }
  }

  @Override
  public <U, R> Future<R> biFlatMap(final Future<U> other,
      final BiFunction<? super T, ? super U, ? extends Future<R>> f) {
    if (getInterruptHandler() != null)
      return continuation(new BiFlatMap<T, U, R>(other, f) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return InterruptHandler.apply(Promise.this, other);
        }
      });
    else
      return continuation(new BiFlatMap<T, U, R>(other, f));
  }

  private static class Ensure<T> extends Continuation<T, T> {
    private final Runnable f;

    public Ensure(final Runnable f) {
      this.f = f;
    }

    @Override
    final Future<T> apply(final Future<T> result) {
      return result.ensure(f);
    }
  }

  @Override
  public final Future<T> ensure(final Runnable f) {
    if (getInterruptHandler() != null)
      return continuation(new Ensure<T>(f) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new Ensure<T>(f));
  }

  private static class OnSuccess<T> extends Continuation<T, T> {
    private final Consumer<? super T> c;

    public OnSuccess(final Consumer<? super T> c) {
      this.c = c;
    }

    @Override
    final Future<T> apply(final Future<T> result) {
      return result.onSuccess(c);
    }
  }

  @Override
  public final Future<T> onSuccess(final Consumer<? super T> c) {
    if (getInterruptHandler() != null)
      return continuation(new OnSuccess<T>(c) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new OnSuccess<T>(c));
  }

  private static class OnFailure<T> extends Continuation<T, T> {
    private final Consumer<Throwable> c;

    public OnFailure(final Consumer<Throwable> c) {
      this.c = c;
    }

    @Override
    final Future<T> apply(final Future<T> result) {
      return result.onFailure(c);
    }
  }

  @Override
  public final Future<T> onFailure(final Consumer<Throwable> c) {
    if (getInterruptHandler() != null)
      return continuation(new OnFailure<T>(c) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new OnFailure<T>(c));
  }

  private static class Respond<T> extends Continuation<T, T> {
    private final Responder<? super T> r;

    public Respond(final Responder<? super T> r) {
      this.r = r;
    }

    @Override
    final Future<T> apply(final Future<T> result) {
      return result.respond(r);
    }
  }

  @Override
  public final Future<T> respond(final Responder<? super T> r) {
    if (getInterruptHandler() != null)
      return continuation(new Respond<T>(r) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new Respond<T>(r));
  }

  private static class Rescue<T> extends Continuation<T, T> {
    private final Function<Throwable, ? extends Future<T>> f;

    public Rescue(final Function<Throwable, ? extends Future<T>> f) {
      this.f = f;
    }

    @Override
    final Future<T> apply(final Future<T> result) {
      return result.rescue(f);
    }
  }

  @Override
  public final Future<T> rescue(final Function<Throwable, ? extends Future<T>> f) {
    if (getInterruptHandler() != null)
      return continuation(new Rescue<T>(f) {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new Rescue<T>(f));
  }

  private static class Voided<T> extends Continuation<T, Void> {
    @Override
    final Future<Void> apply(final Future<T> result) {
      return result.voided();
    }
  }

  @Override
  public final Future<Void> voided() {
    if (getInterruptHandler() != null)
      return continuation(new Voided<T>() {
        @Override
        protected final InterruptHandler getInterruptHandler() {
          return Promise.this;
        }
      });
    else
      return continuation(new Voided<T>());
  }

  private final class DelayedPromise extends Promise<T> implements Runnable {
    @Override
    public final void run() {
      become(Promise.this);
    }

    @Override
    protected final InterruptHandler getInterruptHandler() {
      return Promise.this;
    }
  }

  @Override
  public final Future<T> delayed(final Duration delay, final ScheduledExecutorService scheduler) {
    final DelayedPromise p = new DelayedPromise();
    scheduler.schedule(p, delay.toMillis(), TimeUnit.MILLISECONDS);
    return p;
  }

  @Override
  public final void proxyTo(final Promise<T> p) {
    if (p.isDefined())
      throw new IllegalStateException("Cannot call proxyTo on an already satisfied Promise.");

    final Responder<T> r = new Responder<T>() {
      @Override
      public final void onException(final Throwable ex) {
        p.setException(ex);
      }

      @Override
      public final void onValue(final T value) {
        p.setValue(value);
      }
    };
    respond(r);
  }

  private static final class WithinPromise<T> extends Promise<T> implements Responder<T>, Runnable {

    private final InterruptHandler handler;
    private final ScheduledFuture<?> task;
    private final Throwable exception;

    public WithinPromise(final InterruptHandler handler, final Duration timeout,
        final ScheduledExecutorService scheduler, final Throwable exception) {
      this.handler = handler;
      this.task = scheduler.schedule(this, timeout.toMillis(), TimeUnit.MILLISECONDS);
      this.exception = exception;
    }

    @Override
    public final void onException(final Throwable ex) {
      task.cancel(false);
      becomeIfEmpty(Future.exception(ex));
    }

    @Override
    public final void onValue(final T value) {
      task.cancel(false);
      becomeIfEmpty(Future.value(value));
    }

    @Override
    public final void run() {
      handler.raise(exception);
      becomeIfEmpty(Future.exception(exception));
    }

    @Override
    protected final InterruptHandler getInterruptHandler() {
      return handler;
    }
  }

  @Override
  public final Future<T> within(final Duration timeout, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    if (timeout.toMillis() == Long.MAX_VALUE)
      return this;

    final WithinPromise<T> p = new WithinPromise<>(this, timeout, scheduler, exception);
    respond(p);
    return p;
  }

  protected String toStringPrefix() {
    return "Promise";
  }

  @Override
  public final String toString() {
    final Object curr = state;
    String stateString;
    if (curr instanceof SatisfiedFuture)
      stateString = curr.toString();
    else if ((curr instanceof Promise && !(curr instanceof Continuation)) || curr instanceof LinkedContinuation) // Linked
      stateString = String.format("Linked(%s)", curr.toString());
    else
      stateString = "Waiting";
    return String.format("%s(%s)@%s", toStringPrefix(), stateString, Integer.toHexString(hashCode()));
  }

}

abstract class Continuation<T, R> extends Promise<R> implements WaitQueue<T> {

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c) {
    return new WaitQueueHeadTail<>(c, this);
  }

  @Override
  public final void forward(final Promise<T> target) {
    target.continuation(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void flush(final Future<T> result) {
    Future<Object> r = (Future<Object>) result;
    WaitQueue<Object> q = (Continuation<Object, Object>) this;
    while (q instanceof Continuation) {
      final Continuation<Object, Object> c = (Continuation<Object, Object>) q;
      r = c.apply(r);
      q = c.safeFlush(r);
    }
    if (q != null)
      q.flush(r);
  }

  abstract Future<R> apply(Future<T> result);

  @Override
  protected String toStringPrefix() {
    return "Continuation";
  }
}

final class LinkedContinuation<T, R> {

  private final Continuation<T, R> continuation;

  public LinkedContinuation(final Continuation<T, R> continuation) {
    super();
    this.continuation = continuation;
  }

  public boolean isDefined() {
    return continuation.isDefined();
  }

  public final void raise(final Throwable ex) {
    continuation.raise(ex);
  }

  public final boolean becomeIfEmpty(final Future<R> result) {
    return continuation.becomeIfEmpty(result);
  }

  public final WaitQueue<R> safeFlush(final Future<R> result) {
    return continuation.safeFlush(result);
  }

  final <S> Future<S> continuation(final Continuation<R, S> c) {
    return continuation.continuation(c);
  }

  final R get(final Duration timeout) throws CheckedFutureException {
    return continuation.get(timeout);
  }

  @Override
  public final String toString() {
    return continuation.toString();
  }
}