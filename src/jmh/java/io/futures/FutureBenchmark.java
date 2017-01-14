package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

public class FutureBenchmark {

  @Benchmark
  public void newPromise() {
    new Promise<Integer>();
  }

  @Benchmark
  public void newPromiseTwitter() {
    new com.twitter.util.Promise<Integer>();
  }

  @Benchmark
  public void value() {
    Future.value(1);
  }

  @Benchmark
  public void valueTwitter() {
    com.twitter.util.Future.value(1);
  }

  private static final Future<Integer> constFuture = Future.value(1);

  private static final com.twitter.util.Future<Integer> constFutureTwitter = com.twitter.util.Future.value(1);

  private static final Future<Integer> promise() {
    return new Promise<>();
  }

  private static final com.twitter.util.Future<Integer> promiseTwitter() {
    return new com.twitter.util.Promise<>();
  }

  @Benchmark
  public void mapConst() {
    constFuture.map(i -> i + 1);
  }

  @Benchmark
  public void mapConstTwitter() {
    constFutureTwitter.map(i -> i + 1);
  }

  @Benchmark
  public void mapPromise() {
    promise().map(i -> i + 1);
  }

  @Benchmark
  public void mapPromiseTwitter() {
    promiseTwitter().map(i -> i + 1);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.flatMap(i -> constFuture);
  }

  @Benchmark
  public void flatMapConstTwitter() {
    constFutureTwitter.flatMap(i -> constFutureTwitter);
  }

  @Benchmark
  public void flatMapPromise() {
    promise().flatMap(i -> constFuture);
  }

  @Benchmark
  public void flatMapPromiseTwitter() {
    promiseTwitter().flatMap(i -> constFutureTwitter);
  }

}
