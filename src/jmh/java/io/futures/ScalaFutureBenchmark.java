package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;

public class ScalaFutureBenchmark {

  private static final ExecutionContext ec = scala.concurrent.ExecutionContext.global();
  private static final Future<Integer> constFuture = Future.successful(1);
  private static final Function1<Integer, Integer> mapF = i -> i + 1;
  private static final Function1<Integer, Future<Integer>> flatMapF = i -> constFuture;

  @Benchmark
  public void newPromise() {
    Promise.<Integer>apply();
  }
  
  @Benchmark
  public void newFutureFromPromise() {
    Promise.<Integer>apply().future();
  }

  @Benchmark
  public void value() {
    Future.successful(1);
  }

  @Benchmark
  public void mapConst() {
    constFuture.map(mapF, ec);
  }

  @Benchmark
  public void mapPromise() {
    (Promise.<Integer>apply()).future().map(mapF, ec);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.flatMap(flatMapF, ec);
  }

  @Benchmark
  public void flatMapPromise() {
    (Promise.<Integer>apply()).future().flatMap(flatMapF, ec);
  }
}
