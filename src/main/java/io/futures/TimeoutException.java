package io.futures;

public class TimeoutException extends RuntimeException {

  public static final TimeoutException stackless = new TimeoutException() {
    private static final long serialVersionUID = -4485668079739346310L;

    @Override
    public Throwable fillInStackTrace() {
      return this; // Suppress stack trace
    }
  };

  private static final long serialVersionUID = 3134247771039037170L;
}
