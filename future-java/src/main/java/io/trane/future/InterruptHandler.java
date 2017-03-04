package io.trane.future;

import java.util.Collection;

/**
 * Interrupts provide a way to send signals to the current 
 * pending `Promise` given a `Future` composition. It is a 
 * mechanism that enables cancellations. For instance, 
 * given this composition that involves an async boundary 
 * (`userService.get`) and a continuation (`.map`):
 * 
 * ```java
 * Future<String> username = 
 *   userService.get(userId).map(user -> user.username);
 * ```
 * 
 * It is possible to raise an interrupt that is received 
 * by the `userService.get` `Promise`:
 * 
 * ```java
 * username.raise(new TimeoutException);
 * ```
 * 
 * The `Promise` created by `userService.get` can define a 
 * custom handler that performs an action in case an interrupt 
 * is received. 
 * 
 * `Promise.apply` has overloaded methods that allow the user 
 * to set the interrupt handler. This mechanism can be used 
 * to cancel requests to remote systems, as Finagle does.
 * 
 * The method `interruptible` is a shortcut to fail the 
 * `Promise` if it receives any interrupt signal:
 * 
 * ```java
 * Future<String> username = 
 *   userService.get(userId).interruptible().map(user -> user.username);
 *   
 * username.raise(new TimeoutException);
 * ```
 * 
 * In this case, even if `userService.get` does not handle 
 * interrupts, the `Promise` is satisfied with the interrupt 
 * exception.
 * 
 * The interrupt propagation happens through pointers from 
 * each continuation to its parent that are created automatically 
 * by the library. In the previous example, the `map` continuation 
 * has a pointer to the `Promise` that is pending.
 */
@FunctionalInterface
public interface InterruptHandler {

  /**
   * Creates an interrupt handle that calls multiple handlers.
   * 
   * @param handlers  list of handlers to be called.
   * @return          the aggregate interrupt handler.
   */
  public static InterruptHandler apply(final Collection<? extends InterruptHandler> handlers) {
    return ex -> {
      for (final InterruptHandler handler : handlers)
        handler.raise(ex);
    };
  }

  /**
   * Creates an interrupt handler that calls to other handlers.
   * 
   * @param h1  first handler to be called.
   * @param h2  second handler to be called.
   * @return    the aggregate interrupt handler.
   */
  public static InterruptHandler apply(final InterruptHandler h1, final InterruptHandler h2) {
    return ex -> {
      h1.raise(ex);
      h2.raise(ex);
    };
  }

  /**
   * Raises an interrupt.
   * 
   * @param ex  the interrupt exception.
   */
  void raise(Throwable ex);
}
