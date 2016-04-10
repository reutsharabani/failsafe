package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;
import net.jodah.recurrent.util.concurrent.Schedulers;

/**
 * Performs invocations with synchronous or asynchronous retries according to a {@link RetryPolicy}. Asynchronous
 * retries can optionally be performed on a {@link AsyncRunnable} or {@link AsyncCallable} which allow invocations to be
 * manually retried or completed.
 * 
 * @author Jonathan Halterman
 */
public class Recurrent<T> {
  private static class AsyncRecurrentInternal implements AsyncRecurrent {
    private final RetryPolicy retryPolicy;
    private Scheduler scheduler;
    private final Listeners<?> listeners;
    private AsyncListeners<?> asyncListeners;

    private AsyncRecurrentInternal(RetryPolicy retryPolicy, Scheduler scheduler, Listeners<?> listeners) {
      this.retryPolicy = retryPolicy;
      this.scheduler = scheduler;
      this.listeners = listeners;
    }

    @Override
    public <T> CompletableFuture<T> future(AsyncCallable<CompletableFuture<T>> callable) {
      java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
      call(AsyncContextualCallable.ofFuture(callable), RecurrentFuture.of(response, scheduler, getListeners()));
      return response;
    }

    @Override
    public <T> CompletableFuture<T> future(Callable<CompletableFuture<T>> callable) {
      java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
      call(AsyncContextualCallable.ofFuture(callable), RecurrentFuture.of(response, scheduler, getListeners()));
      return response;
    }

    @Override
    public <T> CompletableFuture<T> future(ContextualCallable<CompletableFuture<T>> callable) {
      java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
      call(AsyncContextualCallable.ofFuture(callable), RecurrentFuture.of(response, scheduler, getListeners()));
      return response;
    }

    @SuppressWarnings("unchecked")
    private <T> Listeners<T> getListeners() {
      return (AsyncListeners<T>) (asyncListeners != null ? asyncListeners : listeners);
    }

    @Override
    public <T> RecurrentFuture<T> get(Callable<T> callable) {
      return call(AsyncContextualCallable.of(callable), null);
    }

    @Override
    public <T> RecurrentFuture<T> get(ContextualCallable<T> callable) {
      return call(AsyncContextualCallable.of(callable), null);
    }

    @Override
    public <T> RecurrentFuture<T> getAsync(AsyncCallable<T> callable) {
      return call(AsyncContextualCallable.of(callable), null);
    }

    @Override
    public RecurrentFuture<Void> run(CheckedRunnable runnable) {
      return call(AsyncContextualCallable.of(runnable), null);
    }

    @Override
    public RecurrentFuture<Void> run(ContextualRunnable runnable) {
      return call(AsyncContextualCallable.of(runnable), null);
    }

    @Override
    public RecurrentFuture<Void> runAsync(AsyncRunnable runnable) {
      return call(AsyncContextualCallable.of(runnable), null);
    }

    @Override
    public AsyncRecurrent with(AsyncListeners<?> listeners) {
      Assert.state(this.listeners == null, "cannot configure Listeners and AsyncListeners");
      this.asyncListeners = Assert.notNull(listeners, "listeners");
      return this;
    }

    /**
     * Calls the asynchronous {@code callable} via the {@code executor}, performing retries according to the
     * {@code retryPolicy}.
     * 
     * @throws NullPointerException if any argument is null
     */
    @SuppressWarnings("unchecked")
    private <T> RecurrentFuture<T> call(AsyncContextualCallable<T> callable, RecurrentFuture<T> future) {
      Listeners<T> listeners = getListeners();
      if (future == null)
        future = new RecurrentFuture<T>(scheduler, listeners);
      AsyncInvocation invocation = new AsyncInvocation(callable, retryPolicy, scheduler, future, listeners);
      future.initialize(invocation);
      callable.initialize(invocation);
      future.setFuture((Future<T>) scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS));
      return future;
    }
  }

  private static class SyncRecurrentInternal implements SyncRecurrent {
    private final RetryPolicy retryPolicy;
    private Listeners<?> listeners;

    private SyncRecurrentInternal(RetryPolicy retryPolicy) {
      this.retryPolicy = retryPolicy;
    }

    @Override
    public <T> T get(Callable<T> callable) {
      return call(Assert.notNull(callable, "callable"));
    }

    @Override
    public <T> T get(ContextualCallable<T> callable) {
      return call(SyncContextualCallable.of(callable));
    }

    @Override
    public void run(CheckedRunnable runnable) {
      call(Callables.of(runnable));
    }

    @Override
    public void run(ContextualRunnable runnable) {
      call(SyncContextualCallable.of(runnable));
    }

    @Override
    public SyncRecurrent with(Listeners<?> listeners) {
      this.listeners = Assert.notNull(listeners, "listeners");
      return this;
    }

    @Override
    public AsyncRecurrent with(ScheduledExecutorService executor) {
      return with(Schedulers.of(Assert.notNull(executor, "executor")));
    }

    @Override
    public AsyncRecurrent with(Scheduler scheduler) {
      return new AsyncRecurrentInternal(retryPolicy, Assert.notNull(scheduler, "scheduler"), listeners);
    }

    /**
     * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
     * 
     * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded or if
     *           interrupted while waiting to perform a retry.
     */
    @SuppressWarnings("unchecked")
    private <T> T call(Callable<T> callable) {
      Invocation invocation = new Invocation(retryPolicy);

      // Handle contextual calls
      if (callable instanceof SyncContextualCallable)
        ((SyncContextualCallable<T>) callable).initialize(invocation);

      Listeners<T> typedListeners = (Listeners<T>) listeners;
      T result = null;
      Throwable failure;

      while (true) {
        try {
          failure = null;
          result = callable.call();
        } catch (Throwable t) {
          failure = t;
        }

        boolean completed = invocation.complete(result, failure, true);
        boolean success = completed && failure == null;
        boolean shouldRetry = completed ? false : invocation.canRetryForInternal(result, failure);

        // Handle failure
        if (!success && typedListeners != null)
          typedListeners.handleFailedAttempt(result, failure, invocation, null);

        // Handle retry needed
        if (shouldRetry) {
          try {
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(invocation.waitTime));
          } catch (InterruptedException e) {
            throw new RecurrentException(e);
          }

          if (typedListeners != null)
            typedListeners.handleRetry(result, failure, invocation, null);
        }

        // Handle completion
        if (completed || !shouldRetry) {
          if (typedListeners != null)
            typedListeners.complete(result, failure, invocation, success);
          if (success || failure == null)
            return result;
          RecurrentException re = failure instanceof RecurrentException ? (RecurrentException) failure
              : new RecurrentException(failure);
          throw re;
        }
      }
    }
  }

  public static SyncRecurrent with(RetryPolicy retryPolicy) {
    return new SyncRecurrentInternal(retryPolicy);
  }
}
