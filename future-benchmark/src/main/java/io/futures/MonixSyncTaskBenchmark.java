package io.futures;


import org.openjdk.jmh.annotations.Benchmark;

import monix.eval.Task;
import monix.execution.ExecutionModel;
import monix.execution.Scheduler;
import scala.Function1;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

// TODO review - I'm not these benchmarks actually represent the same operations as the `future` ones
public class MonixSyncTaskBenchmark {

  private static final String string = "s";
  private static final RuntimeException exception = new RuntimeException();
  private static final Task<String> constTask = Task.now(string);
  private static final Task<BoxedUnit> constVoidTask = Task.unit();
  private static final Function1<String, String> mapF = i -> string;
  private static final Function1<String, Task<String>> flatMapF = i -> constTask;
  private static final Function1<BoxedUnit, BoxedUnit> ensureF = b -> BoxedUnit.UNIT;
  
  private static final Scheduler scheduler = monix.execution.Scheduler.global().withExecutionModel(ExecutionModel.SynchronousExecution$.MODULE$);

  @Benchmark
  public Task<String> newPromise() {
    return Task.apply(() -> string);
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
    return Await.result(constTask.map(mapF).runAsync(scheduler), Duration.Inf());
  }
  
  @Benchmark
  public String mapConstN() throws Exception {
    Task<String> f = constTask;
    for (int i = 0; i < 100; i++)
      f = f.map(mapF);
    return Await.result(f.runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String mapPromise() throws Exception {
    return Await.result(Task.apply(() -> string).map(mapF).runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String mapPromiseN() throws Exception {
    Task<String> p = Task.apply(() -> string);
    for (int i = 0; i < 100; i++)
      p = p.map(mapF);
    return Await.result(p.runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String flatMapConst() throws Exception {
    return Await.result(constTask.flatMap(flatMapF).runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String flatMapConstN() throws Exception {
    Task<String> f = constTask;
    for (int i = 0; i < 100; i++)
      f = f.flatMap(flatMapF);
    return Await.result(f.runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String flatMapPromise() throws Exception {
    return Await.result(Task.apply(() -> string).flatMap(flatMapF).runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String flatMapPromiseN() throws Exception {
    Task<String> p = Task.apply(() -> string);
    for (int i = 0; i < 100; i++)
      p = p.flatMap(flatMapF);
    return Await.result(p.runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public BoxedUnit ensureConst() throws Exception {
    return Await.result(constVoidTask.foreachL(ensureF).runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public BoxedUnit ensureConstN() throws Exception {
    Task<BoxedUnit> f = constVoidTask;
    for (int i = 0; i < 100; i++)
      f.foreachL(ensureF);
    return Await.result(f.runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public BoxedUnit ensurePromise() throws Exception {
    return Await.result(Task.apply(() -> BoxedUnit.UNIT).foreachL(ensureF).runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public BoxedUnit ensurePromiseN() throws Exception {
    Task<BoxedUnit> p = Task.apply(() -> BoxedUnit.UNIT);
    for (int i = 0; i < 100; i++)
      p = p.foreachL(ensureF);
    return Await.result(p.runAsync(scheduler), Duration.Inf());
  }
  
  @Benchmark
  public String setValue() throws Exception {
    return Await.result(Task.apply(() -> string).runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String setValueWithContinuations() throws Exception {
    Task<String> p = Task.apply(() -> string);
    for (int i = 0; i < 100; i++)
      p.map(mapF);
    return Await.result(p.runAsync(scheduler), Duration.Inf());
  }

  @Benchmark
  public String setValueWithNestedContinuation() throws Exception {
    Task<String> p = Task.apply(() -> string);
    for (int i = 0; i < 100; i++)
      p = p.map(mapF);
    return Await.result(p.runAsync(scheduler), Duration.Inf());
  }
}
