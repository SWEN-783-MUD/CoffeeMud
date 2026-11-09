import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.StdTest;
import com.planet_ink.coffee_mud.Tests.interfaces.CMTest;

/**
 * Checks how the runner reports passes, failures, and timeouts.
 * Each example controls its own result, so these checks can run without a server.
 */
public final class HostTestRunnerContractTest
{
	/** A small test with a caller-supplied body and observable cleanup. */
	private static final class Example extends StdTest
	{
		/** The code to run when the harness calls {@code doTest}. */
		private final Callable<String> action;

		/** Lets the caller wait for cleanup after requesting cancellation. */
		private final CountDownLatch cleaned = new CountDownLatch(1);

		/** Number of cleanup calls made by the test worker. */
		private int cleanupCount;

		/** Whether the cleanup after the test should throw an error. */
		private boolean failCleanup;

		/**
		 * Creates a test whose body can pass, fail, or wait for an interrupt.
		 *
		 * @param action body to execute on the runner's test thread
		 */
		Example(final Callable<String> action)
		{
			this.action = action;
		}

		/**
		 * Makes this example selectable for the group lookup checks.
		 *
		 * @return the example test group
		 */
		@Override
		public String[] getTestGroups()
		{
			return new String[] {"example"};
		}

		/**
		 * Runs the body supplied by the check. The examples need no player or room.
		 *
		 * @param mob test character supplied by the runner
		 * @param flags command flags supplied by the runner
		 * @param what group name or test ID used to select this test
		 * @param commands extra test arguments
		 * @return the example's failure message, or {@code null} for a pass
		 * @throws IllegalStateException if the example body throws an exception
		 */
		@Override
		public String doTest(final MOB mob, final int flags, final String what, final List<String> commands)
		{
			try
			{
				return action.call();
			}
			catch(final Exception error)
			{
				throw new IllegalStateException(error);
			}
		}

		/**
		 * Counts cleanup calls and signals the waiting check on the second call.
		 *
		 * @throws AssertionError if this example is set up to fail during final cleanup
		 */
		@Override
		public void cleanupTest()
		{
			if(++cleanupCount == 2)
			{
				cleaned.countDown();
				if(failCleanup)
					throw new AssertionError("cleanup failure");
			}
		}
	}

	/**
	 * Fails this harness check if an expected result is missing.
	 *
	 * @param condition whether the expectation was met
	 * @param message what went wrong if the expectation failed
	 * @throws AssertionError if the condition is false
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}

	/**
	 * Checks the runner's behavior or produces one deliberate process failure.
	 * The launcher uses the failure mode to verify that a failed test reaches Python.
	 *
	 * @param args use {@code --fail} to run the failure example by itself
	 * @throws InterruptedException if the check thread is interrupted while waiting
	 */
	public static void main(final String[] args) throws InterruptedException
	{
		// Let the launcher check the exit status in a separate process.
		if(args.length == 1 && args[0].equals("--fail"))
		{
			final Example example = new Example(() -> "deliberate failure");
			final HostTestRunner.Result failure = HostTestRunner.runOne(example, null, "example", 1000);
			System.exit(HostTestRunner.exitCode(Collections.singletonList(failure)));
		}

		// A pass requires a test result and both cleanup calls.
		final Example passing = new Example(() -> null);
		final HostTestRunner.Result pass = HostTestRunner.runOne(passing, null, "example", 1000);
		check(pass.failure == null && passing.cleanupCount == 2, "Passing tests must clean up before and after.");
		check(HostTestRunner.exitCode(Collections.singletonList(pass)) == 0, "A passing suite must exit successfully.");
		check(HostTestRunner.exitCode(Collections.emptyList()) != 0, "An empty suite must fail.");

		// Keep returned failure messages and mark the whole suite as failed.
		final Example failing = new Example(() -> "expected failure");
		final HostTestRunner.Result failed = HostTestRunner.runOne(failing, null, "example", 1000);
		check("expected failure".equals(failed.failure), "Preserve returned failure messages.");
		check(HostTestRunner.exitCode(Arrays.asList(pass, failed)) != 0, "One failure must fail the suite.");

		// Capture thrown assertions and still finish cleanup.
		final Example throwing = new Example(() -> {
			throw new AssertionError("assertion failure");
		});
		final HostTestRunner.Result thrown = HostTestRunner.runOne(throwing, null, "example", 1000);
		check(thrown.failure.contains("assertion failure"), "Assertions must fail.");
		check(throwing.cleanupCount == 2, "A throwing test must still clean up.");

		// A successful test body can still fail during cleanup.
		final Example dirty = new Example(() -> null);
		dirty.failCleanup = true;
		final HostTestRunner.Result cleanup = HostTestRunner.runOne(dirty, null, "example", 1000);
		check(cleanup.failure.contains("Cleanup failed"), "Cleanup errors must fail.");

		// Block until the runner interrupts the worker, then wait for its cleanup.
		final Example blocked = new Example(() -> {
			new CountDownLatch(1).await();
			return null;
		});
		check(HostTestRunner.runOne(blocked, null, "example", 100).timedOut, "A blocked test must time out.");
		check(blocked.cleaned.await(1, TimeUnit.SECONDS), "A cancelled cooperative test must clean up.");

		// Group lookup ignores case and treats an unknown group as an error.
		final List<CMTest> registered = Collections.singletonList(passing);
		final List<CMTest> selected = HostTestRunner.selectTests(Collections.enumeration(registered), true, "EXAMPLE");
		check(selected.size() == 1, "Group lookup ignores case.");
		try
		{
			HostTestRunner.selectTests(Collections.enumeration(registered), true, "missing");
			throw new AssertionError("An unknown group must fail.");
		}
		catch(final IllegalArgumentException expected)
		{
			check(expected.getMessage().contains("No tests found"), "Report an unknown group clearly.");
		}

		System.out.println("PASS: Host runner contracts (including expected failures and timeout).");
	}
}
