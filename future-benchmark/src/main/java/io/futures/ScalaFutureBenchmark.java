package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

import scala.Function1;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.Duration.Infinite;
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
  private static final Infinite inf = Duration.Inf();

  @Benchmark
  public Promise<String> newPromise() {
    return Promise.<String>apply();
  }

  @Benchmark
  public Future<String> value() {
    return Future.successful(string);
  }

  @Benchmark
  public Future<String> exception() {
    return Future$.MODULE$.<String>failed(exception);
  }

  @Benchmark
  public String mapConst() throws Exception {
    return Await.result(constFuture.map(mapF, ec), inf);
  }

  @Benchmark
  public String mapConstN() throws Exception {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF, ec);
    return Await.result(f, inf);
  }

  @Benchmark
  public String mapPromise() throws Exception {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future().map(mapF, ec);
    p.success(string);
    return Await.result(f, inf);
  }

  @Benchmark
  public String mapPromiseN() throws Exception {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future();
    for (int i = 0; i < 100; i++)
      f = f.map(mapF, ec);
    p.success(string);
    return Await.result(f, inf);
  }

  @Benchmark
  public String flatMapConst() throws Exception {
    return Await.result(constFuture.flatMap(flatMapF, ec), inf);
  }

  @Benchmark
  public String flatMapConstN() throws Exception {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF, ec);
    return Await.result(f, inf);
  }

  @Benchmark
  public String flatMapPromise() throws Exception {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future().flatMap(flatMapF, ec);
    p.success(string);
    return Await.result(f, inf);
  }

  @Benchmark
  public String flatMapPromiseN() throws Exception {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future();
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF, ec);
    p.success(string);
    return Await.result(f, inf);
  }

  @Benchmark
  public Void ensureConst() throws Exception {
    return Await.result(constVoidFuture.transform(ensureF, ec), inf);
  }

  @Benchmark
  public Void ensureConstN() throws Exception {
    Future<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f.transform(ensureF, ec);
    return Await.result(f, inf);
  }

  @Benchmark
  public Void ensurePromise() throws Exception {
    Promise<Void> p = Promise.<Void>apply();
    Future<Void> f = p.future().transform(ensureF, ec);
    p.success(null);
    return Await.result(f, inf);
  }

  @Benchmark
  public Void ensurePromiseN() throws Exception {
    Promise<Void> p = Promise.<Void>apply();
    Future<Void> f = p.future();
    for (int i = 0; i < 100; i++)
      f = f.transform(ensureF, ec);
    p.success(null);
    return Await.result(f, inf);
  }
  
  @Benchmark
  public String setValue() throws Exception {
    Promise<String> p = Promise.<String>apply();
    p.success(string);
    return Await.result(p.future(), inf);
  }

  @Benchmark
  public String setValueN() throws Exception {
    Promise<String> p = Promise.<String>apply();
    Future<String> f = p.future();
    for (int i = 0; i < 100; i++)
      f = f.map(mapF, ec);
    p.success(string);
    return Await.result(f, inf);
  }
}
