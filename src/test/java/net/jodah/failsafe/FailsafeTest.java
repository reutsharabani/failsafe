package net.jodah.failsafe;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.ScheduledExecutorService;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import net.jodah.failsafe.util.concurrent.Scheduler;

@Test
public class FailsafeTest {
	public void testWithExecutor() {
		ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
		Scheduler scheduler = Mockito.mock(Scheduler.class);

		assertTrue(Failsafe.with(new RetryPolicy.Builder().build()) instanceof SyncFailsafe);
		assertTrue(Failsafe.with(new RetryPolicy.Builder().build()).with(executor) instanceof AsyncFailsafe);
		assertTrue(Failsafe.with(new RetryPolicy.Builder().build()).with(scheduler) instanceof AsyncFailsafe);
	}
}
