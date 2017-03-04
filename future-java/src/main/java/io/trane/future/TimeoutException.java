package io.trane.future;

/**
 * Exception thrown when a blocking operation like future.get times out.
 */
public class TimeoutException extends RuntimeException {

  protected static final TimeoutException stackless = new TimeoutException() {
    private static final long serialVersionUID = -4485668079739346310L;

    @Override
    public final Throwable fillInStackTrace() {
      return this; // Suppress stack trace
    }
  };

  private static final long serialVersionUID = 3134247771039037170L;
}
