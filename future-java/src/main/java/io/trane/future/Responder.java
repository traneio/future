package io.trane.future;

/**
 * Callback to be used with future.respond.
 * 
 * @param <T>  the type of the future.
 */
public interface Responder<T> {

  /**
   * Method called when the future completes with an exception.
   * 
   * @param ex  the failure
   */
  public void onException(Throwable ex);

  /**
   * Method called when the future completes with a value.
   * 
   * @param value  the value.
   */
  public void onValue(T value);
}
