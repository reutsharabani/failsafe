package net.jodah.failsafe;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.internal.CircuitState;

public class Testing {
  public static Throwable getThrowable(CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      return t;
    }

    return null;
  }

  public static <T> T ignoreExceptions(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      return null;
    }
  }

  public static void ignoreExceptions(CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
    }
  }

  public static Exception[] failures(int numFailures, Exception failure) {
    Exception[] failures = new Exception[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failure;
    return failures;
  }

  public static void runInThread(CheckedRunnable runnable) {
    new Thread(() -> ignoreExceptions(runnable)).start();
  }

  public static void noop() {
  }

  @SuppressWarnings("unchecked")
  public static <T extends CircuitState> T stateFor(CircuitBreaker breaker) {
    Field stateField;
    try {
      stateField = CircuitBreaker.class.getDeclaredField("state");
      stateField.setAccessible(true);
      return (T) ((AtomicReference<T>) stateField.get(breaker)).get();
    } catch (Exception e) {
      return null;
    }
  }

  public static void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException ignore) {
    }
  }
}
