package io.futures;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.Benchmark;

public class JavaAsyncFutureBenchmark {

  private static final RuntimeException exception = new RuntimeException();
  private static final Supplier<Integer> exceptionSupplier = () -> { throw exception; };
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
  public void exception() {
    CompletableFuture.supplyAsync(exceptionSupplier);
  }

  @Benchmark
  public void mapConst() {
    constFuture.thenApplyAsync(mapF);
  }

  @Benchmark
  public void mapPromise() {
    (new CompletableFuture<Integer>()).thenApplyAsync(mapF);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.thenComposeAsync(flatMapF);
  }

  @Benchmark
  public void flatMapPromise() {
    (new CompletableFuture<Integer>()).thenComposeAsync(flatMapF);
  }
}
