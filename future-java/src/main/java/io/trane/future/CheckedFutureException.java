package io.trane.future;

/**
 * Wrapper exception is used to convert the checked exception into a runtime
 * exception so it can be thrown by blocking methods like `future.get`.
 */
public final class CheckedFutureException extends Exception {
  private static final long serialVersionUID = -5901192406028389609L;

  protected CheckedFutureException(final Throwable cause) {
    super(cause);
  }
}
