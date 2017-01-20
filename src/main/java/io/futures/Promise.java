package io.futures;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class Promise<T> extends Future<T> {

  private static final long stateOffset = Unsafe.objectFieldOffset(Promise.class, "state");

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

  private final InterruptHandler interruptHandler;

  // Future<T> (Done) | WaitQueue (Pending) | Promise<T> (Linked)
  private final Object state = null;

  private final Optional<?>[] savedContext = Local.save();

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
    if (curr instanceof Promise && !(curr instanceof Continuation)) { // Linked
      final Promise<T> target = ((Promise<T>) curr).compress();
      cas(curr, target);
      return target;
    } else
      return this;
  }

  @SuppressWarnings("unchecked")
  private final void link(final Promise<T> target) {
    final Object curr = state;
    while (true)
      if (curr instanceof Promise && !(curr instanceof Continuation)) { // Linked
        if (cas(curr, target)) {
          ((Promise<T>) curr).link(target);
          return;
        }
      } else if (curr instanceof SatisfiedFuture) { // Done
        if (target.state != null && !target.state.equals(curr))
          throw new IllegalStateException("Cannot link two Done Promises with differing values");
        return;
      } else if (cas(curr, target)) { // Waiting
        WaitQueue.forward(curr, target);
        return;
      }
  }

  @SuppressWarnings("unchecked")
  @Override
  final boolean isDefined() {
    if (state instanceof SatisfiedFuture) // Done
      return true;
    else if (state instanceof Promise && !(state instanceof Continuation)) // Linked
      return ((Promise<T>) state).isDefined();
    else // Waiting
      return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected final T get(final long timeout, final TimeUnit unit) throws CheckedFutureException {
    final CountDownLatch latch = new CountDownLatch(1);
    ensure(() -> latch.countDown());
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
  final <R> Future<R> map(final Function<T, R> f) {
    return continuation(new Continuation<T, R>(this) {
      @Override
      final Future<R> apply(final Future<T> result) {
        return result.map(f);
      }
    });
  }

  @Override
  final <R> Future<R> flatMap(final Function<T, Future<R>> f) {
    return continuation(new Continuation<T, R>(this) {
      @Override
      final Future<R> apply(final Future<T> result) {
        return result.flatMap(f);
      }
    });
  }

  @Override
  final Future<T> ensure(final Runnable f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.ensure(f);
      }
    });
  }

  @Override
  final Future<T> onSuccess(final Consumer<T> c) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.onSuccess(c);
      }
    });
  }

  @Override
  final Future<T> onFailure(final Consumer<Throwable> c) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.onFailure(c);
      }
    });
  }

  @Override
  final Future<T> rescue(final Function<Throwable, Future<T>> f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.rescue(f);
      }
    });
  }

  @Override
  final Future<T> handle(final Function<Throwable, T> f) {
    return continuation(new Continuation<T, T>(this) {
      @Override
      final Future<T> apply(final Future<T> result) {
        return result.handle(f);
      }
    });
  }
}

abstract class Continuation<T, R> extends Promise<R> {

  public Continuation(final InterruptHandler handler) {
    super(handler);
  }

  abstract Future<R> apply(Future<T> result);

  protected final void flush(final Future<T> result) {
    super.update(apply(result));
  }
}