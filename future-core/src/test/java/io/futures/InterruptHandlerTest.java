package io.futures;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InterruptHandlerTest {

  private final Exception ex = new TestException();
  
  @Test
  public void applyTwoHandlers() {
    AtomicReference<Throwable> i1 = new AtomicReference<>();
    AtomicReference<Throwable> i2 = new AtomicReference<>();
    InterruptHandler h1 = i1::set;
    InterruptHandler h2 = i2::set;
    InterruptHandler h = InterruptHandler.apply(h1, h2);
    
    h.raise(ex);
    
    assertEquals(ex, i1.get());
    assertEquals(ex, i2.get());
  }
  
  @Test
  public void applyHandlersList() {
    AtomicReference<Throwable> i1 = new AtomicReference<>();
    AtomicReference<Throwable> i2 = new AtomicReference<>();
    AtomicReference<Throwable> i3 = new AtomicReference<>();
    InterruptHandler h1 = i1::set;
    InterruptHandler h2 = i2::set;
    InterruptHandler h3 = i3::set;
    InterruptHandler h = InterruptHandler.apply(Arrays.asList(h1, h2, h3));
    
    h.raise(ex);
    
    assertEquals(ex, i1.get());
    assertEquals(ex, i2.get());
    assertEquals(ex, i3.get());
  }
}
