package io.futures;

import java.util.List;

@FunctionalInterface
public interface InterruptHandler {

  public static InterruptHandler fromList(final List<? extends InterruptHandler> handlers) {
    return (ex) -> {
      for (final InterruptHandler handler : handlers)
        handler.raise(ex);
    };
  }

  void raise(Throwable ex);
}
