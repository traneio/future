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

  // Future<T> (Done) | Promise<T> (Linked) | WaitQueue|Null (Pending)
  private volatile Object state;

  private InterruptHandler interruptHandler;

  private final Optional<?>[] savedContext = Local.save();

  public Promise() {
    super();
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

  public final void become(final Future<T> result) {
    if (!becomeIfEmpty(result))
      throw new IllegalStateException("Can't set result " + result + " for promise with state " + state);
  }

  @SuppressWarnings("unchecked")
  public final boolean becomeIfEmpty(final Future<T> result) {
    try {
      while (true) {
        final Object curr = state;
        if (curr instanceof SatisfiedFuture)
          return false;
        else if (curr instanceof Promise && !(curr instanceof Continuation))
          return ((Promise<T>) curr).becomeIfEmpty(result);
        else if (result instanceof Promise) {
          ((Promise<T>) result).link(compress());
          return true;
        } else if (cas(curr, result)) {
          flush((WaitQueue<T>) curr, result);
          return true;
        }
      }
    } catch (final StackOverflowError ex) {
      System.err.println(
          "FATAL: Stack overflow when satisfying promise. Use `Future.tailrec` or increase the stack size (-Xss).");
      throw ex;
    }
  }

  private final void flush(final WaitQueue<T> queue, final Future<T> result) {
    if (queue == null)
      return;
    else {
      final Optional<?>[] oldContext = Local.save();
      Local.restore(savedContext);
      try {
        queue.flush(result);
      } finally {
        Local.restore(oldContext);
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected final void link(final Promise<T> target) {
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
          target.become((SatisfiedFuture<T>) curr);
        return;
      } else if (cas(curr, target)) { // Waiting
        if (curr != null)
          ((WaitQueue<T>) curr).forward(target);
        return;
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected final <R> Future<R> continuation(final Continuation<T, R> c) {
    while (true) {
      final Object curr = state;
      if (curr instanceof SatisfiedFuture) {
        c.flush((SatisfiedFuture<T>) curr);
        return c;
      } else if (curr instanceof Promise && !(curr instanceof Continuation))
        return ((Promise<T>) curr).continuation(c);
      else if (curr == null && cas(curr, c))
        return c;
      else if (curr != null && cas(curr, ((WaitQueue<T>) curr).add(c)))
        return c;
    }
  }

  @SuppressWarnings("unchecked")
  private final Promise<T> compress() {
    while (true) {
      final Object curr = state;
      if (curr instanceof Promise && !(curr instanceof Continuation)) { // Linked
        final Promise<T> target = ((Promise<T>) curr).compress();
        if (cas(curr, target))
          return target;
      } else
        return this;
    }
  }

  public final void setValue(final T value) {
    become(new ValueFuture<>(value));
  }

  public final void setException(final Throwable ex) {
    become(new ExceptionFuture<T>(ex));
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void raise(final Throwable ex) {
    final Object curr = state;
    if (curr instanceof SatisfiedFuture) // Done
      return;
    else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
      ((Promise<T>) curr).raise(ex);
    else if (interruptHandler != null)
      interruptHandler.raise(ex);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final boolean isDefined() {
    final Object curr = state;
    if (curr instanceof SatisfiedFuture) // Done
      return true;
    else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
      return ((Promise<T>) curr).isDefined();
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
      becomeIfEmpty(Future.exception(ex));
    }

    @Override
    public void onValue(final T value) {
      task.cancel(false);
      becomeIfEmpty(Future.value(value));
    }

    @Override
    public Boolean call() throws Exception {
      return becomeIfEmpty(Future.exception(exception));
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

  protected String toStringPrefix() {
    return "Promise";
  }

  @Override
  public String toString() {
    final Object curr = state;
    String stateString;
    if (curr instanceof SatisfiedFuture)
      stateString = curr.toString();
    else if (curr instanceof Promise && !(curr instanceof Continuation)) // Linked
      stateString = String.format("Linked(%s)", curr.toString());
    else
      stateString = "Waiting";
    return String.format("%s(%s)@%s", toStringPrefix(), stateString, Integer.toHexString(hashCode()));
  }
}

abstract class Continuation<T, R> extends Promise<R> implements WaitQueue<T> {

  public Continuation(final InterruptHandler handler) {
    super(handler);
  }

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
    become(apply(result));
  }

  abstract Future<R> apply(Future<T> result);

  @Override
  protected String toStringPrefix() {
    return "Continuation";
  }
}