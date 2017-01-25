package io.futures;

import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;

public class FutureBenchmark {

  private static final String string = "s";
  private static final RuntimeException exception = new RuntimeException();
  private static final Future<String> constFuture = Future.value(string);
  private static final Future<Void> constVoidFuture = Future.VOID;
  private static final Function<String, String> mapF = i -> string;
  private static final Function<String, Future<String>> flatMapF = i -> constFuture;
  private static final Runnable ensureF = () -> {
  };

  @Benchmark
  public void newPromise() {
    new Promise<String>();
  }

  @Benchmark
  public void newFutureFromPromise() {
    new Promise<String>();
  }

  @Benchmark
  public void value() {
    Future.value(string);
  }

  @Benchmark
  public void exception() {
    Future.<String>exception(exception);
  }

  @Benchmark
  public void mapConst() {
    constFuture.map(mapF);
  }

  @Benchmark
  public void mapConstN() {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
  }

  @Benchmark
  public void mapPromise() {
    (new Promise<String>()).map(mapF);
  }

  @Benchmark
  public void mapPromiseN() {
    Future<String> f = new Promise<String>();
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.flatMap(flatMapF);
  }

  @Benchmark
  public void flatMapConstN() {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
  }

  @Benchmark
  public void flatMapPromise() {
    (new Promise<String>()).flatMap(flatMapF);
  }

  @Benchmark
  public void flatMapPromiseN() {
    Future<String> f = new Promise<String>();
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
  }

  @Benchmark
  public void ensureConst() {
    constVoidFuture.ensure(ensureF);
  }

  @Benchmark
  public void ensureConstN() {
    Future<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f = f.ensure(ensureF);
  }

  @Benchmark
  public void ensurePromise() {
    new Promise<Void>().ensure(ensureF);
  }

  @Benchmark
  public void ensurePromiseN() {
    Future<Void> f = new Promise<>();
    for (int i = 0; i < 100; i++)
      f = f.ensure(ensureF);
  }

  @Benchmark
  public void setValue() {
    (new Promise<String>()).setValue(string);
  }

  @Benchmark
  public void setValueWithContinuations() {
    Promise<String> p = new Promise<String>();
    for (int i = 0; i < 100; i++)
      p.map(mapF);
    p.setValue(string);
  }

  @Benchmark
  public void setValueWithNestedContinuation() {
    Promise<String> p = new Promise<String>();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    p.setValue(string);
  }
}
