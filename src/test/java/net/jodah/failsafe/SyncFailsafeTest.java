/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Testing.failures;
import static net.jodah.failsafe.Testing.ignoreExceptions;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;

@Test
public class SyncFailsafeTest extends AbstractFailsafeTest {
	// Results from a synchronous Failsafe call
	private @SuppressWarnings("unchecked") Class<? extends Throwable>[] syncThrowables = new Class[] {
			ConnectException.class };
	// Results from a get against a future that wraps a synchronous Failsafe call
	private @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureSyncThrowables = new Class[] {
			ExecutionException.class, ConnectException.class };

	@BeforeMethod
	protected void beforeMethod() {
		reset(service);
		counter = new AtomicInteger();
	}

	@Override
	ScheduledExecutorService getExecutor() {
		return null;
	}

	private void assertRun(Object runnable) throws Throwable {
		// Given - Fail twice then succeed
		when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(true);

		// When
		run(Failsafe.with(retryAlways), runnable);

		// Then
		verify(service, times(3)).connect();

		// Given - Fail three times
		reset(service);
		counter.set(0);
		when(service.connect()).thenThrow(failures(10, new ConnectException()));

		// When / Then
		assertThrows(() -> {
			run(Failsafe.with(retryTwice), runnable);
		}, syncThrowables);
		verify(service, times(3)).connect();
	}

	public void shouldRun() throws Throwable {
		assertRun((CheckedRunnable) () -> service.connect());
	}

	public void shouldRunContextual() throws Throwable {
		assertRun((ContextualRunnable) context -> {
			assertEquals(context.getExecutions(), counter.getAndIncrement());
			service.connect();
		});
	}

	private void assertGet(Object callable) throws Throwable {
		// Given - Fail twice then succeed
		when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, false, true);
		RetryPolicy retryPolicy = RetryPolicy.newBuilder().retryWhen(false).build();

		assertEquals(get(Failsafe.with(retryPolicy), callable), Boolean.TRUE);
		verify(service, times(5)).connect();

		// Given - Fail three times
		reset(service);
		counter.set(0);
		when(service.connect()).thenThrow(failures(10, new ConnectException()));

