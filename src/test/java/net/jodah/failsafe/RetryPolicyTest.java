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
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.failsafe.RetryPolicy;

@Test
public class RetryPolicyTest {
	void shouldFail(Runnable runnable, Class<? extends Exception> expected) {
		try {
			runnable.run();
			fail("A failure was expected");
		} catch (Exception e) {
			assertTrue(e.getClass().isAssignableFrom(expected),
					"The expected exception was not of the expected type " + e);
		}
	}

	public void testCanRetryForNull() {
		RetryPolicy policy = RetryPolicy.newBuilder().build();
		assertFalse(policy.canRetryFor(null, null));
	}

	public void testCanRetryForCompletionPredicate() {
		RetryPolicy policy = RetryPolicy.newBuilder()
				.retryIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException).build();
		assertTrue(policy.canRetryFor("test", null));
		// No retries needed for successful result
		assertFalse(policy.canRetryFor(0, null));
		assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
		assertFalse(policy.canRetryFor(null, new IllegalStateException()));
	}

	public void testCanRetryForFailurePredicate() {
		RetryPolicy policy = RetryPolicy.newBuilder().retryOn(failure -> failure instanceof ConnectException).build();
		assertTrue(policy.canRetryFor(null, new ConnectException()));
		assertFalse(policy.canRetryFor(null, new IllegalStateException()));
	}

	public void testCanRetryForResultPredicate() {
		RetryPolicy policy = RetryPolicy.newBuilder().retryIf((Integer result) -> result > 100).build();
		assertTrue(policy.canRetryFor(110, null));
		assertFalse(policy.canRetryFor(50, null));
	}

	@SuppressWarnings("unchecked")
	public void testCanRetryForFailure() {
		RetryPolicy policy = RetryPolicy.newBuilder().build();
		assertTrue(policy.canRetryFor(null, new Exception()));
		assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));

		policy = RetryPolicy.newBuilder().retryOn(Exception.class).build();
		assertTrue(policy.canRetryFor(null, new Exception()));
		assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));

		policy = RetryPolicy.newBuilder().retryOn(RuntimeException.class).build();
		assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
		assertFalse(policy.canRetryFor(null, new Exception()));

		policy = RetryPolicy.newBuilder().retryOn(IllegalArgumentException.class, IOException.class).build();
		assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
		assertTrue(policy.canRetryFor(null, new IOException()));
		assertFalse(policy.canRetryFor(null, new RuntimeException()));
		assertFalse(policy.canRetryFor(null, new IllegalStateException()));

		policy = RetryPolicy.newBuilder().retryOn(Arrays.asList(IllegalArgumentException.class)).build();
		assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
		assertFalse(policy.canRetryFor(null, new RuntimeException()));
		assertFalse(policy.canRetryFor(null, new IllegalStateException()));
	}

	public void testCanRetryForResult() {
		RetryPolicy policy = RetryPolicy.newBuilder().retryWhen(10).build();
		assertTrue(policy.canRetryFor(10, null));
		assertFalse(policy.canRetryFor(5, null));
		assertTrue(policy.canRetryFor(5, new Exception()));
	}

	public void testCanAbortForNull() {
		RetryPolicy policy = RetryPolicy.newBuilder().build();
		assertFalse(policy.canAbortFor(null, null));
	}

	public void testCanAbortForCompletionPredicate() {
		RetryPolicy policy = RetryPolicy.newBuilder()
				.abortIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException).build();
		assertTrue(policy.canAbortFor("test", null));
		assertFalse(policy.canAbortFor(0, null));
		assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));
		assertFalse(policy.canAbortFor(null, new IllegalStateException()));
	}

	public void testCanAbortForFailurePredicate() {
		RetryPolicy policy = RetryPolicy.newBuilder().abortOn(failure -> failure instanceof ConnectException).build();
		assertTrue(policy.canAbortFor(null, new ConnectException()));
		assertFalse(policy.canAbortFor(null, new IllegalArgumentException()));
	}

	public void testCanAbortForResultPredicate() {
		RetryPolicy policy = RetryPolicy.newBuilder().abortIf((Integer result) -> result > 100).build();
		assertTrue(policy.canAbortFor(110, null));
		assertFalse(policy.canAbortFor(50, null));
		assertFalse(policy.canAbortFor(50, new IllegalArgumentException()));
	}

	@SuppressWarnings("unchecked")
	public void testCanAbortForFailure() {
		RetryPolicy policy = RetryPolicy.newBuilder().abortOn(Exception.class).build();
		assertTrue(policy.canAbortFor(null, new Exception()));
		assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));

		policy = RetryPolicy.newBuilder().abortOn(IllegalArgumentException.class, IOException.class).build();
		assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));
		assertTrue(policy.canAbortFor(null, new IOException()));
		assertFalse(policy.canAbortFor(null, new RuntimeException()));
		assertFalse(policy.canAbortFor(null, new IllegalStateException()));

		policy = RetryPolicy.newBuilder().abortOn(Arrays.asList(IllegalArgumentException.class)).build();
		assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));
		assertFalse(policy.canAbortFor(null, new RuntimeException()));
		assertFalse(policy.canAbortFor(null, new IllegalStateException()));
	}

	public void testCanAbortForResult() {
		RetryPolicy policy = RetryPolicy.newBuilder().abortWhen(10).build();
		assertTrue(policy.canAbortFor(10, null));
		assertFalse(policy.canAbortFor(5, null));
		assertFalse(policy.canAbortFor(5, new IllegalArgumentException()));
	}

	public void shouldRequireValidBackoff() {
		shouldFail(() -> RetryPolicy.newBuilder().withBackoff(0, 0, null).build(), NullPointerException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withMaxDuration(1, TimeUnit.MILLISECONDS)
				.withBackoff(100, 120, TimeUnit.MILLISECONDS).build(), IllegalStateException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withBackoff(-3, 10, TimeUnit.MILLISECONDS).build(),
				IllegalArgumentException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withBackoff(100, 10, TimeUnit.MILLISECONDS).build(),
				IllegalArgumentException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withBackoff(5, 10, TimeUnit.MILLISECONDS, .5).build(),
				IllegalArgumentException.class);
	}

	public void shouldRequireValidDelay() {
		shouldFail(() -> RetryPolicy.newBuilder().withDelay(5, null).build(), NullPointerException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withMaxDuration(1, TimeUnit.MILLISECONDS)
				.withDelay(100, TimeUnit.MILLISECONDS).build(), IllegalStateException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withBackoff(1, 2, TimeUnit.MILLISECONDS)
				.withDelay(100, TimeUnit.MILLISECONDS).build(), IllegalStateException.class);
		shouldFail(() -> RetryPolicy.newBuilder().withDelay(-1, TimeUnit.MILLISECONDS).build(),
				IllegalArgumentException.class);
	}

	public void shouldRequireValidMaxRetries() {
		shouldFail(() -> RetryPolicy.newBuilder().withMaxRetries(-4).build(), IllegalArgumentException.class);
	}

	public void shouldRequireValidMaxDuration() {
		shouldFail(() -> RetryPolicy.newBuilder().withDelay(100, TimeUnit.MILLISECONDS)
				.withMaxDuration(100, TimeUnit.MILLISECONDS).build(), IllegalStateException.class);
	}

	public void testCopy() {
		RetryPolicy.Builder rpb = RetryPolicy.newBuilder();
		rpb = rpb.withBackoff(2, 20, TimeUnit.SECONDS, 2.5);
		rpb = rpb.withMaxDuration(60, TimeUnit.SECONDS);
		rpb = rpb.withMaxRetries(3);
		RetryPolicy rp = rpb.build();

		RetryPolicy rp2 = new RetryPolicy.Builder(rp).build();
		assertEquals(rp.getDelay().toNanos(), rp2.getDelay().toNanos());
		assertEquals(rp.getDelayFactor(), rp2.getDelayFactor());
		assertEquals(rp.getMaxDelay().toNanos(), rp2.getMaxDelay().toNanos());
		assertEquals(rp.getMaxDuration().toNanos(), rp2.getMaxDuration().toNanos());
		assertEquals(rp.getMaxRetries(), rp2.getMaxRetries());
	}
}
