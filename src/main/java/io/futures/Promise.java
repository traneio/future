package io.futures;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class Promise<T> implements Future<T> {

  private static final long stateOffset = Unsafe.objectFieldOffset(Promise.class, "state");

  private final InterruptHandler interruptHandler;

  // Future<T> (Done) | Promise<T> (Linked) | WaitQueue|Null (Pending)
  private volatile Object state = null;

  private final Optional<?>[] savedContext = Local.save();

  public Promise() {
    super();
    this.interruptHandler = null;
  }

  public Promise(final InterruptHandler interruptHandler) {
    super();
    this.interruptHandler = interruptHandler;
  }

  public Promise(final List<? extends InterruptHandler> interruptHandlers) {
    super();
    this.interruptHandler = (ex) -> {
      for (final InterruptHandler handler : interruptHandlers)
        handler.raise(ex);
    };
  }

  private final boolean cas(final Object oldState, final Object newState) {
    return Unsafe.compareAndSwapObject(this, stateOffset, oldState, newState);
  }

  public final void update(final Future<T> result) {
    if (!updateIfEmpty(result))
      throw new IllegalStateException("Can't set result " + result + " for promise with state " + state);
  }

  @SuppressWarnings("unchecked")
  public final boolean updateIfEmpty(final Future<T> result) {
    final Optional<?>[] oldContext = Local.save();
    Local.restore(savedContext);
    try {
      while (true) {
        final Object curr = state;
        if (curr instanceof SatisfiedFuture) // Done
          return false;
        else if (curr instanceof Promise && !(curr instanceof Continuation))
          return ((Promise<T>) curr).updateIfEmpty(result);
        else if (result instanceof Promise) {
          become(result);
          return true;
        } else if (cas(curr, result)) { // Waiting
          WaitQueue.flush(curr, result);
          return true;
        }
      }
    } finally {
      Local.restore(oldContext);
    }
  }

  public final void setValue(final T value) {
    update(new ValueFuture<>(value));
  }

  public final void setException(final Throwable ex) {
    update(new ExceptionFuture<T>(ex));
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void raise(final Throwable ex) {
    if (state instanceof SatisfiedFuture) // Done
      return;
    else if (state instanceof Promise && !(state instanceof Continuation)) // Linked
      ((Promise<T>) state).raise(ex);
    else if (interruptHandler != null)
      interruptHandler.raise(ex);
  }

  @SuppressWarnings("unchecked")
  protected final <R> Future<R> continuation(final Continuation<T, R> c) {
    while (true) {
      final Object curr = state;
      if (curr instanceof SatisfiedFuture) { // Done
        c.flush((Future<T>) curr);
        return c;
      } else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
        return ((Promise<T>) curr).continuation(c);
      else if (cas(curr, WaitQueue.add(curr, c))) // Waiting
        return c;
    }
  }

  public final void become(final Future<T> target) {
    if (target instanceof Promise)
      ((Promise<T>) target).link(compress());
    else
      target.ensure(() -> update(target));
  }

  @SuppressWarnings("unchecked")
  private final Promise<T> compress() {
    final Object curr = state;
    while (true)
      if (curr instanceof Promise && !(curr instanceof Continuation)) { // Linked
        final Promise<T> target = ((Promise<T>) curr).compress();
        if (cas(curr, target))
          return target;
      } else
        return this;
  }

  @SuppressWarnings("unchecked")
  private final void link(final Promise<T> target) {
    while (true) {
      final Object curr = state;
      if (curr instanceof Promise && !(curr instanceof Continuation)) { // Linked
        if (cas(curr, target)) {
          ((Promise<T>) curr).link(target);
          return;
        }
      } else if (curr instanceof SatisfiedFuture) { // Done
        if (target.isDefined()) {
          if (!target.state.equals(curr))
            throw new IllegalStateException("Cannot link two Done Promises with differing values");
        } else
          target.update((SatisfiedFuture<T>) curr);

        return;
      } else if (cas(curr, target)) { // Waiting
        WaitQueue.forward(curr, target);
        return;
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public final boolean isDefined() {
    if (state instanceof SatisfiedFuture) // Done
      return true;
    else if (state instanceof Promise && !(state instanceof Continuation)) // Linked
      return ((Promise<T>) state).isDefined();
    else // Waiting
      return false;
  }

  private static final class ReleaseOnRunLatch extends CountDownLatch implements Runnable {
    public ReleaseOnRunLatch() {
      super(1);
    }

    @Override
    public final void run() {
      super.countDown();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public final T get(final long timeout, final TimeUnit unit) throws CheckedFutureException {
    final ReleaseOnRunLatch latch = new ReleaseOnRunLatch();
    ensure(latch);
    try {
      if (latch.await(timeout, unit))
        return ((Future<T>) state).get(0, TimeUnit.MILLISECONDS);
      else
        throw new TimeoutException();
    } catch (final InterruptedException ex) {
      throw new CheckedFutureException(ex);
    }
  }

  @Override
  public final <R> Future<R> map(final Function<T, R> f) {
    return continuation(new Continuation<T, R>(this) {
      @Override
      final Future<R> apply(final Future<T> result) {
        return result.map(f);
      }
    });
  }

  @Override
  public final <R> Future<R> flatMap(final Function<T, Future<R>> f) {
    return continuation(new Continuation<T, R>(this) {
      @Override
      final Future<R> apply(final Future<T> result) {
        return result.flatMap(f);
      }
    });
  }

  @Override
  public final Future<T> ensure(final Runnable f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.ensure(f);
      }
    });
  }

  @Override
  public final Future<T> onSuccess(final Consumer<T> c) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.onSuccess(c);
      }
    });
  }

  @Override
  public final Future<T> onFailure(final Consumer<Throwable> c) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.onFailure(c);
      }
    });
  }

  @Override
  public final Future<T> respond(final Responder<T> r) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.respond(r);
      }
    });
  }

  @Override
  public final Future<T> rescue(final Function<Throwable, Future<T>> f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.rescue(f);
      }
    });
  }

  @Override
  public final Future<T> handle(final Function<Throwable, T> f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.handle(f);
      }
    });
  }

  @Override
  public final Future<Void> voided() {
    return continuation(new Continuation<T, Void>(this) {
      @Override
      final Future<Void> apply(final Future<T> result) {
        return result.voided();
      }
    });
  }

  private final class DelayedPromise extends Promise<T> implements Runnable {
    public DelayedPromise() {
      super(Promise.this);
    }

    @Override
    public final void run() {
      become(Promise.this);
    }
  }

  @Override
  public final Future<T> delayed(final long delay, final TimeUnit timeUnit, final ScheduledExecutorService scheduler) {
    final DelayedPromise p = new DelayedPromise();
    scheduler.schedule(p, delay, timeUnit);
    return p;
  }

  @Override
  public final void proxyTo(final Promise<T> p) {
    if (p.isDefined())
      throw new IllegalStateException("Cannot call proxyTo on an already satisfied Promise.");

    final Responder<T> r = new Responder<T>() {
      @Override
      public void onException(final Throwable ex) {
        p.setException(ex);
      }

      @Override
      public void onValue(final T value) {
        p.setValue(value);
      }
    };
    respond(r);
  }

  private static class WithinPromise<T> extends Promise<T> implements Responder<T>, Callable<Boolean> {

    private final ScheduledFuture<Boolean> task;
    private final Throwable exception;

    public WithinPromise(final InterruptHandler handler, final long timeout, final TimeUnit timeUnit,
        final ScheduledExecutorService scheduler, final Throwable exception) {
      super(handler);
      this.task = scheduler.schedule(this, timeout, timeUnit);
      this.exception = exception;
    }

    @Override
    public void onException(final Throwable ex) {
      task.cancel(false);
      updateIfEmpty(Future.exception(ex));
    }

    @Override
    public void onValue(final T value) {
      task.cancel(false);
      updateIfEmpty(Future.value(value));
    }

    @Override
    public Boolean call() throws Exception {
      return updateIfEmpty(Future.exception(exception));
    }
  }

  @Override
  public final Future<T> within(final long timeout, final TimeUnit timeUnit, final ScheduledExecutorService scheduler,
      final Throwable exception) {
    if (timeout == Long.MAX_VALUE)
      return this;

    final WithinPromise<T> p = new WithinPromise<>(this, timeout, timeUnit, scheduler, exception);
    respond(p);
    return p;
  }

  @Override
  public String toString() {
    final Object curr = state;
    String stateString;
    if (curr instanceof SatisfiedFuture)
      stateString = state.toString();
    else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
      stateString = String.format("Linked(%s)", curr.toString());
    else
      stateString = "Waiting";
    return String.format("Promise(%s)@%s", stateString, Integer.toHexString(hashCode()));
  }
}

abstract class Continuation<T, R> extends Promise<R> implements WaitQueue<T> {

  public Continuation(final InterruptHandler handler) {
    super(handler);
  }

  abstract Future<R> apply(Future<T> result);

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c) {
    return new WaitQueue2<>(this, c);
  }

  @Override
  public final void forward(final Promise<T> target) {
    target.continuation(this);
  }

  @Override
  public final void flush(final Future<T> result) {
    super.update(apply(result));
  }
}