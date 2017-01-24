package io.futures;

interface WaitQueue<T> {

  public static <T, R> WaitQueue<T> create(final Continuation<T, R> c1) {
    return new WaitQueue1<>(c1);
  }

  WaitQueue<T> add(Continuation<T, ?> c);

  void flush(Future<T> result);

  void forward(Promise<T> target);
}

final class WaitQueue1<T> implements WaitQueue<T> {

  final Continuation<T, ?> c1;

  WaitQueue1(final Continuation<T, ?> c1) {
    super();
    this.c1 = c1;
  }

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c2) {
    return new WaitQueue2<>(c1, c2);
  }

  @Override
  public final void flush(final Future<T> result) {
    c1.flush(result);
  }

  @Override
  public final void forward(final Promise<T> target) {
    target.continuation(c1);
  }
}

final class WaitQueue2<T> implements WaitQueue<T> {

  final Continuation<T, ?> c1;
  final Continuation<T, ?> c2;

  WaitQueue2(final Continuation<T, ?> c1, final Continuation<T, ?> c2) {
    super();
    this.c1 = c1;
    this.c2 = c2;
  }

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c3) {
    return new WaitQueue3<>(c1, c2, c3);
  }

  @Override
  public final void flush(final Future<T> result) {
    c1.flush(result);
    c2.flush(result);
  }

  @Override
  public final void forward(final Promise<T> target) {
    target.continuation(c1);
    target.continuation(c2);
  }
}

final class WaitQueue3<T> implements WaitQueue<T> {

  final Continuation<T, ?> c1;
  final Continuation<T, ?> c2;
  final Continuation<T, ?> c3;

  WaitQueue3(final Continuation<T, ?> c1, final Continuation<T, ?> c2, final Continuation<T, ?> c3) {
    super();
    this.c1 = c1;
    this.c2 = c2;
    this.c3 = c3;
  }

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c4) {
    return new WaitQueue4<>(c1, c2, c3, c4);
  }

  @Override
  public final void flush(final Future<T> result) {
    c1.flush(result);
    c2.flush(result);
    c3.flush(result);
  }

  @Override
  public final void forward(final Promise<T> target) {
    target.continuation(c1);
    target.continuation(c2);
    target.continuation(c3);
  }
}

final class WaitQueue4<T> implements WaitQueue<T> {

  final Continuation<T, ?> c1;
  final Continuation<T, ?> c2;
  final Continuation<T, ?> c3;
  final Continuation<T, ?> c4;

  WaitQueue4(final Continuation<T, ?> c1, final Continuation<T, ?> c2, final Continuation<T, ?> c3,
      final Continuation<T, ?> c4) {
    super();
    this.c1 = c1;
    this.c2 = c2;
    this.c3 = c3;
    this.c4 = c4;
  }

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c5) {
    return new WaitQueueN<>(this, WaitQueue.create(c5));
  }

  @Override
  public final void flush(final Future<T> result) {
    c1.flush(result);
    c2.flush(result);
    c3.flush(result);
    c4.flush(result);
  }

  @Override
  public final void forward(final Promise<T> target) {
    target.continuation(c1);
    target.continuation(c2);
    target.continuation(c3);
    target.continuation(c4);
  }
}

final class WaitQueueN<T> implements WaitQueue<T> {

  final WaitQueue<T> parent;
  final WaitQueue<T> tail;

  WaitQueueN(final WaitQueue<T> parent, final WaitQueue<T> tail) {
    super();
    this.parent = parent;
    this.tail = tail;
  }

  @Override
  public final WaitQueue<T> add(final Continuation<T, ?> c) {
    return new WaitQueueN<>(parent, tail.add(c));
  }

  @Override
  public final void flush(final Future<T> result) {
    // TODO avoid stack
    parent.flush(result);
    tail.flush(result);
  }

  @Override
  public final void forward(final Promise<T> target) {
    // TODO avoid stack
    parent.forward(target);
    tail.forward(target);
  }
}
