package io.trane.future;

/**
 * Interface to be used by future.transform.
 * 
 * @param <T>  the type of the future value.
 * @param <U>  the result type of the transformation.
 */
public interface Transformer<T, U> {

  /**
   * Transformation called when the future completes with a failure.
   * 
   * @param ex  the failure.
   * @return    the transformed value.
   */
  public U onException(Throwable ex);

  /**
   * Transformation called when the future completes with a value.
   * 
   * @param value  the value.
   * @return       the transformed value.
   */
  public U onValue(T value);
}
