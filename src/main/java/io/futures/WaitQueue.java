package io.futures;

interface WaitQueue<T> {

  WaitQueue<T> add(Continuation<T, ?> c);

  void flush(Future<T> result);

  void forward(Promise<T> target);
}

final class WaitQueueHeadTail<T> implements WaitQueue<T> {

  private final Continuation<T, ?> head;
  private final WaitQueue<T> tail;

  WaitQueueHeadTail(final Continuation<T, ?> head, final WaitQueue<T> tail) {
    this.head = head;
    this.tail = tail;
  }

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c) {
    return new WaitQueueHeadTail<>(c, this);
  }

  @Override
  public final void flush(final Future<T> result) {
    WaitQueue<T> t = this;
    while (t instanceof WaitQueueHeadTail) {
      final WaitQueueHeadTail<T> l = (WaitQueueHeadTail<T>) t;
      l.head.flush(result);
      t = l.tail;
    }
    t.flush(result);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void forward(final Promise<T> target) {
    WaitQueue<T> t = this;
    while (t instanceof WaitQueueHeadTail) {
      final WaitQueueHeadTail<T> l = (WaitQueueHeadTail<T>) t;
      target.continuation(l.head);
      t = l.tail;
    }
    target.continuation((Continuation<T, ?>) t);
  }
}
