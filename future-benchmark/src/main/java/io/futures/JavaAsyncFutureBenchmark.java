package io.futures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.Benchmark;

public class JavaAsyncFutureBenchmark {

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
  public CompletableFuture<String> newPromise() {
    return new CompletableFuture<String>();
  }

  @Benchmark
  public CompletableFuture<String> value() {
    return CompletableFuture.completedFuture(string);
  }

  @Benchmark
  public CompletableFuture<String> exception() {
    return CompletableFuture.supplyAsync(exceptionSupplier);
  }

  @Benchmark
  public String mapConst() throws InterruptedException, ExecutionException {
    return constFuture.thenApplyAsync(mapF).get();
  }

  @Benchmark
  public String mapConstN() throws InterruptedException, ExecutionException {
    CompletableFuture<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.thenApplyAsync(mapF);
    return f.get();
  }

  @Benchmark
  public String mapPromise() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<String>();
    CompletableFuture<String> f = p.thenApplyAsync(mapF);
    p.complete(string);
    return f.get();
  }

  @Benchmark
  public String mapPromiseN() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<String>();
    CompletableFuture<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.thenApplyAsync(mapF);
    p.complete(string);
    return f.get();
  }

  @Benchmark
  public String flatMapConst() throws InterruptedException, ExecutionException {
    return constFuture.thenComposeAsync(flatMapF).get();
  }

  @Benchmark
  public String flatMapConstN() throws InterruptedException, ExecutionException {
    CompletableFuture<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.thenComposeAsync(flatMapF);
    return f.get();
  }

  @Benchmark
  public String flatMapPromise() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<String>();
    p.thenComposeAsync(flatMapF);
    p.complete(string);
    return p.get();
  }

  @Benchmark
  public String flatMapPromiseN() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<>();
    CompletableFuture<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.thenComposeAsync(flatMapF);
    p.complete(string);
    return f.get();
  }

  @Benchmark
  public Void ensureConst() throws InterruptedException, ExecutionException {
    return constVoidFuture.thenRunAsync(ensureF).get();
  }

  @Benchmark
  public Void ensureConstN() throws InterruptedException, ExecutionException {
    CompletableFuture<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f = f.thenRunAsync(ensureF);
    return f.get();
  }

  @Benchmark
  public Void ensurePromise() throws InterruptedException, ExecutionException {
    CompletableFuture<Void> p = new CompletableFuture<Void>();
    CompletableFuture<Void> f = p.thenRunAsync(ensureF);
    p.complete(null);
    return f.get();
  }

  @Benchmark
  public Void ensurePromiseN() throws InterruptedException, ExecutionException {
    CompletableFuture<Void> p = new CompletableFuture<>();
    CompletableFuture<Void> f = p;
    for (int i = 0; i < 100; i++)
      f = f.thenRunAsync(ensureF);
    p.complete(null);
    return f.get();
  }

  @Benchmark
  public String setValue() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<>();
    p.complete(string);
    return p.get();
  }

  @Benchmark
  public String setValueWithContinuations() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<>();
    for (int i = 0; i < 100; i++)
      p.thenApplyAsync(mapF);
    p.complete(string);
    return p.get();
  }

  @Benchmark
  public String setValueWithNestedContinuation() throws InterruptedException, ExecutionException {
    CompletableFuture<String> p = new CompletableFuture<>();
    CompletableFuture<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.thenApplyAsync(mapF);
    p.complete(string);
    return f.get();
  }
}
