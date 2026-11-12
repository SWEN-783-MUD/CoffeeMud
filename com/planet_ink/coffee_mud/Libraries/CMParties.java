package com.planet_ink.coffee_mud.Libraries;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMProps;
import com.planet_ink.coffee_mud.core.CMStrings;
import com.planet_ink.coffee_mud.core.Log;
import com.planet_ink.coffee_mud.core.collections.Pair;
import com.planet_ink.coffee_mud.core.party.ExplorationContext;
import com.planet_ink.coffee_mud.core.party.ExplorationContext.Activity;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry.Type;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.IdLimitException;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingView;
import com.planet_ink.coffee_mud.core.party.PartyStore.TransferResult;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Arrival;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Step;

/** Handles live players and feedback around the party store's membership rules. */
public class CMParties extends StdLibrary implements PartyLibrary
{
	/** Keeps character name reads and party operations together with rename and deletion. */
	private final Object characterLock = new Object();

	/** Deleted live objects with commands that may still be finishing. Guarded by characterLock. */
	private final Set<MOB> retiredCharacters = Collections.newSetFromMap(new WeakHashMap<MOB, Boolean>());

	/** Characters whose session is ending. Guarded by characterLock. */
	private final Set<MOB> departingFollowers = Collections.newSetFromMap(new WeakHashMap<MOB, Boolean>());

	/** Live travel revisions and exploration context. Guarded by characterLock. */
	private final Map<MOB, CharacterTravelState> travelStates = new WeakHashMap<MOB, CharacterTravelState>();

	/** Holds one live character's travel and exploration state under characterLock. */
	private static final class CharacterTravelState
	{
		/** Increments on a room change or session departure. */
		private long position;
		/** Increments when the outgoing target changes. */
		private long follow;

		private Activity activity = Activity.NORMAL;

		/** The direction back through the entry into the current room, if known. */
		private int entrySide = ExplorationContext.UNKNOWN_ENTRY;

		/** Increments on an activity or approach change. */
		private long explorationRevision;

		private void selectActivity(final Activity selection)
		{
			if(activity != selection)
			{
				activity = selection;
				explorationRevision++;
			}
		}

		private void resetSession()
		{
			if((activity != Activity.NORMAL) || (entrySide != ExplorationContext.UNKNOWN_ENTRY))
			{
				activity = Activity.NORMAL;
				entrySide = ExplorationContext.UNKNOWN_ENTRY;
				explorationRevision++;
			}
		}

		private ExplorationContext snapshot(final Room room)
		{
			return new ExplorationContext(activity,room,entrySide,explorationRevision);
		}
	}

	/** The store for this server run. Access is guarded by characterLock. */
	private PartyStore store = new InMemoryPartyStore();

	/**
	 * Identifies this library in CoffeeMud's registry.
	 *
	 * @return the library ID
	 */
	@Override
	public String ID()
	{
		return "CMParties";
	}