		// When / Then
		assertThrows(() -> get(Failsafe.with(retryTwice), callable), syncThrowables);
		verify(service, times(3)).connect();
	}

	public void shouldGet() throws Throwable {
		assertGet((Callable<Boolean>) () -> service.connect());
	}

	public void shouldGetContextual() throws Throwable {
		assertGet((ContextualCallable<Boolean>) context -> {
			assertEquals(context.getExecutions(), counter.getAndIncrement());
			return service.connect();
		});
	}

	public void testPerStageRetries() throws Throwable {
		// Given - Fail twice then succeed
		when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, true);
		when(service.disconnect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, true);
		RetryPolicy retryPolicy = RetryPolicy.newBuilder().retryWhen(false).build();

		// When
		CompletableFuture.supplyAsync(() -> Failsafe.with(retryPolicy).get(() -> service.connect()))
				.thenRun(() -> Failsafe.with(retryPolicy).get(() -> service.disconnect())).get();

		// Then
		verify(service, times(4)).connect();
		verify(service, times(4)).disconnect();

		// Given - Fail three times
		reset(service);
		when(service.connect()).thenThrow(failures(10, new ConnectException()));

		// When / Then
		assertThrows(
				() -> CompletableFuture.supplyAsync(() -> Failsafe.with(retryTwice).get(() -> service.connect())).get(),
				futureSyncThrowables);
		verify(service, times(3)).connect();
	}

	/**
	 * Asserts that retries are performed then a non-retryable failure is thrown.
	 */
	@SuppressWarnings("unchecked")
	public void shouldThrowOnNonRetriableFailure() throws Throwable {
		// Given
		when(service.connect()).thenThrow(ConnectException.class, ConnectException.class, IllegalStateException.class);
		RetryPolicy retryPolicy = RetryPolicy.newBuilder().retryOn(ConnectException.class).build();

		// When / Then
		assertThrows(() -> Failsafe.with(retryPolicy).get(() -> service.connect()), IllegalStateException.class);
		verify(service, times(3)).connect();
	}

	public void shouldOpenCircuitWhenTimeoutExceeded() throws Throwable {
		// Given
		CircuitBreaker breaker = new CircuitBreaker().withTimeout(10, TimeUnit.MILLISECONDS);
		assertTrue(breaker.isClosed());

		// When
		Failsafe.with(breaker).run(() -> {
			Thread.sleep(20);
		});

		// Then
		assertTrue(breaker.isOpen());
	}

	/**
	 * Asserts that Failsafe throws when interrupting a waiting thread.
	 */
	public void shouldThrowWhenInterruptedDuringSynchronousDelay() throws Throwable {
		Thread mainThread = Thread.currentThread();
		new Thread(() -> {
			try {
				Thread.sleep(100);
				mainThread.interrupt();
			} catch (Exception e) {
			}
		}).start();

		try {
			Failsafe.with(RetryPolicy.newBuilder().withDelay(5, TimeUnit.SECONDS).build()).run(() -> {
				throw new Exception();
			});
		} catch (Exception e) {
			assertTrue(e instanceof FailsafeException);
			assertTrue(e.getCause() instanceof InterruptedException);
			// Clear interrupt flag
			Thread.interrupted();
		}
	}

	public void shouldRetryAndOpenCircuit() {
		CircuitBreaker circuit = new CircuitBreaker().withFailureThreshold(3).withDelay(10, TimeUnit.MINUTES);

		// Given - Fail twice then succeed
		when(service.connect()).thenThrow(failures(20, new ConnectException())).thenReturn(true);

		// When
		assertThrows(() -> Failsafe.with(retryAlways).with(circuit).run(() -> service.connect()),
				CircuitBreakerOpenException.class);

		// Then
		verify(service, times(3)).connect();
	}

	public void shouldThrowCircuitBreakerOpenExceptionAfterFailuresExceeded() {
		// Given
		CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(2).withDelay(10, TimeUnit.SECONDS);
		AtomicInteger counter = new AtomicInteger();
		CheckedRunnable runnable = () -> Failsafe.with(breaker).run(() -> {
			counter.incrementAndGet();
			throw new Exception();
		});

		// When
		ignoreExceptions(runnable);
		ignoreExceptions(runnable);

		// Then
		assertThrows(runnable, CircuitBreakerOpenException.class);
		assertEquals(counter.get(), 2);
	}

	/**
	 * Asserts that an execution is failed when the max duration is exceeded.
	 */
	public void shouldCompleteWhenMaxDurationExceeded() throws Throwable {
		when(service.connect()).thenReturn(false);
		RetryPolicy retryPolicy = RetryPolicy.newBuilder().retryWhen(false).withMaxDuration(100, TimeUnit.MILLISECONDS)
				.build();

		assertEquals(Failsafe.with(retryPolicy).onFailure((r, f) -> {
			assertEquals(r, Boolean.FALSE);
			assertNull(f);
		}).get(() -> {
			Testing.sleep(120);
			return service.connect();
		}), Boolean.FALSE);
		verify(service).connect();
	}

	public void shouldWrapCheckedExceptions() throws Throwable {
		assertThrows(() -> Failsafe.with(RetryPolicy.newBuilder().withMaxRetries(1).build()).run(() -> {
			throw new TimeoutException();
		}), FailsafeException.class, TimeoutException.class);
	}

	private void run(SyncFailsafe<?> failsafe, Object runnable) {
		if (runnable instanceof CheckedRunnable)
			failsafe.run((CheckedRunnable) runnable);
		else if (runnable instanceof ContextualRunnable)
			failsafe.run((ContextualRunnable) runnable);
	}

	@SuppressWarnings("unchecked")
	private <T> T get(SyncFailsafe<?> failsafe, Object callable) {
		if (callable instanceof Callable)
			return (T) failsafe.get((Callable<T>) callable);
		else
			return (T) failsafe.get((ContextualCallable<T>) callable);
	}
}
