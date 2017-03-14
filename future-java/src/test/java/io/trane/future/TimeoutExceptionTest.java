package io.trane.future;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeoutExceptionTest {

  @Test
  public void stackless() {
    assertEquals(TimeoutException.stackless, TimeoutException.stackless.fillInStackTrace());
  }
}
