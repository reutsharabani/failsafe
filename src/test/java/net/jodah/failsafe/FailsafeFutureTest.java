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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class FailsafeFutureTest {
	ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

	@Test(expectedExceptions = TimeoutException.class)
	public void shouldGetWithTimeout() throws Throwable {
		Failsafe.with(RetryPolicy.newBuilder().build()).with(executor).run(() -> {
			Thread.sleep(1000);
		}).get(100, TimeUnit.MILLISECONDS);

		Thread.sleep(1000);
	}

	public void shouldCompleteFutureOnCancel() throws Throwable {
		Waiter waiter = new Waiter();
		FailsafeFuture<String> future = Failsafe.with(RetryPolicy.newBuilder().build()).with(executor)
				.onComplete((r, f) -> {
					waiter.assertNull(r);
					waiter.assertTrue(f instanceof CancellationException);
					waiter.resume();
				}).get(() -> {
					Thread.sleep(5000);
					return "test";
				});

		Testing.sleep(300);
		future.cancel(true);
		waiter.await(1000);

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());
		Asserts.assertThrows(() -> future.get(), CancellationException.class);
	}

	/**
	 * Asserts that completion handlers are not called again if a completed
	 * execution is cancelled.
	 */
	public void shouldNotCancelCompletedExecution() throws Throwable {
		Waiter waiter = new Waiter();
		FailsafeFuture<String> future = Failsafe.with(RetryPolicy.newBuilder().build()).with(executor).onComplete((r, f) -> {
			waiter.assertEquals("test", r);
			waiter.assertNull(f);
			waiter.resume();
			Thread.sleep(100);
		}).get(() -> "test");

		waiter.await(1000);
		assertFalse(future.cancel(true));
		assertFalse(future.isCancelled());
		assertTrue(future.isDone());
		assertEquals(future.get(), "test");
	}
}
