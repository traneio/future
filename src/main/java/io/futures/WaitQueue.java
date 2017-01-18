package io.futures;

abstract class WaitQueue<T> {

  @SuppressWarnings("unchecked")
  public static <T, R> Object add(final Object queue, final Continuation<T, R> c) {
    if (queue == null)
      return c;
    else if (queue instanceof Continuation)
      return new WaitQueue2<>((Continuation<T, ?>) queue, c);
    else if (queue instanceof WaitQueue)
      return ((WaitQueue<T>) queue).add(c);
    else
      throw new IllegalStateException("Invalid wait queue " + queue);
  }

  @SuppressWarnings("unchecked")
  public static <T> void flush(final Object queue, final Future<T> result) {
    if (queue == null)
      return;
    else if (queue instanceof Continuation)
      ((Continuation<T, ?>) queue).flush(result);
    else if (queue instanceof WaitQueue)
      ((WaitQueue<T>) queue).flush(result);
    else
      throw new IllegalStateException("Invalid wait queue " + queue);
  }

  @SuppressWarnings("unchecked")
  public static <T> void forward(final Object queue, final Promise<T> target) {
    if (queue == null)
      return;
    else if (queue instanceof Continuation)
      target.continuation((Continuation<T, ?>) queue);
    else if (queue instanceof WaitQueue)
      ((WaitQueue<T>) queue).forward(target);
    else
      throw new IllegalStateException("Invalid wait queue " + queue);
  }

  protected abstract WaitQueue<T> add(Continuation<T, ?> c);

  protected abstract void flush(Future<T> result);

  protected abstract void forward(Promise<T> target);
}

final class WaitQueue2<T> extends WaitQueue<T> {

  final Continuation<T, ?> c1;
  final Continuation<T, ?> c2;

  protected WaitQueue2(final Continuation<T, ?> c1, final Continuation<T, ?> c2) {
    super();
    this.c1 = c1;
    this.c2 = c2;
  }

  @Override
  protected final WaitQueue<T> add(final Continuation<T, ?> c3) {
    return new WaitQueue3<>(c1, c2, c3);
  }

  @Override
  protected final void flush(final Future<T> result) {
    c1.flush(result);
    c2.flush(result);
  }

  @Override
  protected final void forward(final Promise<T> target) {
    target.continuation(c1);
    target.continuation(c2);
  }
}

final class WaitQueue3<T> extends WaitQueue<T> {

  final Continuation<T, ?> c1;
  final Continuation<T, ?> c2;
  final Continuation<T, ?> c3;

  protected WaitQueue3(final Continuation<T, ?> c1, final Continuation<T, ?> c2, final Continuation<T, ?> c3) {
    super();
    this.c1 = c1;
    this.c2 = c2;
    this.c3 = c3;
  }

  @Override
  protected final WaitQueue<T> add(final Continuation<T, ?> c4) {
    return new WaitQueue4<>(c1, c2, c3, c4);
  }

  @Override
  protected final void flush(final Future<T> result) {
    c1.flush(result);
    c2.flush(result);
    c3.flush(result);
  }

  @Override
  protected final void forward(final Promise<T> target) {
    target.continuation(c1);
    target.continuation(c2);
    target.continuation(c3);
  }
}

final class WaitQueue4<T> extends WaitQueue<T> {

  final Continuation<T, ?> c1;
  final Continuation<T, ?> c2;
  final Continuation<T, ?> c3;
  final Continuation<T, ?> c4;

  protected WaitQueue4(final Continuation<T, ?> c1, final Continuation<T, ?> c2, final Continuation<T, ?> c3,
      final Continuation<T, ?> c4) {
    super();
    this.c1 = c1;
    this.c2 = c2;
    this.c3 = c3;
    this.c4 = c4;
  }

  @Override
  protected final WaitQueue<T> add(final Continuation<T, ?> c5) {
    return new WaitQueueN<>(this, c5);
  }

  @Override
  protected final void flush(final Future<T> result) {
    c1.flush(result);
    c2.flush(result);
    c3.flush(result);
    c4.flush(result);
  }

  @Override
  protected final void forward(final Promise<T> target) {
    target.continuation(c1);
    target.continuation(c2);
    target.continuation(c3);
    target.continuation(c4);
  }
}

final class WaitQueueN<T> extends WaitQueue<T> {

  final WaitQueue<T> parent;
  final Object tail;

  protected WaitQueueN(final WaitQueue<T> parent, final Object tail) {
    super();
    this.parent = parent;
    this.tail = tail;
  }

  @Override
  protected final WaitQueue<T> add(final Continuation<T, ?> c) {
    final Object newTail = WaitQueue.add(tail, c);
    return new WaitQueueN<>(parent, newTail);
  }

  @Override
  protected final void flush(final Future<T> result) {
    parent.flush(result);
    WaitQueue.flush(tail, result);
  }

  @Override
  protected final void forward(final Promise<T> target) {
    parent.forward(target);
    WaitQueue.forward(tail, target);
  }
}
