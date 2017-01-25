package io.futures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.Benchmark;

public class JavaSyncFutureBenchmark {

  private static final String string = "s";
  private static final RuntimeException exception = new RuntimeException();
  private static final Supplier<String> exceptionSupplier = () -> {
    throw exception;
  };
  private static final CompletableFuture<String> constFuture = CompletableFuture.completedFuture(string);
  private static final CompletableFuture<Void> constVoidFuture = CompletableFuture.completedFuture(null);
  private static final Function<String, String> mapF = i -> string;
  private static final Function<String, CompletableFuture<String>> flatMapF = i -> constFuture;
  private static final Runnable ensureF = () -> {
  };

  @Benchmark
  public void newPromise() {
    new CompletableFuture<String>();
  }

  @Benchmark
  public void newFutureFromPromise() {
    new CompletableFuture<String>();
  }

  @Benchmark
  public void value() {
    CompletableFuture.completedFuture(string);
  }

  @Benchmark
  public void exception() {
    CompletableFuture.supplyAsync(exceptionSupplier);
  }

  @Benchmark
  public void mapConst() {
    constFuture.thenApply(mapF);
  }

  @Benchmark
  public void mapConstN() {
    CompletionStage<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.thenApply(mapF);
  }

  @Benchmark
  public void mapPromise() {
    (new CompletableFuture<String>()).thenApply(mapF);
  }

  @Benchmark
  public void mapPromiseN() {
    CompletionStage<String> f = new CompletableFuture<String>();
    for (int i = 0; i < 100; i++)
      f = f.thenApply(mapF);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.thenCompose(flatMapF);
  }

  @Benchmark
  public void flatMapConstN() {
    CompletionStage<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.thenCompose(flatMapF);
  }

  @Benchmark
  public void flatMapPromise() {
    (new CompletableFuture<String>()).thenCompose(flatMapF);
  }

  @Benchmark
  public void flatMapPromiseN() {
    CompletionStage<String> f = new CompletableFuture<String>();
    for (int i = 0; i < 100; i++)
      f = f.thenCompose(flatMapF);
  }
  
  @Benchmark
  public void ensureConst() {
    constVoidFuture.thenRun(ensureF);
  }

  @Benchmark
  public void ensureConstN() {
    CompletionStage<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f = f.thenRun(ensureF);
  }

  @Benchmark
  public void ensurePromise() {
    new CompletableFuture<Void>().thenRun(ensureF);
  }

  @Benchmark
  public void ensurePromiseN() {
    CompletionStage<Void> f = new CompletableFuture<>();
    for (int i = 0; i < 100; i++)
      f = f.thenRun(ensureF);
  }
}
