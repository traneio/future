package io.futures;

public final class NonFatalException {

  public static final Throwable verify(Throwable ex) {

    // VirtualMachineError includes OutOfMemoryError and other fatal errors
    if (ex instanceof VirtualMachineError || ex instanceof ThreadDeath || ex instanceof LinkageError)
      throw (Error) ex;
    else if (ex instanceof InterruptedException)
      throw new RuntimeException(ex);
    else
      return ex;
  }
}
