package io.futures;

@FunctionalInterface
public interface InterruptHandler {

  void raise(Exception ex);
}
