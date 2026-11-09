import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.FakeSession;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.interfaces.CMTest;
import com.planet_ink.coffee_mud.application.MUD;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;

/**
 * Runs CoffeeMud's registered tests in the temporary server started by the launcher.
 * Each test gets a fresh character and room. Tests that create more world state
 * are responsible for cleaning it up before the next test starts.
 */
public final class HostTestRunner
{
	/** Stores the outcome of one check for the console log and XML report. */
	static final class Result
	{
		/** The test ID or the name of a harness check. */
		final String name;

		/** The failure message, or {@code null} if the check passed. */
		final String failure;

		/** Elapsed time in seconds, measured with {@link System#nanoTime()}. */
		final double seconds;

		/** Whether the runner stopped waiting before the test finished. */
		final boolean timedOut;

		/**
		 * Records a finished check or the point when the runner stopped waiting.
		 *
		 * @param name name to show in the report
		 * @param failure failure message, or {@code null} for a pass
		 * @param start start time from {@link System#nanoTime()}
		 * @param timedOut whether the check ran past its deadline
		 */
		Result(final String name, final String failure, final long start, final boolean timedOut)
		{
			this.name = name;
			this.failure = failure;
			this.seconds = (System.nanoTime() - start) / 1_000_000_000.0;
			this.timedOut = timedOut;
		}
	}

	/**
	 * Keeps command output in memory so the runner can check what a player would see.
	 * CoffeeMud hides sessions marked as fake. This session stays visible to commands.
	 */
	private static final class RecordingSession extends FakeSession
	{
		/**
		 * Keeps the session accessible through the test character's session getter.
		 *
		 * @return {@code false}, so commands can find this session
		 */
		@Override
		public boolean isFake()
		{
			return false;
		}
	}

	/** Owns the temporary world objects and session used by one check. */
	private static final class TestContext implements AutoCloseable
	{
		/** The area that holds the test room. */
		private final Area area = CMClass.getAreaType("StdArea");

		/** The character's starting room. */
		private final Room room = CMClass.getLocale("StdRoom");

		/** The temporary Archon passed to CoffeeMud's test API. */
		private final MOB actor = CMClass.getMOB("StdMOB");

		/** The session that captures the Archon's command output. */
		private final RecordingSession session = new RecordingSession();

		/**
		 * Creates a character that can use {@code TEST} without going through login.
		 * Call this after the host has loaded its classes and libraries.
		 */
		TestContext()
		{
			// Give the character a room in a temporary area.
			area.setName("CITestArea");
			room.setRoomID("CITestArea#0");
			room.setArea(area);

			// Set up an Archon so the real TEST command allows the check.
			actor.setName("CITestRunner");
			actor.setPlayerStats((PlayerStats)CMClass.getCommon("DefaultPlayerStats"));
			actor.baseCharStats().setMyRace(CMClass.getRace("Human"));
			actor.baseCharStats().setCurrentClass(CMClass.getCharClass("Archon"));
			actor.baseCharStats().setCurrentClassLevel(100);
			actor.basePhyStats().setLevel(100);

			// Apply the base stats before placing the character in the room.
			actor.recoverCharStats();
			actor.recoverPhyStats();
			actor.recoverMaxState();
			actor.resetToMaxState();
			actor.setStartRoom(room);
			actor.setLocation(room);
			room.addInhabitant(actor);

			// Capture player messages without opening a client connection.
			session.initialize(null, "MEMORY");
			session.setMob(actor);
			actor.setSession(session);
		}

		/**
		 * Releases the session, character, room, and area after a check finishes.
		 * A test that timed out may still use these objects, so the runner leaves
		 * them for server shutdown in that case.
		 */
		@Override
		public void close()
		{
			actor.makePeace(true);
			actor.setSession(null);
			session.setMob(null);

			// Destroy the character before the room and area it uses.
			actor.destroy();
			room.destroy();
			area.destroy();
		}
	}

	/**
	 * Finds tests by an exact group name or test ID, ignoring case.
	 *
	 * @param registered tests loaded by CoffeeMud
	 * @param byGroup whether to match a group name instead of an ID
	 * @param selector the requested group name or test ID
	 * @return a new list of matching tests in ID order
	 * @throws IllegalArgumentException if the selection matches no tests
	 */
	static List<CMTest> selectTests(final Enumeration<CMTest> registered,
			final boolean byGroup, final String selector)
	{
		final List<CMTest> selected = new ArrayList<CMTest>();
		while(registered.hasMoreElements())
		{
			final CMTest test = registered.nextElement();
			if(!byGroup)
			{
				if(test.ID().equalsIgnoreCase(selector))
					selected.add(test);
				continue;
			}

			// Add each test once even if its group list contains duplicates.
			for(final String group : test.getTestGroups())
			{
				if(group.equalsIgnoreCase(selector))
				{
					selected.add(test);
					break;
				}
			}
		}

		// Treat an empty selection as a CI failure.
		if(selected.isEmpty())
			throw new IllegalArgumentException("No tests found for " + (byGroup ? "group " : "test ") + selector);

		Collections.sort(selected, Comparator.comparing(CMTest::ID));
		return selected;
	}

