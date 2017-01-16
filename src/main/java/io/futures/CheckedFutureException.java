package io.futures;

public class CheckedFutureException extends Exception {
  private static final long serialVersionUID = -5901192406028389609L;

  public CheckedFutureException(Throwable cause) {
    super(cause);
  }
}
