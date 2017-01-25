package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

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
  public void newPromise() {
    new Promise<String>();
  }

  @Benchmark
  public void newFutureFromPromise() {
    new Promise<String>();
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
