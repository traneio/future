package io.trane.future;

import java.util.ArrayList;
import java.util.List;

public final class Scheduler {

  private static ThreadLocal<Scheduler> local = new ThreadLocal<Scheduler>() {
    @Override
    public Scheduler initialValue() {
      return new Scheduler();
    }
  };

  public static void submit(final Runnable r) {
    local.get().addTask(r);
  }

  private List<Runnable> tasks = null;

  private boolean running = false;

  private void addTask(final Runnable r) {
    if (tasks == null)
      tasks = new ArrayList<>(1);
    tasks.add(r);
    if (!running)
      run();
  }

  private void run() {
    running = true;
    while (tasks != null) {
      final List<Runnable> pending = tasks;
      tasks = null;
      pending.forEach(Runnable::run);
    }
    running = false;
  }
}
