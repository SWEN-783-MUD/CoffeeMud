package com.planet_ink.coffee_mud.core.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/** Snapshots used to run a party's exit move and report where it ended. */
public final class PartyTravel
{
	/** Holds the travel snapshots. */
	private PartyTravel()
	{
	}

	/** One character and their direct party followers at the start of a move. */
	public static final class Step
	{
		/** The character whose step this is. */
		public final MOB player;
		/** The character they were following, or null for independent travel. */
		public final MOB parent;
		/** The room the batch is leaving. */
		public final Room source;
		/** The room the character actually occupied when the batch started. */
		public final Room startingRoom;
		/** The character's session at the start of the batch. */
		public final Session session;
		/** The roster that supplied this branch, or zero for a solo traveler. */
		public final long partyId;
		/** Changes when the player moves or their session ends. */
		public final long positionRevision;
		/** Changes when the player replaces or clears an outgoing follow link. */
		public final long followRevision;
		/** The parent's starting position revision, or zero for the root. */
		public final long parentPositionRevision;
		/** Direct followers in native follower order. */
		public final List<Step> children;

		/**
		 * Captures a step without making a second live follow graph.
		 *
		 * @param player the character taking the step
		 * @param parent the original follow target, or null for the initiator
		 * @param source the batch's starting room
		 * @param startingRoom the character's actual room at capture
		 * @param session the current session
		 * @param partyId the captured party ID
		 * @param positionRevision the captured position revision
		 * @param followRevision the captured follow revision
		 * @param parentPositionRevision the parent's position revision before their step
		 * @param children the captured direct follower steps
		 */
		public Step(final MOB player, final MOB parent, final Room source, final Room startingRoom,
				final Session session, final long partyId, final long positionRevision,
				final long followRevision, final long parentPositionRevision, final List<Step> children)
		{
			this.player = player;
			this.parent = parent;
			this.source = source;
			this.startingRoom = startingRoom;
			this.session = session;
			this.partyId = partyId;
			this.positionRevision = positionRevision;
			this.followRevision = followRevision;
			this.parentPositionRevision = parentPositionRevision;
			this.children = Collections.unmodifiableList(new ArrayList<Step>(children));
		}
	}

	/** A player who actually crossed the exit during the batch. */
	public static final class Arrival
	{
		/** The live character at the time of arrival. */
		public final MOB player;
		/** The canonical name at arrival, captured for reporting. */
		public final String characterKey;
		/** The direction back through the actual destination connection, or -1 if unknown. */
		public final int entrySide;
		/** The player's activity and approach at the time they entered the room. */
		public final ExplorationContext exploration;
		/** The session that actually traveled. */
		public final Session session;
		/** The player's position revision after placement. */
		public final long positionRevision;

		/**
		 * Records a committed room entry.
		 *
		 * @param player the character who arrived
		 * @param characterKey the character's key at arrival
		 * @param session the session that entered
		 * @param positionRevision the player's position revision at placement
		 * @param exploration the fixed exploration context from placement
		 */
		public Arrival(final MOB player, final String characterKey, final Session session,
				final long positionRevision, final ExplorationContext exploration)
		{
			this.player = player;
			this.characterKey = characterKey;
			this.session = session;
			this.positionRevision = positionRevision;
			this.exploration = exploration;
			this.entrySide = exploration.entrySide;
		}
	}

	/** Why a captured follower did not arrive through their branch. */
	public enum FailureReason
	{
		/** The follower was unable to take the step through the normal movement rules. */
		BLOCKED,
		/** The captured position, session, or party link was no longer current. */
		NO_LONGER_ELIGIBLE,
		/** The parent stayed behind, so this player did not try to move. */
		PARENT_STAYED
	}

	/** One follower who did not take their planned step. */
	public static final class Failure
	{
		/** The follower who did not arrive. */
		public final MOB player;
		/** The canonical name when the outcome was recorded. */
		public final String characterKey;
		/** Why the follower did not move through the batch. */
		public final FailureReason reason;

		/**
		 * Records a failed or skipped step.
		 *
		 * @param player the follower who did not arrive
		 * @param characterKey the follower's key
		 * @param reason why the step did not finish
		 */
		public Failure(final MOB player, final String characterKey, final FailureReason reason)
		{
			this.player = player;
			this.characterKey = characterKey;
			this.reason = reason;
		}
	}

	/** The result published once the initiator and all captured branches have resolved. */
	public static final class Completed
	{
		/** Identifies this traversal for a consumer that needs to deduplicate delivery. */
		public final UUID id;
		/** The room where the independent move began. */
		public final Room source;
		/** The actual room the initiator entered. */
		public final Room destination;
		/** The direction the initiator entered at the command line. */
		public final int direction;
		/** Successful steps in arrival order, beginning with the initiator. */
		public final List<Arrival> arrivals;
		/** Failed and skipped steps in the order they were resolved. */
		public final List<Failure> failures;

		/**
		 * Takes a fixed copy of the completed traversal.
		 *
		 * @param source the original room
		 * @param destination the room that was entered
		 * @param direction the requested direction
		 * @param arrivals the successful steps
		 * @param failures the steps that stayed behind or were skipped
		 */
		public Completed(final Room source, final Room destination, final int direction,
				final List<Arrival> arrivals, final List<Failure> failures)
		{
			this.id = UUID.randomUUID();
			this.source = source;
			this.destination = destination;
			this.direction = direction;
			this.arrivals = Collections.unmodifiableList(new ArrayList<Arrival>(arrivals));
			this.failures = Collections.unmodifiableList(new ArrayList<Failure>(failures));
		}
	}
}
