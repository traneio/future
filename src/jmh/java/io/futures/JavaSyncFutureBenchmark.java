package io.futures;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;

public class JavaSyncFutureBenchmark {

  private static final CompletableFuture<Integer> constFuture = CompletableFuture.completedFuture(1);
  private static final Function<Integer, Integer> mapF = i -> i + 1;
  private static final Function<Integer, CompletableFuture<Integer>> flatMapF = i -> constFuture;

  @Benchmark
  public void newPromise() {
    new CompletableFuture<Integer>();
  }
  
  @Benchmark
  public void newFutureFromPromise() {
    new CompletableFuture<Integer>();
  }

  @Benchmark
  public void value() {
    CompletableFuture.completedFuture(1);
  }

  @Benchmark
  public void mapConst() {
    constFuture.thenApply(mapF);
  }

  @Benchmark
  public void mapPromise() {
    (new CompletableFuture<Integer>()).thenApply(mapF);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.thenCompose(flatMapF);
  }

  @Benchmark
  public void flatMapPromise() {
    (new CompletableFuture<Integer>()).thenCompose(flatMapF);
  }
}
