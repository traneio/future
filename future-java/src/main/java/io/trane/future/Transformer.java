package io.trane.future;

public interface Transformer<T, U> {

  public U onException(Throwable ex);

  public U onValue(T value);
}
