package com.planet_ink.coffee_mud.Tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Combat.ActionOutcome;
import com.planet_ink.coffee_mud.Combat.ActionRequest;
import com.planet_ink.coffee_mud.Combat.ActionResolver;
import com.planet_ink.coffee_mud.Combat.EncounterAction;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.EndTurnAction;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Combat.ValidationResult;
import com.planet_ink.coffee_mud.Combat.ValidationResult.Code;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.PartyInviteCommandTest.TestPlayer;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;

/** Host fixtures for shared prerequisites, phase ordering, and one-time effects. */
public class EncounterActionTest extends StdTest
{
	@Override
	public String ID() { return "EncounterActionTest"; }

	@Override
	public String[] getTestGroups() { return new String[] {"all", "encounter", "exec_integration"}; }

	@Override
	public String doTest(final MOB mob, final int metaFlags, final String what, final List<String> commands)
	{
		try
		{
			phaseOrdering();
			rejectedActions();
			requestReplay();
			completionAndReentry();
			postUseFailure();
			concurrentSpending();
			return null;
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
	}

	private static void phaseOrdering()
	{
		try(Fixture f=new Fixture())
		{
			check(f.manager.activateEncounter(f.encounter),"The fixture must activate.");
			final RecordingAction action=new RecordingAction(3);
			action.outcome=ActionOutcome.completed(true,1);
			action.onExecute=() -> check(f.first.getActionsRemaining()==3,"Effects execute before resource spending.");
			action.onPost=() -> check(f.encounter.getCurrentParticipant()==f.first
					&& f.first.getActionsRemaining()==0 && f.first.getAttackCount()==1
					&& f.first.getDiagonalCount()==1 && f.second.getActionsRemaining()==0,
					"post_use must see committed usage before the next participant begins.");
			check(f.submit(action).isSuccessful(),"A valid activity must resolve.");
			check(action.phases.equals(Arrays.asList("pre_use","execute","post_use")),"Phases must run once in order.");
			check(f.encounter.getCurrentParticipant()==f.second&&f.second.getActionsRemaining()==3,
					"Exhaustion must advance after follow-up processing.");
			check(f.service.submit(f.bob.getMob(),new EndTurnAction()).isSuccessful()
					&& f.encounter.getCurrentParticipant()==f.first && f.encounter.getRound()==2,
					"End Turn must use shared follow-up to discard actions and advance.");
			final RecordingAction free=new RecordingAction(0);
			check(f.submit(free).isSuccessful()&&f.first.getActionsRemaining()==3,
					"A valid free activity must not spend actions or end a nonempty turn.");
		}
	}

	private static void rejectedActions()
	{
		try(Fixture f=new Fixture())
		{
			final RecordingAction action=new RecordingAction(1);
			check(f.submit(action).getValidation().getCode()==Code.PENDING&&action.phases.isEmpty(),
					"Common prerequisites must run before action-specific callbacks.");
			check(f.manager.activateEncounter(f.encounter),"The fixture must activate.");
			check(f.service.submit(f.bob.getMob(),action).getValidation().getCode()==Code.NOT_YOUR_TURN,
					"Another participant must not submit actions for this turn.");
			check(f.submit(new RecordingAction(-1)).getValidation().getCode()==Code.INVALID_ACTION,
					"Negative costs must be rejected.");
			check(f.submit(new RecordingAction(4)).getValidation().getCode()==Code.INSUFFICIENT_ACTIONS,
					"Overspending must be rejected before callbacks.");
			check(f.service.submit(f.alice.getMob(),null).getValidation().getCode()==Code.INVALID_ACTION,
					"Missing actions must be rejected.");
			check(new ActionResolver().resolve(new ActionRequest(f.encounter,
					new EncounterParticipant(f.alice.getMob()),action)).getValidation().getCode()==Code.NOT_PARTICIPANT,
					"Unregistered participant records must be rejected.");
			action.validation=ValidationResult.rejected(Code.REJECTED,"Tactical placement is unavailable.");
			final ActionOutcome blocked=f.submit(action);
			check(!blocked.isSuccessful()&&blocked.getValidation().getReason().equals("Tactical placement is unavailable.")
					&& action.phases.equals(Arrays.asList("pre_use"))&&action.effects==0,
					"A missing action prerequisite must return its reason without effects or post_use.");
			check(f.first.getActionsRemaining()==3&&f.first.getAttackCount()==0&&f.encounter.getRound()==1,
					"Rejected validation must preserve resources and the turn.");
			final RecordingAction rejectedExecution=new RecordingAction(1);
			rejectedExecution.outcome=ActionOutcome.rejected(ValidationResult.rejected(Code.REJECTED,"No effect applied."));
			check(!f.submit(rejectedExecution).isSuccessful()&&f.first.getActionsRemaining()==3
					&& rejectedExecution.phases.equals(Arrays.asList("pre_use","execute")),
					"An execution rejection without effects must not spend resources or run post_use.");
			check(f.manager.awaitRescue(f.encounter)&&f.submit(new RecordingAction(1)).getValidation().getCode()==Code.PAUSED,
					"Paused encounters must reject actions.");
			check(f.manager.completeEncounter(f.encounter),"The paused encounter must complete.");
			check(new ActionResolver().resolve(new ActionRequest(f.encounter,f.first,new EndTurnAction()))
					.getValidation().getCode()==Code.COMPLETED,"Completed encounters must reject direct resolution.");
		}
	}

	private static void requestReplay()
	{
		try(Fixture f=new Fixture())
		{
			check(f.manager.activateEncounter(f.encounter),"The fixture must activate.");
			final RecordingAction action=new RecordingAction(1);
			final ActionRequest request=new ActionRequest(f.encounter,f.first,action);
			final ActionResolver resolver=new ActionResolver();
			check(resolver.resolve(request).isSuccessful(),"The request must resolve once.");
			check(resolver.resolve(request).getValidation().getCode()==Code.ALREADY_PROCESSED
					&&action.effects==1&&f.first.getActionsRemaining()==2,"Reprocessing one request must not repeat effects or costs.");
		}
	}

	private static void completionAndReentry()
	{
		try(Fixture f=new Fixture())
		{
			check(f.manager.activateEncounter(f.encounter),"The fixture must activate.");
			final RecordingAction nested=new RecordingAction(1);
			final RecordingAction outer=new RecordingAction(1);
			outer.onExecute=() -> check(f.submit(nested).getValidation().getCode()==Code.RESOLUTION_IN_PROGRESS,
					"Message callbacks must not interleave another action's lifecycle.");
			check(f.submit(outer).isSuccessful()&&nested.effects==0&&f.first.getActionsRemaining()==2,
					"Only the outer action may apply effects and costs.");
			final RecordingAction throwing=new RecordingAction(1);
			throwing.onExecute=() -> { throw new IllegalStateException("fixture failure before effects"); };
			try
			{
				f.submit(throwing);
				throw new AssertionError("Unexpected callback errors must not become successful outcomes.");
			}
			catch(final IllegalStateException expected)
			{
				check(expected.getMessage().equals("fixture failure before effects"),"The original error must propagate.");
			}
			final RecordingAction finishing=new RecordingAction(1);
			finishing.onPost=() -> check(f.manager.completeEncounter(f.encounter),"Follow-up may complete the room encounter.");
			check(f.submit(finishing).isSuccessful()&&f.manager.getEncounter()==null&&f.encounter.getRound()==1
					&&f.second.getActionsRemaining()==0,"Completion must prevent next-turn processing, and errors must release the resolver guard.");
		}
	}

	private static void postUseFailure()
	{
		try(Fixture f=new Fixture())
		{
			check(f.manager.activateEncounter(f.encounter),"The fixture must activate.");
			final RecordingAction action=new RecordingAction(3);
			action.onPost=() -> { throw new IllegalStateException("fixture post_use failure"); };
			final ActionResolver resolver=new ActionResolver();
			final ActionRequest request=new ActionRequest(f.encounter,f.first,action);
			try
			{
				resolver.resolve(request);
				throw new AssertionError("Unexpected follow-up failures must propagate.");
			}
			catch(final IllegalStateException expected)
			{
				check(expected.getMessage().equals("fixture post_use failure"),"Preserve the follow-up error.");
			}
			check(action.effects==1&&f.first.getActionsRemaining()==0
					&&f.encounter.getCurrentParticipant()==f.second&&f.second.getActionsRemaining()==3,
					"Committed costs must still finalize an exhausted turn when follow-up fails.");
			check(resolver.resolve(request).getValidation().getCode()==Code.ALREADY_PROCESSED&&action.effects==1,
					"An error after effects must not permit replaying the same request.");
			check(f.service.submit(f.bob.getMob(),new EndTurnAction()).isSuccessful(),
					"Follow-up failures must release the resolution guard.");
		}
	}

	private static void concurrentSpending() throws Exception
	{
		try(Fixture f=new Fixture())
		{
			check(f.manager.activateEncounter(f.encounter),"The fixture must activate.");
			final ExecutorService workers=Executors.newFixedThreadPool(4);
			final CountDownLatch start=new CountDownLatch(1);
			final RecordingAction action=new RecordingAction(1);
			try
			{
				final List<Future<ActionOutcome>> pending=new ArrayList<Future<ActionOutcome>>();
				for(int i=0;i<5;i++)
					pending.add(workers.submit(() -> { start.await(); return f.submit(action); }));
				start.countDown();
				int successes=0;
				for(final Future<ActionOutcome> result : pending)
					if(result.get(5,TimeUnit.SECONDS).isSuccessful())
						successes++;
				check(successes==3&&action.effects==3&&f.first.getActionsRemaining()==0
						&&f.encounter.getCurrentParticipant()==f.second&&f.encounter.getRound()==1,
						"Concurrent submissions must not overspend or advance twice.");
			}
			finally { start.countDown(); workers.shutdownNow(); }
		}
	}

	private static final class RecordingAction implements EncounterAction
	{
		private final int cost;
		private final List<String> phases=new ArrayList<String>();
		private ValidationResult validation=ValidationResult.valid();
		private ActionOutcome outcome=ActionOutcome.completed();
		private Runnable onExecute=() -> { };
		private Runnable onPost=() -> { };
		private int effects;

		private RecordingAction(final int cost) { this.cost=cost; }
		@Override public int getActionCost() { return cost; }
		@Override public ValidationResult validate(final GeneralEncounter encounter, final EncounterParticipant participant)
		{
			phases.add("pre_use");
			return validation;
		}
		@Override public ActionOutcome execute(final GeneralEncounter encounter, final EncounterParticipant participant)
		{
			phases.add("execute");
			onExecute.run();
			if(outcome.isSuccessful()) effects++;
			return outcome;
		}
		@Override public void postUse(final GeneralEncounter encounter, final EncounterParticipant participant, final ActionOutcome outcome)
		{
			phases.add("post_use");
			onPost.run();
		}
	}

	private static final class Fixture implements AutoCloseable
	{
		private final Area area=CMClass.getAreaType("StdArea");
		private final Room room=CMClass.getLocale("StdRoom");
		private final TestPlayer alice;
		private final TestPlayer bob;
		private final EncounterParticipant first;
		private final EncounterParticipant second;
		private final GeneralEncounter encounter;
		private final EncounterManager manager;
		private final EncounterLibrary service=CMLib.encounters();

		private Fixture()
		{
			final String suffix=UUID.randomUUID().toString().substring(0,8);
			area.setName("EncounterActions"+suffix);
			room.setArea(area);
			alice=new TestPlayer("Alice"+suffix,room);
			bob=new TestPlayer("Bob"+suffix,room);
			first=new EncounterParticipant(alice.getMob());
			second=new EncounterParticipant(bob.getMob());
			encounter=new GeneralEncounter(Arrays.asList(first,second));
			manager=room.getEncounterManager();
			check(manager.trySetEncounter(encounter),"The fixture must own its encounter.");
		}
		private ActionOutcome submit(final EncounterAction action) { return service.submit(alice.getMob(),action); }
		@Override public void close()
		{
			alice.close();
			bob.close();
			room.destroy();
			area.destroy();
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition) throw new AssertionError(message);
	}
}
