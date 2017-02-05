package io.futures;

public final class CheckedFutureException extends Exception {
  private static final long serialVersionUID = -5901192406028389609L;

  public CheckedFutureException(final Throwable cause) {
    super(cause);
  }
}
