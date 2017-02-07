package io.futures;

import java.util.concurrent.TimeUnit;
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
  public Promise<String> newPromise() {
    return Promise.<String>apply();
  }

  @Benchmark
  public Future<String> value() {
    return Future.value(string);
  }

  @Benchmark
  public Future<String> exception() {
    return Future.<String>exception(exception);
  }

  @Benchmark
  public String mapConst() throws CheckedFutureException {
    return constFuture.map(mapF).get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String mapConstN() throws CheckedFutureException {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String mapPromise() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.map(mapF);
    p.setValue(string);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String mapPromiseN() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    p.setValue(string);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String flatMapConst() throws CheckedFutureException {
    return constFuture.flatMap(flatMapF).get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String flatMapConstN() throws CheckedFutureException {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String flatMapPromise() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.flatMap(flatMapF);
    p.setValue(string);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String flatMapPromiseN() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
    p.setValue(string);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public Void ensureConst() throws CheckedFutureException {
    return constVoidFuture.ensure(ensureF).get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public Void ensureConstN() throws CheckedFutureException {
    Future<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f = f.ensure(ensureF);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public Void ensurePromise() throws CheckedFutureException {
    Promise<Void> p = Promise.<Void>apply();
    Future<Void> f = p.ensure(ensureF);
    p.setValue(null);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public Void ensurePromiseN() throws CheckedFutureException {
    Promise<Void> p = Promise.apply();
    Future<Void> f = p;
    for (int i = 0; i < 100; i++)
      f = f.ensure(ensureF);
    p.setValue(null);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String setValue() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    p.setValue(string);
    return p.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String setValueWithContinuations() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    for (int i = 0; i < 100; i++)
      p.map(mapF);
    p.setValue(string);
    return p.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }

  @Benchmark
  public String setValueWithNestedContinuation() throws CheckedFutureException {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    p.setValue(string);
    return f.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
  }
}
