package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

import net.jodah.recurrent.util.concurrent.Scheduler;

public interface SyncRecurrent {
  <T> T get(Callable<T> callable);

  <T> T get(ContextualCallable<T> callable);

  void run(CheckedRunnable runnable);

  void run(ContextualRunnable runnable);

  SyncRecurrent with(Listeners<?> listeners);

  AsyncRecurrent with(ScheduledExecutorService executor);

  AsyncRecurrent with(Scheduler scheduler);
}