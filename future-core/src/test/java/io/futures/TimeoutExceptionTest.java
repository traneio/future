package io.futures;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimeoutExceptionTest {

  @Test
  public void stackless() {
    assertEquals(TimeoutException.stackless, TimeoutException.stackless.fillInStackTrace());
  }
}
