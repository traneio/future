package io.futures;

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
  private Object state;

  public Promise() {
  }

  protected InterruptHandler getInterruptHandler() {
    return null;
  }

  protected Optional<?>[] getSavedContext() {
    return null;
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
          ((Promise<T>) result).compress().link(this);
          return true;
        } else if (cas(curr, result)) {
          if (curr != null)
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
    Optional<?>[] savedContext = getSavedContext();
    if (savedContext != null) {
      final Optional<?>[] oldContext = Local.save();
      Local.restore(savedContext);
      try {
        queue.flush(result);
      } finally {
        Local.restore(oldContext);
      }
    } else
      queue.flush(result);
  }

  @SuppressWarnings("unchecked")
  private final void link(final Promise<T> target) {
    while (true) {
      final Object curr = state;
      if (curr instanceof SatisfiedFuture) {
        target.become((SatisfiedFuture<T>) curr);
        return;
      } else if (cas(curr, target)) {
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
      } else if (curr instanceof Promise && !(curr instanceof Continuation)) {
        return ((Promise<T>) curr).continuation(c);
      } else if (curr == null) {
        if (cas(curr, c))
          return c;
      } else if (curr != null) {
        if (cas(curr, ((WaitQueue<T>) curr).add(c)))
          return c;
      }
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
    InterruptHandler interruptHandler = getInterruptHandler();
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
    join(timeout, unit);
    return ((Future<T>) state).get(0, TimeUnit.MILLISECONDS);
  }

  @Override
  public void join(long timeout, TimeUnit unit) throws CheckedFutureException {
    final ReleaseOnRunLatch latch = new ReleaseOnRunLatch();
    ensure(latch);
    try {
      if (!latch.await(timeout, unit))
        throw new TimeoutException();
    } catch (final InterruptedException ex) {
      throw new CheckedFutureException(ex);
    }
  }

  @Override
  public final <R> Future<R> map(final Function<? super T, ? extends R> f) {
    return continuation(new Continuation<T, R>(this) {
      @Override
      final Future<R> apply(final Future<T> result) {
        return result.map(f);
      }
    });
  }

  @Override
  public final <R> Future<R> flatMap(final Function<? super T, ? extends Future<R>> f) {
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
  public final Future<T> onSuccess(final Consumer<? super T> c) {
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
  public final Future<T> respond(final Responder<? super T> r) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.respond(r);
      }
    });
  }

  @Override
  public final Future<T> rescue(final Function<Throwable, ? extends Future<T>> f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.rescue(f);
      }
    });
  }

  @Override
  public final Future<T> handle(final Function<Throwable, ? extends T> f) {
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
    @Override
    protected InterruptHandler getInterruptHandler() {
      return Promise.this;
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

    private final InterruptHandler handler;
    private final ScheduledFuture<Boolean> task;
    private final Throwable exception;

    public WithinPromise(final InterruptHandler handler, final long timeout, final TimeUnit timeUnit,
        final ScheduledExecutorService scheduler, final Throwable exception) {
      this.handler = handler;
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

    @Override
    protected InterruptHandler getInterruptHandler() {
      return handler;
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

  private final InterruptHandler handler;

  public Continuation(final InterruptHandler handler) {
    this.handler = handler;
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

  @Override
  protected InterruptHandler getInterruptHandler() {
    return handler;
  }

  abstract Future<R> apply(Future<T> result);

  @Override
  protected String toStringPrefix() {
    return "Continuation";
  }
}