package io.trane.future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import io.trane.future.CheckedFutureException;
import io.trane.future.Future;
import io.trane.future.FuturePool;

public class FuturePoolTest {

  private <T> T get(Future<T> future) throws CheckedFutureException {
    return future.get(100, TimeUnit.MILLISECONDS);
  }

  @Test
  public void isolate() throws CheckedFutureException {
    ExecutorService es = Executors.newCachedThreadPool();
    try {
      FuturePool pool = new FuturePool(es);
      Thread originalThread = Thread.currentThread();

      Future<Integer> future = pool.isolate(() -> {
        assertNotEquals(originalThread, Thread.currentThread());
        return Future.value(1);
      });

      assertEquals(new Integer(1), get(future));
    } finally {
      es.shutdown();
    }
  }

  @Test
  public void async() throws CheckedFutureException {
    ExecutorService es = Executors.newCachedThreadPool();
    try {
      FuturePool pool = new FuturePool(es);
      Thread originalThread = Thread.currentThread();

      Future<Integer> future = pool.async(() -> {
        assertNotEquals(originalThread, Thread.currentThread());
        return 1;
      });

      assertEquals(new Integer(1), get(future));
    } finally {
      es.shutdown();
    }
  }

  @Test(expected = RejectedExecutionException.class)
  public void asyncRejected() throws CheckedFutureException {
    ExecutorService es = new RejectExecutorService();
    FuturePool pool = new FuturePool(es);
    get(pool.async(() -> 1));
  }

  class RejectExecutorService implements ExecutorService {

    @Override
    public void execute(Runnable command) {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
      return null;
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return false;
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
      throw new RejectedExecutionException();
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
      throw new RejectedExecutionException();
    }

    @Override
    public java.util.concurrent.Future<?> submit(Runnable task) {
      throw new RejectedExecutionException();
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
        TimeUnit unit) throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }
  }
}
