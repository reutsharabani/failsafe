package net.jodah.recurrent;

import java.util.concurrent.Callable;

public interface AsyncRecurrent {
  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   * Allows asynchronous invocations to manually perform retries or completion via the {@code callable}'s
   * {@link AsyncInvocation} reference.
   * <p>
   * If the {@code callable} throws an exception or its resulting future is completed with an exception, the invocation
   * will be retried automatically, else if the {@code retryPolicy} has been exceeded the resulting future will be
   * completed exceptionally.
   * <p>
   * For non-exceptional results, retries or completion can be performed manually via the {@code callable}'s
   * {@link AsyncInvocation} reference.
   * 
   * @throws NullPointerException if any argument is null
   */
  <T> java.util.concurrent.CompletableFuture<T> future(
      AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable);

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code scheduler} according to the {@code retryPolicy}.
   * Allows asynchronous invocations to manually perform retries or completion via the {@code callable}'s
   * {@link AsyncInvocation} reference.
   * <p>
   * If the {@code callable} throws an exception or its resulting future is completed with an exception, the invocation
   * will be retried automatically, else if the {@code retryPolicy} has been exceeded the resulting future will be
   * completed exceptionally.
   * <p>
   * For non-exceptional results, retries or completion can be performed manually via the {@code callable}'s
   * {@link AsyncInvocation} reference.
   * 
   * @throws NullPointerException if any argument is null
   */
  <T> java.util.concurrent.CompletableFuture<T> future(Callable<java.util.concurrent.CompletableFuture<T>> callable);

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   * Allows asynchronous invocations to manually perform retries or completion via the {@code callable}'s
   * {@link AsyncInvocation} reference.
   * <p>
   * If the {@code callable} throws an exception or its resulting future is completed with an exception, the invocation
   * will be retried automatically, else if the {@code retryPolicy} has been exceeded the resulting future will be
   * completed exceptionally.
   * <p>
   * For non-exceptional results, retries or completion can be performed manually via the {@code callable}'s
   * {@link AsyncInvocation} reference.
   * 
   * @throws NullPointerException if any argument is null
   */
  <T> java.util.concurrent.CompletableFuture<T> future(
      ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable);

  <T> RecurrentFuture<T> get(Callable<T> callable);

  <T> RecurrentFuture<T> get(ContextualCallable<T> callable);

  <T> RecurrentFuture<T> getAsync(AsyncCallable<T> callable);

  RecurrentFuture<Void> run(CheckedRunnable runnable);

  RecurrentFuture<Void> run(ContextualRunnable runnable);

  RecurrentFuture<Void> runAsync(AsyncRunnable runnable);

  AsyncRecurrent with(AsyncListeners<?> listeners);
}