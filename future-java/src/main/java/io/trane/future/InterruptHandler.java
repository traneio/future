package io.trane.future;

import java.util.List;

@FunctionalInterface
public interface InterruptHandler {

  public static InterruptHandler apply(final InterruptHandler h1, final InterruptHandler h2) {
    return ex -> {
      h1.raise(ex);
      h2.raise(ex);
    };
  }

  public static InterruptHandler apply(final List<? extends InterruptHandler> handlers) {
    return ex -> {
      for (final InterruptHandler handler : handlers)
        handler.raise(ex);
    };
  }

  void raise(Throwable ex);
}
