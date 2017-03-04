package io.trane.future;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * `Local`s are a mechanism similar to `ThreadLocal` but for
 * asynchronous computations.
 * 
 * It is not possible to use `ThreadLocal`s with `Future` because 
 * the data it holds become invalid when the computation reaches 
 * an asynchronous boundary. The thread returns to its thread pool 
 * to execute other computations, and the continuations are performed 
 * by the thread that sets the result of the `Promise`.
 * 
 * `Local`s are a mechanism similar to `ThreadLocal`, but it has a 
 * more flexible scope. For example, this code sets the `UserSession` 
 * local when a request is processed:
 * 
 * ```java
 * public class UserSession {
 *   public static final Local<UserSession> local= Local.apply();
 *   // UserSession impl
 * }
 * 
 * public class MyService {
 *   public Future<List<Tweet>> getTweetsEndpoint(Request request) {
 *     UserSession.local.let(
 *       request.getSession(), 
 *       () -> tweetRepo.get(request.getUserId()));
 *   }
 * }
 * ```
 * 
 * Note that the `let` method is used to define the local value, execute 
 * the function defined by the second parameter, and then set the local 
 * to its previous value. It is a convenient method to avoid having to 
 * set and clear the value manually:
 * 
 * ```java
 * public class MyService {
 *   public Future<List<Tweet>> getTweetsEndpoint(Request request) {
 *     final Optional<UserSessuib> saved = UserSession.local.get();
 *     UserSession.local.set(Optional.of(request.getSession()));
 *     try {
 *       return tweetRepo.get(request.getUserId());
 *     } finally {
 *       UserSession.local.set(saved);
 *     }
 *   }
 * }
 * ```
 * 
 * At any point of the of the request processing, even after asynchronous 
 * boundaries, the user session can be accessed. For instance, let's say 
 * that `tweetRepo` uses a `TweetStorage` that routes the query to a specific 
 * database shard based on the user that is requesting the tweet:
 * 
 * ```java
 * public class TweetStorage {
 *   public Future<RawTweet> getTweet(long tweetId) {
 *     databaseFor(UserSession.local.get().getUserId()).getTweet(tweetId);
 *   }
 * }
 * ```
 * 
 * This feature is implemented with a `ThreadLocal` that is saved at the 
 * point of an asynchronous boundary as a `Promise` field and is restored 
 * when the `Promise` is satisfied, flushing its continuations with the 
 * original `ThreadLocal` contents.
 * 
 * Note: This feature does not have the same behavior as Twitter's `Local`. 
 * The `ThreadLocal` state is captured when a `Promise` is created, whereas 
 * Twitter's implementation captures the state only when a `Promise` 
 * continuation is created (for instance, `map` is called on a `Promise` 
 * instance). In practice, most `Promise` creations are followed by a 
 * continuation, so the behavior is usually the same.
 * 
 * @param <T>
 *          the type of the local value.
 */
public final class Local<T> {

  protected static final Optional<?>[] EMPTY = new Optional<?>[0];
  private static ThreadLocal<Optional<?>[]> threadLocal = null;
  private static int size = 0;

  /**
   * Creates a new local value.
   * 
   * @param <T>  the type of the local value.
   * @return     the local value
   */
  public static final <T> Local<T> apply() {
    return new Local<>();
  }

  protected static final Optional<?>[] save() {
    if (threadLocal == null)
      return EMPTY;
    else {
      Optional<?>[] state = threadLocal.get();
      if (state == null)
        return EMPTY;
      else
        return state;
    }
  }

  protected static final void restore(final Optional<?>[] saved) {
    if (threadLocal != null)
      threadLocal.set(saved);
  }

  private static final synchronized int newPosition() {
    if (threadLocal == null)
      threadLocal = new ThreadLocal<>();
    return size++;
  }

  private final int position = newPosition();

  private Local() {
  }

  /**
   * Updates the local with the provided value.
   * 
   * @param value  value to set
   */
  public final void update(final T value) {
    set(Optional.of(value));
  }

  /**
   * Sets the value of the local. It's similar to update but receives an
   * optional value.
   * 
   * @param opt  optional value to set.
   */
  public final void set(final Optional<T> opt) {
    Optional<?>[] ctx = threadLocal.get();

    if (ctx == null)
      ctx = new Optional<?>[size];
    else {
      final Optional<?>[] oldCtx = ctx;
      ctx = new Optional<?>[size];
      System.arraycopy(oldCtx, 0, ctx, 0, oldCtx.length);
    }

    ctx[position] = opt;
    threadLocal.set(ctx);
  }

  /**
   * Gets the current value of the local.
   * 
   * @return  the local value
   */
  @SuppressWarnings("unchecked")
  public final Optional<T> get() {
    final Optional<?>[] ctx = threadLocal.get();
    if (ctx == null || ctx.length <= position)
      return Optional.empty();

    final Optional<?> v = ctx[position];
    if (v == null)
      return Optional.empty();
    else
      return (Optional<T>) v;
  }

  /**
   * Executes the supplier with the provided value and then rolls back to the
   * previous local value.
   * 
   * @param value  value to be set.
   * @param s      supplier to be executed with the provided value.
   * @return       the result of the supplier.
   */
  public final <U> U let(final T value, final Supplier<U> s) {
    final Optional<T> saved = get();
    set(Optional.of(value));
    try {
      return s.get();
    } finally {
      set(saved);
    }
  }
}