	/**
	 * Runs one test on a worker so the caller can stop waiting if it gets stuck.
	 * The deadline covers the test and both cleanup calls. After a timeout, the
	 * runner asks the worker to stop, but the test may still be running.
	 *
	 * @param test registered test to run
	 * @param actor character to pass to the test, or {@code null} for a harness check
	 * @param selector group name or ID used to select the test
	 * @param timeoutMillis maximum time to wait in milliseconds
	 * @return the test result, including any timeout or cleanup failure
	 * @throws InterruptedException if the caller is interrupted while waiting
	 */
	static Result runOne(final CMTest test, final MOB actor, final String selector, final long timeoutMillis)
			throws InterruptedException
	{
		final long start = System.nanoTime();

		// The worker inherits the caller's host thread group.
		final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
			final Thread thread = new Thread(task, "CI-" + test.ID());
			thread.setDaemon(true);
			return thread;
		});
		final Future<String> pending = worker.submit(() -> runTestWithCleanup(test, actor, selector));

		// Wait for the result while keeping a limit on how long this check can take.
		try
		{
			return new Result(test.ID(), pending.get(timeoutMillis, TimeUnit.MILLISECONDS), start, false);
		}
		catch(final TimeoutException error)
		{
			return new Result(test.ID(), "Timed out after " + timeoutMillis + " ms", start, true);
		}
		catch(final ExecutionException error)
		{
			error.printStackTrace();
			return new Result(test.ID(), error.toString(), start, false);
		}
		finally
		{
			// Ask any unfinished work to stop before releasing the executor.
			pending.cancel(true);
			worker.shutdownNow();
		}
	}

	/**
	 * Runs the test between cleanup calls, as the in-game command does.
	 * Keeps the original failure message if cleanup also fails.
	 *
	 * @param test registered test to run
	 * @param actor character passed to the test
	 * @param selector group name or ID passed to the test
	 * @return a failure message, or {@code null} when the test and cleanup pass
	 */
	private static String runTestWithCleanup(final CMTest test, final MOB actor, final String selector)
	{
		String failure = null;
		try
		{
			test.cleanupTest();
			failure = test.doTest(actor, 0, selector.toUpperCase(Locale.ROOT), new ArrayList<String>());
		}
		catch(final Throwable error)
		{
			error.printStackTrace();
			failure = error.toString();
		}
		finally
		{
			try
			{
				test.cleanupTest();
			}
			catch(final Throwable error)
			{
				error.printStackTrace();
				failure = (failure == null ? "" : failure + "; ") + "Cleanup failed: " + error;
			}
		}
		return failure;
	}

	/**
	 * Converts the check results to a process exit code the launcher can use.
	 *
	 * @param results checks completed during the run
	 * @return {@code 0} if at least one check ran and all passed, or {@code 1} otherwise
	 */
	static int exitCode(final List<Result> results)
	{
		if(results.isEmpty())
			return 1;

		for(final Result result : results)
		{
			if(result.failure != null)
				return 1;
		}
		return 0;
	}

	/**
	 * Escapes quotes and other special characters in a report attribute.
	 *
	 * @param text name or failure message to write
	 * @return text with XML attribute characters escaped
	 */
	private static String escapeXmlAttribute(final String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&apos;");
	}

	/**
	 * Writes the report beside the runtime folder and prints a short summary.
	 * Writing before shutdown preserves the results if the server takes too long to stop.
	 *
	 * @param results checks to include in the report
	 * @throws IOException if the report cannot be opened
	 * @throws IllegalStateException if a write to the report fails
	 */
	private static void writeResults(final List<Result> results) throws IOException
	{
		int failures = 0;
		for(final Result result : results)
		{
			if(result.failure != null)
				failures++;
		}

		// Keep the report outside the runtime so the launcher can retain it after cleanup.
		try(PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get("../results.xml"), StandardCharsets.UTF_8)))
		{
			out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			out.println("<testsuite name=\"CoffeeMud host\" tests=\"" + results.size() + "\" failures=\"" + failures + "\">");
			for(final Result result : results)
			{
				out.println("  <testcase name=\"" + escapeXmlAttribute(result.name) + "\" time=\"" + result.seconds + "\">");
				if(result.failure != null)
					out.println("    <failure message=\"" + escapeXmlAttribute(result.failure) + "\"/>");
				out.println("  </testcase>");
			}
			out.println("</testsuite>");

			// PrintWriter saves write errors for the caller to check.
			if(out.checkError())
				throw new IllegalStateException("Could not write results.xml");
		}
		System.out.println("CI SUMMARY tests=" + results.size() + " failures=" + failures);
	}

	/**
	 * Checks that the test character can use the real {@code TEST LIST} command
	 * and see a test the runner found in the registry.
	 *
	 * @param context temporary character and recording session
	 * @param expectedTest ID that should appear in the command output
	 * @throws AssertionError if permissions or test discovery fail
	 */
	private static void commandSmoke(final TestContext context, final String expectedTest)
	{
		if(!CMClass.getCommand("Test").securityCheck(context.actor))
			throw new AssertionError("The temporary test character must have TEST permission.");

		context.actor.doCommand(new ArrayList<String>(Arrays.asList("TEST", "LIST")), 0);
		if(!context.session.getAfkMessage().toLowerCase(Locale.ROOT).contains(expectedTest.toLowerCase(Locale.ROOT)))
			throw new AssertionError("TEST LIST did not discover " + expectedTest);
	}

	/**
	 * Waits for CoffeeMud to finish loading its libraries and open the listener.
	 *
	 * @param startupTimeoutSeconds maximum time to wait for startup in seconds
	 * @throws InterruptedException if the runner is interrupted while waiting
	 * @throws IllegalStateException if the host misses its startup deadline
	 */
	private static void waitForHost(final long startupTimeoutSeconds) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(startupTimeoutSeconds);
		while(CMLib.hosts().isEmpty() || !CMLib.hosts().get(0).isAcceptingConnections())
		{
			if(System.nanoTime() >= deadline)
				throw new IllegalStateException("Server startup timed out");
			Thread.sleep(50);
		}
	}

	/**
	 * Runs the selected checks in the main host's thread group and exits the process.
	 * This thread owns the result list. Test workers return their outcomes through
	 * futures, so they never write to the list directly.
	 *
	 * @param args launcher arguments in the order documented by {@link #main(String[])}
	 */
	private static void runInHost(final String[] args)
	{
		final List<Result> results = new ArrayList<Result>();
		final long start = System.nanoTime();
		boolean ready = false;
		try
		{
			// Wait for startup before looking up registered tests or creating characters.
			waitForHost(Long.parseLong(args[4]));
			ready = true;

			// Check discovery through the player command before running the suite.
			final boolean byGroup = args[0].equals("group");
			final String selector = args[1];
			final List<CMTest> selected = selectTests(CMClass.tests(), byGroup, selector);
			try(TestContext context = new TestContext())
			{
				commandSmoke(context, selected.get(0).ID());
			}
			results.add(new Result("HostCommandSmoke", null, start, false));

			// Give each registered test its own character and room.
			final long testTimeoutMillis = Long.parseLong(args[3]) * 1000;
			for(final CMTest test : selected)
			{
				final TestContext context = new TestContext();
				Result result = null;
				try
				{
					result = runOne(test, context.actor, selector, testTimeoutMillis);
					results.add(result);
					System.out.println("CI " + (result.failure == null ? "PASS " : "FAIL ") + result.name
							+ (result.failure == null ? "" : ": " + result.failure));

					// Stop the suite if the worker may still be using the world.
					if(result.timedOut)
						break;
				}
				finally
				{
					// Leave a timed-out test's objects for server shutdown.
					if(result == null || !result.timedOut)
						context.close();
				}
			}
		}
		catch(final Throwable error)
		{
			error.printStackTrace();
			results.add(new Result("Harness", error.toString(), start, false));
		}

		// Save the report before shutdown. The launcher limits the total process time.
		try
		{
			writeResults(results);
			if(ready)
				MUD.globalShutdown(null, true, null);
		}
		catch(final Throwable error)
		{
			error.printStackTrace();
			results.add(new Result("Shutdown", error.toString(), start, false));

			// Include the final error if the report can still be written.
			try
			{
				writeResults(results);
			}
			catch(final Exception reportError)
			{
				reportError.printStackTrace();
			}
		}

		System.exit(exitCode(results));
	}

	/**
	 * Starts CoffeeMud and a worker that runs the tests after startup.
	 * The Python launcher starts this in a separate process with a temporary runtime folder.
	 *
	 * @param args selection kind ({@code group} or {@code test}), selection name,
	 *             port, per-test timeout in seconds, and startup timeout in seconds
	 * @throws IllegalArgumentException if the launcher supplies the wrong number of arguments
	 */
	public static void main(final String[] args)
	{
		if(args.length != 5)
			throw new IllegalArgumentException("Expected selection kind, selector, port, test timeout, startup timeout");

		// CoffeeMud uses the first character of the group name to find the main host.
		new Thread(new ThreadGroup("0-CI"), () -> runInHost(args), "CI-tests").start();
		MUD.main(new String[] {"CITestHost", "BOOT=coffeemud.ini", "PORT=" + args[2]});
	}
}
