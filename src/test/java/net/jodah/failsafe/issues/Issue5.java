package net.jodah.failsafe.issues;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Test
public class Issue5 {
  /**
   * Asserts that a failure is handled as expected by a listener registered via whenFailure.
   */
  public void test() throws Throwable {
    Waiter waiter = new Waiter();
    RetryPolicy retryPolicy = new RetryPolicy.Builder().withDelay(100, TimeUnit.MILLISECONDS)
        .withMaxDuration(2, TimeUnit.SECONDS)
        .withMaxRetries(3)
        .retryWhen(null).build();

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    Failsafe.with(retryPolicy).with(executor).onFailure((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertNull(failure);
      waiter.resume();
    }).get(() -> null);

    waiter.await(1000);
  }
}
