package io.trane.future;

public interface Responder<T> {

  public void onException(Throwable ex);

  public void onValue(T value);
}
