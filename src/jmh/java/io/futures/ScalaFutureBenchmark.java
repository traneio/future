package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.concurrent.Promise;
import scala.util.Try;

public class ScalaFutureBenchmark {

  private static final String string = "s";
  private static final RuntimeException exception = new RuntimeException();
  private static final ExecutionContext ec = scala.concurrent.ExecutionContext.global();
  private static final Future<String> constFuture = Future.successful(string);
  private static final Future<Void> constVoidFuture = Future.successful(null);
  private static final Function1<String, String> mapF = i -> string;
  private static final Function1<String, Future<String>> flatMapF = i -> constFuture;
  private static final Function1<Try<Void>, Try<Void>> ensureF = t -> t;

  @Benchmark
  public void newPromise() {
    Promise.<String>apply();
  }

  @Benchmark
  public void newFutureFromPromise() {
    Promise.<String>apply().future();
  }

  @Benchmark
  public void value() {
    Future.successful(1);
  }

  @Benchmark
  public void exception() {
    Future$.MODULE$.failed(exception);
  }

  @Benchmark
  public void mapConst() {
    constFuture.map(mapF, ec);
  }

  @Benchmark
  public void mapConstN() {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF, ec);
  }

  @Benchmark
  public void mapPromise() {
    (Promise.<String>apply()).future().map(mapF, ec);
  }

  @Benchmark
  public void mapPromiseN() {
    Future<String> f = Promise.<String>apply().future();
    for (int i = 0; i < 100; i++)
      f = f.map(mapF, ec);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.flatMap(flatMapF, ec);
  }

  @Benchmark
  public void flatMapConstN() {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF, ec);
  }

  @Benchmark
  public void flatMapPromise() {
    (Promise.<String>apply()).future().flatMap(flatMapF, ec);
  }

  @Benchmark
  public void flatMapPromiseN() {
    Future<String> f = (Promise.<String>apply()).future();
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF, ec);
  }

  @Benchmark
  public void ensureConst() {
    constVoidFuture.transform(ensureF, ec);
  }

  @Benchmark
  public void ensureConstN() {
    Future<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f.transform(ensureF, ec);
  }

  @Benchmark
  public void ensurePromise() {
    Promise.<Void>apply().future().transform(ensureF, ec);
  }

  @Benchmark
  public void ensurePromiseN() {
    Future<Void> f = Promise.<Void>apply().future();
    for (int i = 0; i < 100; i++)
      f = f.transform(ensureF, ec);
  }
  
  @Benchmark
  public void setValue() {
    (Promise.<String>apply()).success(string);
  }

  @Benchmark
  public void setValueWithContinuations() {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future();
    for (int i = 0; i < 100; i++)
      f.map(mapF, ec);
    p.success(string);
  }

  @Benchmark
  public void setValueWithNestedContinuation() {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future();
    for (int i = 0; i < 100; i++)
      f = f.map(mapF, ec);
    p.success(string);
  }
}
