package io.trane.future;

public interface NonFatalException {

  public static Throwable verify(final Throwable ex) {

    // VirtualMachineError includes OutOfMemoryError and other fatal errors
    if (ex instanceof VirtualMachineError || ex instanceof ThreadDeath || ex instanceof LinkageError)
      throw (Error) ex;
    else if (ex instanceof InterruptedException)
      throw new RuntimeException(ex);
    else
      return ex;
  }
}
