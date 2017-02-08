package io.trane.future;

import org.openjdk.jmh.annotations.Benchmark;

import monix.eval.Task;
import monix.execution.Cancelable;
import monix.execution.ExecutionModel;
import monix.execution.Scheduler;
import scala.Function1;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.Duration.Infinite;
import scala.runtime.BoxedUnit;
import scala.util.Try;

public class MonixSyncTaskBenchmark {

  private static final String string = "s";
  private static final Try<String> tryString = Try.apply(() -> string);
  private static final Try<BoxedUnit> tryUnit = Try.apply(() -> BoxedUnit.UNIT);
  private static final RuntimeException exception = new RuntimeException();
  private static final Task<String> constTask = Task.now(string);
  private static final Task<BoxedUnit> constVoidTask = Task.unit();
  private static final Function1<String, String> mapF = i -> string;
  private static final Function1<String, Task<String>> flatMapF = i -> constTask;
  private static final Function1<BoxedUnit, BoxedUnit> ensureF = b -> BoxedUnit.UNIT;
  private static final Infinite inf = Duration.Inf();

  private static final Scheduler scheduler = monix.execution.Scheduler.global()
      .withExecutionModel(ExecutionModel.SynchronousExecution$.MODULE$);

  @Benchmark
  public Task<String> newPromise() {
    return Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
  }

  @Benchmark
  public Task<String> value() {
    return Task.now(string);
  }

  @Benchmark
  public Task<Object> exception() {
    return Task.raiseError(exception);
  }
  
  @Benchmark
  public String mapConst() throws Exception {
    return Await.result(constTask.map(mapF).runAsync(scheduler), inf);
  }

  @Benchmark
  public String mapConstN() throws Exception {
    Task<String> f = constTask;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    return Await.result(f.runAsync(scheduler), inf);
  }

  @Benchmark
  public String mapPromise() throws Exception {
    Task<String> p = Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
    return Await.result(p.map(mapF).runAsync(scheduler), inf);
  }

  @Benchmark
  public String mapPromiseN() throws Exception {
    Task<String> p = Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
    ;
    for (int i = 0; i < 100; i++)
      p = p.map(mapF);
    return Await.result(p.runAsync(scheduler), inf);
  }

  @Benchmark
  public String flatMapConst() throws Exception {
    return Await.result(constTask.flatMap(flatMapF).runAsync(scheduler), inf);
  }

  @Benchmark
  public String flatMapConstN() throws Exception {
    Task<String> f = constTask;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
    return Await.result(f.runAsync(scheduler), inf);
  }

  @Benchmark
  public String flatMapPromise() throws Exception {
    Task<String> p = Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
    return Await.result(p.flatMap(flatMapF).runAsync(scheduler), inf);
  }

  @Benchmark
  public String flatMapPromiseN() throws Exception {
    Task<String> p = Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
    for (int i = 0; i < 100; i++)
      p = p.flatMap(flatMapF);
    return Await.result(p.runAsync(scheduler), inf);
  }

  @Benchmark
  public BoxedUnit ensureConst() throws Exception {
    return Await.result(constVoidTask.foreachL(ensureF).runAsync(scheduler), inf);
  }

  @Benchmark
  public BoxedUnit ensureConstN() throws Exception {
    Task<BoxedUnit> f = constVoidTask;
    for (int i = 0; i < 100; i++)
      f.foreachL(ensureF);
    return Await.result(f.runAsync(scheduler), inf);
  }

  @Benchmark
  public BoxedUnit ensurePromise() throws Exception {
    Task<BoxedUnit> p = Task.create((s, c) -> {
      c.apply(tryUnit);
      return Cancelable.empty();
    });
    return Await.result(p.foreachL(ensureF).runAsync(scheduler), inf);
  }
  
  public static void main(String[] args) throws Exception {
    Task<BoxedUnit> p = Task.create((s, c) -> {
      c.apply(tryUnit);
      return Cancelable.empty();
    });
    for (int i = 0; i < 100; i++)
      p = p.foreachL(ensureF);
    Await.result(p.runAsync(scheduler), inf);
  }

  @Benchmark
  public BoxedUnit ensurePromiseN() throws Exception {
    Task<BoxedUnit> p = Task.create((s, c) -> {
      c.apply(tryUnit);
      return Cancelable.empty();
    });
    for (int i = 0; i < 100; i++)
      p = p.foreachL(ensureF);
    return Await.result(p.runAsync(scheduler), inf);
  }

  @Benchmark
  public String setValue() throws Exception {
    Task<String> p = Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
    return Await.result(p.runAsync(scheduler), inf);
  }

  @Benchmark
  public String setValueN() throws Exception {
    Task<String> p = Task.create((s, c) -> {
      c.apply(tryString);
      return Cancelable.empty();
    });
    for (int i = 0; i < 100; i++)
      p = p.map(mapF);
    return Await.result(p.runAsync(scheduler), inf);
  }
}