	/** {@inheritDoc} */
	@Override
	public boolean isPlayerCharacter(final MOB character)
	{
		return character != null && character.playerStats() != null;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCombatRestricted(final MOB player)
	{
		if(player.isInCombat())
			return true;

		final Room room = player.location();
		final EncounterManager manager = room == null ? null : room.getEncounterManager();
		return (manager != null) && manager.restricts(player);
	}

	/** {@inheritDoc} */
	@Override
	public boolean commitEncounterChange(final BooleanSupplier change)
	{
		synchronized(characterLock)
		{
			return change.getAsBoolean();
		}
	}

	/** {@inheritDoc} */
	@Override
	public List<MOB> onlinePartyMembers(final MOB player)
	{
		synchronized(characterLock)
		{
			final PartySnapshot party = findParty(player);
			if(party == null)
				return Collections.emptyList();

			final String ownKey = characterKey(player);
			final List<MOB> members = new ArrayList<MOB>();
			for(final String key : party.getMembers())
			{
				if(key.equals(ownKey))
					continue;
				final MOB member = findOnlinePlayer(key);
				if((member != null) && !departingFollowers.contains(member))
					members.add(member);
			}
			return Collections.unmodifiableList(members);
		}
	}

	/** {@inheritDoc} */
	@Override
	public ExplorationContext explorationContext(final MOB player)
	{
		synchronized(characterLock)
		{
			return canUseCharacter(player) ? travelState(player).snapshot(player.location()) : null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public ActivitySelection selectExplorationActivity(final MOB player, final Activity activity)
	{
		synchronized(characterLock)
		{
			if(!canUseCharacter(player) || departingFollowers.contains(player) || (activity == null))
				return ActivitySelection.UNAVAILABLE;
			if(isCombatRestricted(player))
				return ActivitySelection.IN_COMBAT;

			travelState(player).selectActivity(activity);
			return ActivitySelection.SELECTED;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void resetExplorationActivity(final MOB player)
	{
		synchronized(characterLock)
		{
			if(canUseCharacter(player))
				travelState(player).selectActivity(Activity.NORMAL);
		}
	}

	private String characterKey(final MOB character)
	{
		return CMStrings.capitalizeAndLower(character.Name());
	}

	/** {@inheritDoc} */
	@Override
	public boolean renameCharacter(final MOB character, final String oldName, final String newName,
			final BooleanSupplier save)
	{
		synchronized(characterLock)
		{
			if(!canUseCharacter(character) || !oldName.equals(character.Name()) || !save.getAsBoolean())
				return false;

			// Capture the old key before publishing the new name to other game systems.
			store.renameCharacter(characterKey(character), CMStrings.capitalizeAndLower(newName));
			character.setName(newName);
			return true;
		}
	}

	/** {@inheritDoc} */
	@Override
	public DeleteResult deleteCharacter(final MOB character, final Predicate<String> remove)
	{
		synchronized(characterLock)
		{
			if(!canUseCharacter(character))
				return DeleteResult.UNAVAILABLE;
			if(!remove.test(character.Name()))
				return DeleteResult.SAVE_FAILED;

			// Reject delayed commands that still hold the deleted object.
			retiredCharacters.add(character);
			clearPartyLinks(character);
			travelStates.remove(character);
			store.deleteCharacter(characterKey(character));
			return DeleteResult.DELETED;
		}
	}

	// Send feedback after this returns so session and room code run outside characterLock.
	private <T> T withCharacter(final MOB actor, final Function<String, T> operation)
	{
		synchronized(characterLock)
		{
			return canUseCharacter(actor) ? operation.apply(characterKey(actor)) : null;
		}
	}

	// Call with characterLock held so a deleted character cannot act under a reused name.
	private boolean canUseCharacter(final MOB character)
	{
		return isPlayerCharacter(character) && !character.amDestroyed() && !retiredCharacters.contains(character);
	}

	/** {@inheritDoc} */
	@Override
	public FollowResult checkPlayerFollow(final MOB follower, final MOB target)
	{
		synchronized(characterLock)
		{
			return validateFollow(follower, target);
		}
	}

	/** {@inheritDoc} */
	@Override
	public FollowResult changePlayerFollow(final MOB follower, final MOB target, final BooleanSupplier write)
	{
		synchronized(characterLock)
		{
			// Detaching and native links involving NPCs keep their existing rules.
			if(isPlayerCharacter(target))
			{
				final FollowResult result = validateFollow(follower, target);
				if(result != FollowResult.ALLOWED)
					return result;
			}
			final MOB previous = follower.amFollowing();
			if(!write.getAsBoolean())
				return FollowResult.LINK_CHANGED;
			if(previous != target)
				travelState(follower).follow++;
			return FollowResult.ALLOWED;
		}
	}

	/** {@inheritDoc} */
	@Override
	public Room changePlayerLocation(final MOB player, final Supplier<Room> write)
	{
		synchronized(characterLock)
		{
			final CharacterTravelState state = travelState(player);
			final Room source = player.location();
			final Room previous = write.get();
			final Room current = player.location();
			if(previous != current)
				state.position++;

			// Location writes without a known exit cannot reuse the old room's approach.
			if(source != current)
			{
				state.entrySide = ExplorationContext.UNKNOWN_ENTRY;
				state.explorationRevision++;
			}
			return previous;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void placePlayerFromExit(final MOB player, final Room destination, final int entrySide)
	{
		synchronized(characterLock)
		{
			placeFromExit(player,destination,entrySide);
		}
	}

	// Call with characterLock held. Same-room moves keep the current approach.
	private void placeFromExit(final MOB player, final Room destination, final int entrySide)
	{
		final Room source = player.location();
		player.setLocation(destination);

		// Replace the default unknown entry from setLocation with the known approach for this same move.
		if((source != destination) && (player.location() == destination))
			travelState(player).entrySide = entrySide;
	}

	// Call with characterLock held.
	private CharacterTravelState travelState(final MOB player)
	{
		return travelStates.computeIfAbsent(player, key -> new CharacterTravelState());
	}

	/** {@inheritDoc} */
	@Override
	public Step captureTravel(final MOB player)
	{
		synchronized(characterLock)
		{
			if(!canStartFollowing(player))
				return null;
			final Room source = player.location();
			if((source == null) || !source.isInhabitant(player))
				return null;
			final PartySnapshot party = store.findByMember(characterKey(player));
			final Set<MOB> seen = new HashSet<MOB>();
			seen.add(player);
			return captureStep(player, null, source, party, seen);
		}
	}

	// Capture the native follow links parent-first while holding characterLock.
	private Step captureStep(final MOB player, final MOB parent, final Room source, final PartySnapshot party,
			final Set<MOB> seen)
	{
		final List<Step> children = new ArrayList<Step>();
		if(party != null)
		{
			for(final Enumeration<Pair<MOB, Short>> followers = player.followers(); followers.hasMoreElements();)
			{
				final MOB follower = followers.nextElement().first;
				if(canStartFollowing(follower) && (follower.amFollowing() == player)
				&& party.getMembers().contains(characterKey(follower)) && seen.add(follower))
					children.add(captureStep(follower, player, source, party, seen));
			}
		}
		final CharacterTravelState state = travelState(player);
		return new Step(player, parent, source, player.location(), player.session(), party == null ? 0 : party.getId(),
				state.position, state.follow, parent == null ? 0 : travelState(parent).position, children);
	}

	/** {@inheritDoc} */
	@Override
	public boolean canTravel(final Step step, final Room destination)
	{
		synchronized(characterLock)
		{
			return isCurrentStep(step, destination) && step.source.isInhabitant(step.player);
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean departTravel(final Step step, final Room destination)
	{
		synchronized(characterLock)
		{
			if(!isCurrentStep(step, destination) || !step.source.isInhabitant(step.player))
				return false;
			step.source.delInhabitant(step.player);
			return true;
		}
	}

	/** {@inheritDoc} */
	@Override
	public Arrival arriveTravel(final Step step, final Room destination, final int entrySide)
	{
		synchronized(characterLock)
		{
			if(!isCurrentStep(step, destination) || step.source.isInhabitant(step.player))
				return null;

			// Publish the room and any independent unlink together, before enter callbacks run.
			if((step.parent == null) && (destination != step.source) && isPlayerCharacter(step.player.amFollowing()))
				step.player.setFollowing(null);
			placeFromExit(step.player,destination,entrySide);
			destination.addInhabitant(step.player);
			final CharacterTravelState state = travelState(step.player);
			return new Arrival(step.player,characterKey(step.player),step.session,state.position,state.snapshot(destination));
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCurrentArrival(final Arrival arrival)
	{
		synchronized(characterLock)
		{
			final MOB player = arrival.player;
			final Room room = arrival.exploration.room;
			return canStartFollowing(player) && (player.session() == arrival.session)
					&& (player.location() == room) && room.isInhabitant(player)
					&& (travelState(player).position == arrival.positionRevision);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void restoreTravelSource(final Step step)
	{
		synchronized(characterLock)
		{
			if(isOriginalPosition(step) && (step.player.location() == step.source)
			&& !step.source.isInhabitant(step.player))
				step.source.addInhabitant(step.player);
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean detachBlockedFollower(final Step step)
	{
		synchronized(characterLock)
		{
			if((step.parent == null) || !isOriginalPosition(step) || !isOriginalFollow(step))
				return false;
			step.player.setFollowing(null);
			return true;
		}
	}

	// Call with characterLock held before writing a new position.
	private boolean isCurrentStep(final Step step, final Room destination)
	{
		if((destination == null) || (step.startingRoom != step.source) || !isOriginalPosition(step))
			return false;
		if(step.parent == null)
			return true;
		return isOriginalFollow(step) && canStartFollowing(step.parent)
				&& (step.parent.location() == destination) && destination.isInhabitant(step.parent)
				&& (travelState(step.parent).position == step.parentPositionRevision + 1);
	}

	// Call with characterLock held.
	private boolean isOriginalPosition(final Step step)
	{
		return canStartFollowing(step.player) && (step.player.session() == step.session)
				&& (step.player.location() == step.startingRoom)
				&& (travelState(step.player).position == step.positionRevision);
	}

	// Call with characterLock held.
	private boolean isOriginalFollow(final Step step)
	{
		if((step.player.amFollowing() != step.parent) || (travelState(step.player).follow != step.followRevision))
			return false;
		final PartySnapshot party = store.findByMember(characterKey(step.player));
		return (party != null) && (party.getId() == step.partyId)
				&& party.getMembers().contains(characterKey(step.parent));
	}

	// Call with characterLock held.
	private FollowResult validateFollow(final MOB follower, final MOB target)
	{
		if(follower == target)
			return FollowResult.SELF;
		if(!canStartFollowing(follower) || !canStartFollowing(target))
			return FollowResult.OFFLINE;

		final Room room = follower.location();
		if((room == null) || (room != target.location())
		|| !room.isInhabitant(follower) || !room.isInhabitant(target))
			return FollowResult.NOT_HERE;
		final PartySnapshot party = store.findByMember(characterKey(follower));
		if((party == null) || !party.getMembers().contains(characterKey(target)))
			return FollowResult.NOT_PARTY;
		if(isCombatRestricted(follower))
			return FollowResult.IN_COMBAT;
		if(follower.amFollowing() == target)
			return FollowResult.ALREADY_FOLLOWING;

		// Walk the native links so a chain through an NPC also cannot form a loop.
		final Set<MOB> seen = new HashSet<MOB>();
		seen.add(follower);
		MOB leader = target;
		for(MOB next = target; next != null; next = next.amFollowing())
		{
			if(!seen.add(next))
				return FollowResult.CYCLE;
			leader = next;
		}
		if(target.isAttributeSet(MOB.Attrib.NOFOLLOW) || leader.isAttributeSet(MOB.Attrib.NOFOLLOW))
			return FollowResult.REFUSED;

		// Reparenting within the target's branch does not add any followers to it.
		seen.clear();
		for(MOB next = follower.amFollowing(); (next != null) && seen.add(next); next = next.amFollowing())
		{
			if(next == target)
				return FollowResult.ALLOWED;
		}
		if(target.totalFollowers() + follower.totalFollowers() >= target.maxFollowers())
			return FollowResult.FULL;
		return FollowResult.ALLOWED;
	}

	// Call with characterLock held.
	private boolean canStartFollowing(final MOB player)
	{
		if(!canUseCharacter(player) || departingFollowers.contains(player))
			return false;
		final Session session = player.session();
		return (session != null) && (session.mob() == player) && !session.isStopped()
				&& (session.getStatus() == Session.SessionStatus.MAINLOOP);
	}

	/** {@inheritDoc} */
	@Override
	public void playerSessionEnding(final MOB player, final Session session)
	{
		synchronized(characterLock)
		{
			// A delayed close from an old session cannot clear the new session's links.
			if((session != null) && isPlayerCharacter(player) && (player.session() == session))
			{
				final CharacterTravelState state = travelState(player);
				if(departingFollowers.add(player))
					state.position++;
				state.resetSession();
				clearPartyLinks(player);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void playerSessionStarted(final MOB player, final Session session)
	{
		synchronized(characterLock)
		{
			if(isPlayerCharacter(player) && (player.session() == session))
			{
				departingFollowers.remove(player);
				travelState(player).resetSession();
			}
		}
	}

	// Call with characterLock held before removing the roster entry.
	private void clearPartyLinks(final MOB player)
	{
		final PartySnapshot party = store.findByMember(characterKey(player));
		if(party == null)
			return;

		final MOB leader = player.amFollowing();
		if(isPlayerCharacter(leader) && party.getMembers().contains(characterKey(leader)))
			player.setFollowing(null);

		// The native follower enumeration is a snapshot. Descendants keep their own links.
		for(final Enumeration<Pair<MOB, Short>> followers = player.followers(); followers.hasMoreElements();)
		{
			final MOB follower = followers.nextElement().first;
			if(isPlayerCharacter(follower) && (follower.amFollowing() == player)
			&& party.getMembers().contains(characterKey(follower)))
				follower.setFollowing(null);
		}
	}

	/** {@inheritDoc} */
	@Override
	public CreateResult createParty(final MOB character)
	{
		synchronized(characterLock)
		{
			if(!canUseCharacter(character))
				return CreateResult.NOT_PLAYER;
			if(isCombatRestricted(character))
				return CreateResult.IN_COMBAT;
			try
			{
				return store.create(characterKey(character)) == null ? CreateResult.ALREADY_MEMBER : CreateResult.CREATED;
			}
			catch(final IdLimitException error)
			{
				Log.errOut(ID(), error);
				return CreateResult.ID_LIMIT;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public PartySnapshot findParty(final MOB character)
	{
		return withCharacter(character, key -> store.findByMember(key));
	}

	/** {@inheritDoc} */
	@Override
	public boolean leaveParty(final MOB character)
	{
		return Boolean.TRUE.equals(withCharacter(character, key -> {
			clearPartyLinks(character);
			return Boolean.valueOf(store.leave(key));
		}));
	}

	/** {@inheritDoc} */
	@Override
	public void transferLeadership(final MOB leader, final String memberName)
	{
		// Match a roster key so a disconnected member can still take over.
		final String memberKey = CMStrings.capitalizeAndLower(memberName);
		final TransferResult result;
		final MOB newLeader;
		synchronized(characterLock)
		{
			if(!canManageParty(leader))
				return;
			result = store.transferLeadership(characterKey(leader), memberKey);
			newLeader = result == TransferResult.TRANSFERRED ? findOnlinePlayer(memberKey) : null;
		}
		switch(result)
		{
		case TRANSFERRED:
			leader.tell(L("You passed party leadership to @x1.", memberKey));
			if(newLeader != null)
				newLeader.tell(L("@x1 passed party leadership to you. Use PARTY PENDING to see invitations and requests.",
						leader.Name()));
			break;
		case NOT_LEADER:
			leader.tell(L("Only the current party leader can pass leadership."));
			break;
		case NOT_MEMBER:
			leader.tell(L("@x1 is not in your party. Use PARTY to see the member names.", memberName));
			break;
		case ALREADY_LEADER:
			leader.tell(L("You already lead the party."));
			break;
		}
	}

	/**
	 * {@inheritDoc}
	 * A repeat invite reports the existing ID to the sender. The recipient gets
	 * a message when the store creates an invite.
	 */
	@Override
	public void sendInvitation(final MOB sender, final String recipientName)
	{
		final MOB recipient;
		final PendingResult result;
		synchronized(characterLock)
		{
			if(!canManageParty(sender))
				return;

			// Check the sender before looking up another player.
			final String senderKey = characterKey(sender);
			final PartySnapshot party = store.findByMember(senderKey);
			if((party == null) || !senderKey.equals(party.getLeader()))
			{
				recipient = null;
				result = new PendingResult(PendingStatus.NOT_LEADER, null);
			}
			else
			{
				recipient = findOnlinePlayer(recipientName);
				result = recipient == null ? null : openPending(() -> store.invite(senderKey, characterKey(recipient)));
			}
		}

		if(result == null)
		{
			sender.tell(L("No online player has the character name @x1.", recipientName));
			return;
		}

		switch(result.getStatus())
		{
		case CREATED:
		{
			// Notify the recipient after the store commits the invite.
			final String id = Long.toString(result.getEntry().getId());
			sender.tell(L("You invited @x1 to your party. Invitation ID is @x2.", recipient.Name(), id));
			recipient.tell(L("^HParty invitation^N\n\r@x1 invited you to their party.\n\r"
					+ "  PARTY ACCEPT @x2\n\r  PARTY DECLINE @x2\n\rUse PARTY PENDING for details and name shortcuts.",
					sender.Name(), id));
			break;
		}
		case ALREADY_PENDING:
		{
			final PartyPendingEntry entry = result.getEntry();
			final String id = Long.toString(entry.getId());
			if(entry.getType() == Type.REQUEST)
				sender.tell(L("@x1 already asked to join your party. Use PARTY APPROVE @x2.", recipient.Name(), id));
			else
				sender.tell(L("Your invitation to @x1 is already pending. Invitation ID is @x2.", recipient.Name(), id));
			break;
		}
		case NOT_LEADER:
			sender.tell(L("Only a party leader can send invitations."));
			break;
		case ALREADY_MEMBER:
			sender.tell(L("@x1 already belongs to a party.", recipient.Name()));
			break;
		case ID_LIMIT:
			sender.tell(L("An invitation cannot be created right now. Please contact an administrator."));
			break;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void requestJoin(final MOB candidate, final String leaderName)
	{
		final MOB leader;
		final PendingResult result;
		synchronized(characterLock)
		{
			if(!canManageParty(candidate))
				return;

			// Keep the online lookup and entry creation on the same names.
			leader = findOnlinePlayer(leaderName);
			result = leader == null ? null : openPending(() -> store.requestJoin(characterKey(candidate), characterKey(leader)));
		}
		if(result == null)
		{
			candidate.tell(L("No online player has the character name @x1.", leaderName));
			return;
		}

		switch(result.getStatus())
		{
		case CREATED:
		{
			final PartyPendingEntry entry = result.getEntry();
			final String id = Long.toString(entry.getId());
			candidate.tell(L("You asked to join @x1's party. Request ID is @x2.", leader.Name(), id));

			// If leadership changed while the request was opening, the new leader can see it in PARTY PENDING.
			final Boolean stillLeads = withCharacter(leader, key -> {
				final PartySnapshot current = store.findByMember(key);
				return Boolean.valueOf((current != null) && (current.getId() == entry.getPartyId())
						&& key.equals(current.getLeader()));
			});
			if(Boolean.TRUE.equals(stillLeads))
				leader.tell(L("^HParty join request^N\n\r@x1 asked to join your party.\n\r"
						+ "  PARTY APPROVE @x1\n\r  PARTY APPROVE @x2\n\r  PARTY DECLINE @x2",
						candidate.Name(), id));
			break;
		}
		case ALREADY_PENDING:
		{
			// The first entry decides which side still needs to act.
			final PartyPendingEntry entry = result.getEntry();
			final String id = Long.toString(entry.getId());
			if(entry.getType() == Type.INVITATION)
				candidate.tell(L("You already have an invitation to that party. Use PARTY ACCEPT @x1.", id));
			else
				candidate.tell(L("Your request to join @x1's party is already pending. Request ID is @x2.",
						leader.Name(), id));
			break;
		}
		case NOT_LEADER:
			candidate.tell(L("@x1 does not lead a party. Use PARTY REQUEST <leader>.", leader.Name()));
			break;
		case ALREADY_MEMBER:
			candidate.tell(L("You already belong to a party. Use PARTY LEAVE before joining another."));
			break;
		case ID_LIMIT:
			candidate.tell(L("A join request cannot be created right now. Please contact an administrator."));
			break;
		}
	}

	private PendingResult openPending(final Supplier<PendingResult> operation)
	{
		try
		{
			return operation.get();
		}
		catch(final IdLimitException error)
		{
			Log.errOut(ID(), error);
			return new PendingResult(PendingStatus.ID_LIMIT, null);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void showPendingEntries(final MOB character)
	{
		final Instant now = Instant.now();
		final String key;
		final List<PendingView> pending;
		synchronized(characterLock)
		{
			if(!canUseCharacter(character))
				return;
			key = characterKey(character);
			pending = store.pendingEntries(key);
		}
		if(pending.isEmpty())
		{
			character.tell(L("You have no pending party invitations or requests."));
			return;
		}

		final StringBuilder output = new StringBuilder(L("^HParty invitations and requests^N\n\r"));
		for(final PendingView view : pending)
		{
			final PartyPendingEntry entry = view.getEntry();
			final String id = Long.toString(entry.getId());
			final boolean candidate = key.equals(entry.getCandidateKey());
			output.append("\n\r");
			if(entry.getType() == Type.INVITATION)
			{
				output.append(candidate ? L("^HInvitation #@x1 from @x2^N\n\r", id, entry.getSenderKey())
						: L("^HInvitation #@x1 to @x2^N\n\r", id, entry.getCandidateKey()));
				if(candidate && !entry.getSenderKey().equals(view.getLeaderKey()))
					output.append(L("Current party leader: @x1\n\r", view.getLeaderKey()));
			}
			else
			{
				output.append(candidate ? L("^HRequest #@x1^N\n\rYou asked to join @x2's party.\n\r", id, view.getLeaderKey())
						: L("^HRequest #@x1 from @x2^N\n\r", id, entry.getCandidateKey()));
			}

			// Round up so a live entry with less than a second left still shows time.
			final Duration remaining = Duration.between(now, entry.getExpiresAt());
			final long roundedSeconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
			final String seconds = Long.toString(Math.max(0, roundedSeconds));
			output.append(L("Expires in @x1 seconds.\n\r", seconds));

			if(entry.getType() == Type.INVITATION)
			{
				if(candidate)
				{
					if(hasUniqueInvitationSender(pending, key, entry.getSenderKey()))
						appendPendingCommand(output, "ACCEPT", entry.getSenderKey());
					appendPendingCommand(output, "ACCEPT", id);
					appendPendingCommand(output, "DECLINE", id);
				}
				else
					appendPendingCommand(output, "CANCEL", id);
			}
			else
			{
				if(candidate)
					appendPendingCommand(output, "CANCEL", id);
				else
				{
					appendPendingCommand(output, "APPROVE", entry.getCandidateKey());
					appendPendingCommand(output, "APPROVE", id);
					appendPendingCommand(output, "DECLINE", id);
				}
			}
		}

		if(isCombatRestricted(character))
			output.append(L("\n\rYou can respond after combat if the invitation or request is still open."));
		character.tell(output.toString());
	}

	private void appendPendingCommand(final StringBuilder output, final String action, final String selector)
	{
		output.append(L("  PARTY @x1 @x2\n\r", action, selector));
	}

	private boolean hasUniqueInvitationSender(final List<PendingView> pending, final String recipient, final String sender)
	{
		int matches = 0;
		for(final PendingView view : pending)
		{
			final PartyPendingEntry entry = view.getEntry();
			if((entry.getType() == Type.INVITATION) && recipient.equals(entry.getCandidateKey())
			&& sender.equalsIgnoreCase(entry.getSenderKey()))
				matches++;
		}
		return matches == 1;
	}

	/** {@inheritDoc} */
	@Override
	public void acceptInvitation(final MOB character, final long invitationId)
	{
		joinPending(character, Type.INVITATION, () -> store.acceptInvitation(characterKey(character), invitationId));
	}

	/** {@inheritDoc} */
	@Override
	public void acceptInvitation(final MOB character, final String senderName)
	{
		joinPending(character, Type.INVITATION, () -> store.acceptInvitation(characterKey(character), senderName));
	}

	/** {@inheritDoc} */
	@Override
	public void approveRequest(final MOB leader, final long requestId)
	{
		joinPending(leader, Type.REQUEST, () -> store.approveRequest(characterKey(leader), requestId));
	}

	/** {@inheritDoc} */
	@Override
	public void approveRequest(final MOB leader, final String candidateName)
	{
		joinPending(leader, Type.REQUEST, () -> store.approveRequest(characterKey(leader), candidateName));
	}

	private void joinPending(final MOB actor, final Type type, final Supplier<JoinResult> operation)
	{
		final JoinResult result;
		final MOB candidate;
		synchronized(characterLock)
		{
			if(!canManageParty(actor))
				return;
			result = operation.get();

			// Capture the recipient before a rename or deletion can reuse the key.
			final String candidateKey = result.getJoinedCandidateKey();
			candidate = (type != Type.REQUEST) || (candidateKey == null) ? null : findOnlinePlayer(candidateKey);
		}
		reportJoinResult(actor, result, type, candidate);
	}

	/** {@inheritDoc} */
	@Override
	public void declineEntry(final MOB actor, final long entryId)
	{
		closePendingEntry(actor, entryId, true);
	}

	/** {@inheritDoc} */
	@Override
	public void cancelEntry(final MOB actor, final long entryId)
	{
		closePendingEntry(actor, entryId, false);
	}

	private void closePendingEntry(final MOB actor, final long entryId, final boolean declined)
	{
		final PendingView closed;
		final MOB other;
		synchronized(characterLock)
		{
			if(!canManageParty(actor))
				return;
			final String actorKey = characterKey(actor);
			closed = declined ? store.decline(actorKey, entryId) : store.cancel(actorKey, entryId);
			if(closed == null)
				other = null;
			else
			{
				final String candidateKey = closed.getEntry().getCandidateKey();
				other = findOnlinePlayer(actorKey.equals(candidateKey) ? closed.getLeaderKey() : candidateKey);
			}
		}
		reportClosedEntry(actor, closed, other, declined);
	}

	private void reportClosedEntry(final MOB actor, final PendingView closed, final MOB other, final boolean declined)
	{
		if(closed == null)
		{
			actor.tell(L("That invitation or request is unavailable. Use PARTY PENDING to see what you can do."));
			return;
		}

		final PartyPendingEntry entry = closed.getEntry();
		final String id = Long.toString(entry.getId());
		final String type = entry.getType() == Type.INVITATION ? L("party invitation") : L("join request");
		actor.tell(declined ? L("You declined the @x1 (ID @x2).", type, id)
				: L("You cancelled the @x1 (ID @x2).", type, id));

		if(other != null)
			other.tell(declined ? L("@x1 declined your @x2 (ID @x3).", actor.Name(), type, id)
					: L("@x1 cancelled the @x2 (ID @x3).", actor.Name(), type, id));
	}

	private void reportJoinResult(final MOB actor, final JoinResult result, final Type type, final MOB candidate)
	{
		final boolean approval = type == Type.REQUEST;
		switch(result.getStatus())
		{
		case JOINED:
			if(approval)
			{
				final String candidateKey = result.getJoinedCandidateKey();
				actor.tell(L("@x1 joined your party. Use PARTY to see the members.", candidateKey));

				if(candidate != null)
					candidate.tell(L("@x1 approved your request. You joined the party. Use PARTY to see the members.",
							actor.Name()));
			}
			else
				actor.tell(L("You joined the party. Use PARTY to see the members."));
			break;
		case ALREADY_MEMBER:
			actor.tell(approval ? L("That player already belongs to a party.")
					: L("You already belong to a party. Use PARTY LEAVE before joining another."));
			break;
		case ENTRY_UNAVAILABLE:
			actor.tell(approval ? L("That request is unavailable. Use PARTY PENDING to see requests you can approve.")
					: L("That invitation is unavailable. Use PARTY PENDING to see your invitations."));
			break;
		case EXPIRED:
			actor.tell(approval ? L("That join request has expired.") : L("That party invitation has expired."));
			break;
		case PARTY_FULL:
			actor.tell(L("That party is full. You can try again if a seat opens before the deadline."));
			break;
		case AMBIGUOUS:
			actor.tell(L("More than one invitation or request matches that name. Use an ID to choose:"));
			for(final PendingView choice : result.getMatchingEntries())
				actor.tell(L("  Party leader: @x1. Use PARTY @x2 @x3.", choice.getLeaderKey(),
						approval ? "APPROVE" : "ACCEPT", Long.toString(choice.getEntry().getId())));
			break;
		}
	}

	// Call with characterLock held so encounter opening and the command cannot cross.
	private boolean canManageParty(final MOB character)
	{
		if(!isPlayerCharacter(character))
		{
			if(character != null)
				character.tell(L("Only player characters can use parties."));
			return false;
		}
		if(!canUseCharacter(character))
			return false;

		if(isCombatRestricted(character))
		{
			character.tell(L("Wait until combat ends before using that party command."));
			return false;
		}
		return true;
	}

	// Call with characterLock held so the returned player still matches the name.
	private MOB findOnlinePlayer(final String name)
	{
		for(final Session session : CMLib.sessions().localOnlineIterableAllHosts())
		{
			final MOB player = session.mob();
			if(canUseCharacter(player) && (player.session() == session) && player.Name().equalsIgnoreCase(name))
				return player;
		}
		return null;
	}

	/**
	 * Starts the server's party store with its configured limits.
	 * The server reads these settings at startup.
	 *
	 * @return whether the library started successfully
	 */
	@Override
	public boolean activate()
	{
		final CMProps properties = CMProps.instance();
		final int configuredSeconds = properties.getInt("PARTYINVITESECONDS");
		final int configuredLimit = properties.getInt("PARTYMAXMEMBERS");

		// Fall back to the standard limits when a setting is absent or invalid.
		final Duration lifetime = configuredSeconds > 0
				? Duration.ofSeconds(configuredSeconds) : InMemoryPartyStore.DEFAULT_PENDING_LIFETIME;
		final int memberLimit = configuredLimit > 0 ? configuredLimit : InMemoryPartyStore.DEFAULT_MEMBER_LIMIT;
		synchronized(characterLock)
		{
			store = new InMemoryPartyStore(Instant::now, lifetime, memberLimit);
		}
		return super.activate();
	}

	/**
	 * Releases the current party and invitation state at shutdown.
	 *
	 * @return whether the library shut down successfully
	 */
	@Override
	public boolean shutdown()
	{
		synchronized(characterLock)
		{
			store = new InMemoryPartyStore();
			retiredCharacters.clear();
			departingFollowers.clear();
			travelStates.clear();
		}
		return super.shutdown();
	}
}
