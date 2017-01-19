package io.futures;

import org.openjdk.jmh.annotations.Benchmark;

import com.twitter.util.Future;
import com.twitter.util.Promise;

import scala.Function1;

public class TwitterFutureBenchmark {

  private static final RuntimeException exception = new RuntimeException();
  private static final Future<Integer> constFuture = Future.value(1);
  private static final Function1<Integer, Integer> mapF = i -> i + 1;
  private static final Function1<Integer, Future<Integer>> flatMapF = i -> constFuture;

  @Benchmark
  public void newPromise() {
    new Promise<Integer>();
  }
  
  @Benchmark
  public void newFutureFromPromise() {
    new Promise<Integer>();
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
  public void mapPromise() {
    (new Promise<Integer>()).map(mapF);
  }

  @Benchmark
  public void flatMapConst() {
    constFuture.flatMap(flatMapF);
  }

  @Benchmark
  public void flatMapPromise() {
    (new Promise<Integer>()).flatMap(flatMapF);
  }
}
