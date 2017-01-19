package io.futures;

import static org.junit.Assert.*;

import org.junit.Test;

public class NonFatalExceptionTest {

  @Test(expected = VirtualMachineError.class)
  public void virtualMachineError() {
    NonFatalException.verify(new VirtualMachineError() {
      private static final long serialVersionUID = 1L;
    });
  }
  
  @Test(expected = RuntimeException.class)
  public void interruptedException() {
    NonFatalException.verify(new InterruptedException());
  }
  
  @Test
  public void nonFatalException() {
    Throwable ex = new NullPointerException();
    Throwable result = NonFatalException.verify(ex);
    assertEquals(ex, result);
  }
}
