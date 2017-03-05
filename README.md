[![Build Status](https://travis-ci.org/traneio/future.svg?branch=master)](https://travis-ci.org/traneio/future)
[![Code Coverage](https://sonarqube.com/api/badges/measure?key=io.trane:future&metric=coverage)](https://sonarqube.com/dashboard?id=io.trane%3Afuture)
[![Tech Debt](https://sonarqube.com/api/badges/measure?key=io.trane:future&metric=sqale_debt_ratio)](https://sonarqube.com/dashboard?id=io.trane%3Afuture)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.trane/future-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.trane/future)
[![Javadoc](https://img.shields.io/badge/api-javadoc-green.svg)](http://trane.io/apidocs/future-java/current/)
[![Join the chat at https://gitter.im/traneio/future](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/traneio/future?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project is a high-performance implementation of the `Future` abstraction. The library was designed from scratch with a focus on reducing CPU usage and memory footprint, and having the [Twitter `Future`](https://github.com/twitter/util) as its main inspiration.

It allows the user to express complex asynchronous code in a composable and type-safe manner. It also supports more advanced features that are currently only available for Twitter futures and are essential to developing non-trivial systems. Namely, it provides `Local`s, that are similar to `ThreadLocal`s but for asynchronous code, and `interrupt`s, also known as cancellations.
 
The current version has only one implementation in Java, but the intent is to create modules for other JVM languages to make the API idiomatic in multiple languages.

Getting started
===============

The library binaries are distributed through maven central. Click on the maven central badge for information on how to add the project dependency to your project:

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.trane/future-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.trane/future)

This project does not have a mailing list; please use our gitter channel:

[![Join the chat at https://gitter.im/traneio/future](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/traneio/future?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Please refer to the Javadoc for detailed information about the library and its features:

[![Javadoc](https://img.shields.io/badge/api-javadoc-green.svg)](http://trane.io/apidocs/future-java/current/)

The `Future` abstraction
=======================

`Future` is an abstraction to deal with asynchronicity without having to use callbacks directly or blocking threads. The primary usage for `Futures` on the JVM is to perform IO operations, which are asynchronous by nature. 

Although most IO APIs return synchronously, they do that by blocking the current `Thread`. For instance, the thread issues a request to a remote system and then waits until a response comes back. Considering that the JVM uses native threads, it is wasteful to block them since it leads to potential thread starvation and higher garbage collection pressure. It is hard to scale a JVM system vertically if the IO throughput is bounded by the number of threads.

From the user perspective, a `Future` can be in three states:

1. Uncompleted
2. Completed with a value
3. Completed with an exception

Instead of exposing this state to the user, `Future` provides combinators to express computations that run once the `Future` completes. The results of these combinators are `Future` instances that can be used to perform other transformations, giving the user a powerful tool to express complex chains of asynchronous transformations.

Let's say that we need to call a remote service to get the username given an id:

```java
Future<User> user = userService.get(userId);
```

It's possible to apply the `map` transformation that produces a `Future` for the username string:

```java
Future<String> username = user.map(user -> user.username);
```

Note that we are using a lambda expression (`user -> user.username`) that takes a user and returns its username.

Let's say that now we need to call a service to validate the username string. This is the result if we use the `map` combinator for it:

```java
Future<Future<Boolean>> isValid = username.map(username -> usernameService.isValid(username));
```

Given that the lambda expression calls another service and returns a `Future`, the produced result is a nested future (`Future<Future<Boolean>>`). One alternative to flatten this nested result is using `Future.flatten`:

```java
Future<Boolean> isValidFlat = Future.flatten(isValid);
```

There's a convenient combinator called `flatMap` that applies both `map` and `Future.flatten` at once:

```java
Future<Boolean> isValid = username.flatMap(username -> usernameService.isValid(username));
```

The `flatMap` combinator is very flexible and comes from the monad abstraction. Although useful, learning monads and category theory is not a requirement to use `Future`s. Strictly speaking,
`Future` isn't a monad since it uses eager evaluation and thus breaks referential transparency. Once a `Future` is created, it is already running. See the "Execution Model" section for more information.

There are many other useful operators to deal with exceptions, collections of futures, and others. For a complete reference, please see the [javadocs](http://trane.io/apidocs/future-java/current/).

Execution model
===============

`Future`s are eager by nature. Once a future is created, the asynchronous computation is triggered. For instance, even though these two futures are composed sequentially through the `flatMap` combinator, they are already running in parallel:

```java
Future<User> user = userService.get(userId);
Future<List<Tweet>> tweets = timelineService.getUserTweets(userId);

Future<Profile> profile = 
  user.flatMap(u ->
    tweets.map(t ->
      new Profile(u, t);
    )
  )
```

Both calls are issued to the remote service when the futures are created, and the `flatMap` combinator only uses the `Future` instances that are already running. If the call to `tweetService` is inlined within the `flatMap` lambda body, the `tweetService` is called only after  the `userService` returns:

```java
Future<User> user = userService.get(userId);

Future<Profile> profile = 
  user.flatMap(u ->
    timelineService.getUserTweets(userId).map(t ->
      new Profile(u, t);
    )
  )
```

This implementation of `Future` leverages this behavior to avoid context thread switches. The execution of the asynchronous computation reuses the current thread until it reaches an asynchronous boundary, where it cannot continue executing since it needs to wait for the completion of an asynchronous operation like a remote system call. For instance, this computation runs entirely on the current thread synchronously:

```java
Future.value(1).map(i -> i + 1);
```

If there's an asynchronous boundary, the future composition is executed until it reaches the boundary:

```java
Future.value(1)
  .map(i -> i + 1) // Runs on the current thread
  .flatMap(i -> callAService(i)); // Issues the request on the current thread
```

This composition is executed by the current thread and stops at the point where the remote call is issued to the network layer. Once the remote service returns the response, the network layer thread continues the execution of the remaining steps:

```java
Future.value(1)
  .map(i -> i + 1) // Runs on the current thread
  .flatMap(i -> callAService(i)) // Issues the request on the current thread
  .map(i -> i == 10); // Runs on the network thread that satisfies the future continuation
```

Asynchronous boundaries are defined using `Promise`s:

```java
public Future<Integer> callAService(Integer i) {
  Promise<Integer> p = Promise.apply();
  networkLayer.issueRequest(i).onComplete(i -> p.setValue(i));
  return p;
}
```

Note that `Promise` is a `Future` but provides methods to set its result. They are useful to interact with callback-based APIs like the ones that are typically provided by network libraries. The promise is created and returned synchronously to the caller, but it is pending until the `onComplete` callback is executed by the network layer.

Using `Promise`s, it is possible to create fully asynchronous code throughout the application stack and never block threads. It is a common misconception that blocking must happen at some layer of the application. For instance, it is possible to satisfy a request to a server and avoid blocking to write the result back to the client using a lambda that captures a reference to the network connection/session. Example:

```java
public void processRequest(Request request, Connection conn) {
  callEndpointMethod(request)
    .onSuccess(result-> conn.writeSuccess(result))
    .onFailure(ex -> conn.writeFailure(ex));
}
```

Recursive `Future`s
==================

Given the optimization that this library implements to avoid thread context switch, compositions are not stack-safe by default. It is necessary to wrap recursive computations with a `Tailrec` call:

```java
public Future<Integer> factorial(Integer i) {
  Tailrec.apply(() ->
    if (i ==0) return Future.value(1);
    else factorial(i - 1).map(j -> i * j);
  )
}
```

This is just an example, there's no reason to use `Future`s to implement a factorial function. Requiring the `Tailrec` call for recursive computations is a reasonable compromise since recursive futures are uncommon.

Even though the computation is wrapped by `Tailrec`, the execution still leverages the synchronous execution optimizations in batches. It executes the composition synchronously until it reaches the batch size and then uses a `Promise` to unwind the stack and then run the next batch.

The default batch size is defined by the system property "io.trane.future.defaultBatchSize", which is `512` by default. Alternatively, it is possible to set the batch size when calling `Tailrec.apply`:

```java
public Future<Integer> factorial(Integer i) {
  Tailrec.apply(1024, () ->
    if (i ==0) return Future.value(1);
    else factorial(i - 1).map(j -> i * j);
  )
}
```

Note that the first parameter defines the batch size as `1024`. Typically, the users do not need to tune this parameter unless a `StackOverflowException` is thrown or the user wants to increase the batch size for performance reasons. Larger batches tend to improve performance but increase the risk of a `StackOverflowException`.

Isolating thread pools
======================

It is possible to isolate portions of a `Future` composition on a separate thread pool:

```java
FuturePool futurePool = FuturePool.apply(Executors.newCachedThreadPool());

Future<List<Token>> user = 
  documentService.get(docId)
    .flatMap(doc -> futurePool.async(tokenize(doc)))
```

This feature useful to isolate cpu-intensive tasks and blocking operations. Please refer to the Java documentation to decide which type of executor is the best for the kind of task that needs to be performed. For instance, a `ForkJoinPool` is useful for cpu-intensive tasks, but can't be used for blocking operations.

The `FuturePool` also has the method `isolate` that isolates the execution of a `Future`:

```java
FuturePool futurePool = FuturePool.apply(Executors.newCachedThreadPool());

Future<User> user = futurePool.isolate(userRepo.get(userId));
```

`isolate` is just a shortcut for `async` + `Future.flatten`.

`Local`s
========

It is not possible to use `ThreadLocal`s with `Future` because the data it holds become invalid when the computation reaches an asynchronous boundary. The thread returns to its thread pool to execute other computations, and the continuations are performed by the thread that sets the result of the `Promise`.

`Local`s are a mechanism similar to `ThreadLocal`, but it has a more flexible scope. For example, this code sets the `UserSession` local when a request is processed:

```java
public class UserSession {
  public static final Local<UserSession> local= Local.apply();
  // UserSession impl
}

public class MyService {
  public Future<List<Tweet>> getTweetsEndpoint(Request request) {
    UserSession.local.let(request.getSession(), () -> tweetRepo.get(request.getUserId()));
  }
}
```

Note that the `let` method is used to define the local value, execute the function defined by the second parameter, and then set the local to its previous value. It is a convenient method to avoid having to set and clear the value manually:

```java
public class MyService {
  public Future<List<Tweet>> getTweetsEndpoint(Request request) {
    final Optional<UserSessuib> saved = UserSession.local.get();
    UserSession.local.set(Optional.of(request.getSession()));
    try {
      return tweetRepo.get(request.getUserId());
    } finally {
      UserSession.local.set(saved);
    }
  }
}
```

At any point of the of the request processing, even after asynchronous boundaries, the user session can be accessed. For instance, let's say that `tweetRepo` uses a `TweetStorage` that routes the query to a specific database shard based on the user that is requesting the tweet:

```java
public class TweetStorage {
  public Future<RawTweet> getTweet(long tweetId) {
    databaseFor(UserSession.local.get().getUserId()).getTweet(tweetId);
  }
}
```

This feature is implemented with a `ThreadLocal` that is saved at the point of an asynchronous boundary as a `Promise` field and is restored when the `Promise` is satisfied, flushing its continuations with the original `ThreadLocal` contents.

Note: This feature does not have the same behavior as Twitter's `Local`. The `ThreadLocal` state is captured when a `Promise` is created, whereas the Twitter's implementation captures the state only when a `Promise` continuation is created (for instance, `map` is called on a `Promise` instance). In practice, most `Promise` creations are followed by a continuation, so the behavior is usually the same.

Interrupts/cancellations
======================

This feature provides a way to send signals to the current pending `Promise` given a `Future` composition. It is a mechanism that enables cancellations. For instance, given this composition that involves an async boundary (`userService.get`) and a continuation (`.map`):

```java
Future<String> username = userService.get(userId).map(user -> user.username);
```

It is possible to raise an interrupt that is received by the `userService.get` `Promise`:

```java
username.raise(new TimeoutException);
```

The `Promise` created by `userService.get` can define a custom handler that performs an action in case an interrupt is received. 

`Promise.apply` has overloaded methods that allow the user to set the interrupt handler. This mechanism can be used to cancel requests to remote systems, as Finagle does.

The method `interruptible` is a shortcut to fail the `Promise` if it receives any interrupt signal:

```java
Future<String> username = userService.get(userId).interruptible().map(user -> user.username);
username.raise(new TimeoutException);
```

In this case, even if `userService.get` does not handle interrupts, the `Promise` is satisfied with the interrupt exception.

The interrupt propagation happens through pointers from each continuation to its parent that are created automatically by the library. In the previous example, the `map` continuation has a pointer to the `Promise` that is pending.

Benchmarks
===========

This library scores better than the main `Future` implementations available on the JVM in multiple scenarios, both in terms of throughput and memory footprint.

To run the benchmarks, use the `run.sh` script under the `future-benchmark` folder. It also outputs results for Java's, Scala's, and Twitter's `Future` implementations for comparison.

FAQ
====

**Why create a new `Future` implementation?**

This project aims to provide a `Future` implementation with the following characteristics:

1. Pure Java implementation without dependencies
2. Convenient API with combinators for common operations
3. `Local` and interrupts support, essential for non-trivial systems
4. Low CPU usage and memory footprint

Currently, there aren't other `Future` libraries with this feature set.

**Why `trane`?**

The name is in honor of the great saxophonist [John Coltrane](http://www.johncoltrane.com/), also known as Trane (his nickname).

> “Invest yourself in everything you do. There's fun in being serious.” 
-- John Coltrane

**Why is it high-performance?**

Several techniques were used to optimize this library. For an overview, please refer to [CONTRIBUTING.md](https://github.com/traneio/future/blob/master/CONTRIBUTING.md#analyzing-performance).
