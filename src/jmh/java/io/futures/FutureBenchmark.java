package io.futures;

import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;

public class FutureBenchmark {

  private static final RuntimeException exception = new RuntimeException();
  private static final Future<Integer> constFuture = Future.value(1);
  private static final Function<Integer, Integer> mapF = i -> i + 1;
  private static final Function<Integer, Future<Integer>> flatMapF = i -> constFuture;

  @Benchmark
  public void newPromise() {
    new Promise<Integer>();
  }
  
  @Benchmark
  public void newFutureFromPromise() {
    new Promise<Integer>();
  }

  @Benchmark
  public void value() {
    Future.value(1);
  }
  
  @Benchmark
  public void exception() {
    Future.exception(exception);
  }

  @Benchmark
  public void mapConst() {
    constFuture.map(mapF);
  }

  @Benchmark
  public void mapPromise() {
    (new Promise<Integer>()).map(mapF);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.flatMap(flatMapF);
  }

  @Benchmark
  public void flatMapPromise() {
    (new Promise<Integer>()).flatMap(flatMapF);
  }
}
