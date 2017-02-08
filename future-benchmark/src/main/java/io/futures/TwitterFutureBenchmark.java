package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

import com.twitter.util.Await;
import com.twitter.util.Future;
import com.twitter.util.Promise;

import scala.Function0;
import scala.Function1;
import scala.runtime.BoxedUnit;

public class TwitterFutureBenchmark {

  private static final String string = "s";
  private static final RuntimeException exception = new RuntimeException();
  private static final Future<String> constFuture = Future.value(string);
  private static final Future<Void> constVoidFuture = Future.Void();
  private static final Function1<String, String> mapF = i -> string;
  private static final Function1<String, Future<String>> flatMapF = i -> constFuture;
  private static final Function0<BoxedUnit> ensureF = () -> BoxedUnit.UNIT;

  @Benchmark
  public Promise<String> newPromise() {
    return new Promise<String>();
  }

  @Benchmark
  public Future<String> value() {
    return Future.value(string);
  }

  @Benchmark
  public Future<Object> exception() {
    return Future.exception(exception);
  }

  @Benchmark
  public String mapConst() throws Exception {
    return Await.result(constFuture.map(mapF));
  }

  @Benchmark
  public String mapConstN() throws Exception {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    return Await.result(f);
  }

  @Benchmark
  public String mapPromise() throws Exception {
    Promise<String> p = new Promise<String>();
    Future<String> f = p.map(mapF);
    p.setValue(string);
    return Await.result(f);
  }

  @Benchmark
  public String mapPromiseN() throws Exception {
    Promise<String> p = new Promise<String>();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    p.setValue(string);
    return Await.result(f);
  }

  @Benchmark
  public String flatMapConst() throws Exception {
    return Await.result(constFuture.flatMap(flatMapF));
  }

  @Benchmark
  public String flatMapConstN() throws Exception {
    Future<String> f = constFuture;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
    return Await.result(f);
  }

  @Benchmark
  public String flatMapPromise() throws Exception {
    Promise<String> p = new Promise<String>();
    Future<String> f = p.flatMap(flatMapF);
    p.setValue(string);
    return Await.result(f);
  }

  @Benchmark
  public String flatMapPromiseN() throws Exception {
    Promise<String> p = new Promise<String>();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
    p.setValue(string);
    return Await.result(f);
  }
  
  @Benchmark
  public Void ensureConst() throws Exception {
    return Await.result(constVoidFuture.ensure(ensureF));
  }

  @Benchmark
  public Void ensureConstN() throws Exception {
    Future<Void> f = constVoidFuture;
    for (int i = 0; i < 100; i++)
      f = f.ensure(ensureF);
    return Await.result(f);
  }

  @Benchmark
  public Void ensurePromise() throws Exception {
    Promise<Void> p = new Promise<Void>();
    Future<Void> f = p.ensure(ensureF);
    p.setValue(null);
    return Await.result(f);
  }

  @Benchmark
  public Void ensurePromiseN() throws Exception {
    Promise<Void> p = new Promise<>();
    Future<Void> f = p;
    for (int i = 0; i < 100; i++)
      f = f.ensure(ensureF);
    p.setValue(null);
    return Await.result(f);
  }
  
  @Benchmark
  public String setValue() throws Exception {
    Promise<String> p = new Promise<String>();
    p.setValue(string);
    return Await.result(p);
  }
  
  @Benchmark
  public String setValueN() throws Exception {
    Promise<String> p = new Promise<String>();
    Future<String> f = p;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    p.setValue(string);
    return Await.result(f);
  }
}
