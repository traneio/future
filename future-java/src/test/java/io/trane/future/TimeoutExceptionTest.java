package io.trane.future;

import org.junit.Test;

import io.trane.future.TimeoutException;

import static org.junit.Assert.*;

public class TimeoutExceptionTest {

  @Test
  public void stackless() {
    assertEquals(TimeoutException.stackless, TimeoutException.stackless.fillInStackTrace());
  }
}
