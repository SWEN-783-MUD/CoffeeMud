package com.planet_ink.coffee_mud.Libraries;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMProps;
import com.planet_ink.coffee_mud.core.CMStrings;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry.Type;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingView;
import com.planet_ink.coffee_mud.core.party.PartyStore.TransferResult;

/** Handles live players and feedback around the party store's membership rules. */
public class CMParties extends StdLibrary implements PartyLibrary
{
	/** Keeps character name reads and party operations together with rename and deletion. */
	private final Object characterLock = new Object();

	/** Deleted live objects with commands that may still be finishing. Guarded by characterLock. */
	private final Set<MOB> retiredCharacters = Collections.newSetFromMap(new WeakHashMap<MOB, Boolean>());

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

	/**
	 * Uses the character's real name so the lookup works across sessions.
	 *
	 * @param character the player whose name is needed
	 * @return the canonical character key used by the store
	 */
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
			store.deleteCharacter(characterKey(character));
			return DeleteResult.DELETED;
		}
	}

	/**
	 * Runs a store operation with the actor's current name. Callers send feedback
	 * after this method returns so session and room code run outside the lock.
	 *
	 * @param <T> the operation's result type
	 * @param actor the character acting on party state
	 * @param operation the store operation using the current key
	 * @return the result, or {@code null} if the actor is unavailable
	 */
	private <T> T withCharacter(final MOB actor, final Function<String, T> operation)
	{
		synchronized(characterLock)
		{
			return canUseCharacter(actor) ? operation.apply(characterKey(actor)) : null;
		}
	}

	/**
	 * Checks whether a live object can still act under its character name.
	 * Call this while holding characterLock.
	 *
	 * @param character the actor or target to check
	 * @return whether the player object can use party state
	 */
	private boolean canUseCharacter(final MOB character)
	{
		return isPlayerCharacter(character) && !character.amDestroyed() && !retiredCharacters.contains(character);
	}

	/** {@inheritDoc} */
	@Override
	public CreateResult createParty(final MOB character)
	{
		if(!isPlayerCharacter(character))
			return CreateResult.NOT_PLAYER;
		if(character.isInCombat())
			return CreateResult.IN_COMBAT;
		final CreateResult result = withCharacter(character, key -> store.create(key) == null
				? CreateResult.ALREADY_MEMBER : CreateResult.CREATED);
		return result == null ? CreateResult.NOT_PLAYER : result;
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
		return Boolean.TRUE.equals(withCharacter(character, key -> Boolean.valueOf(store.leave(key))));
	}

	/** {@inheritDoc} */
	@Override
	public void transferLeadership(final MOB leader, final String memberName)
	{
		if(!canManageParty(leader))
			return;

		// Match a roster key so a disconnected member can still take over.
		final String memberKey = CMStrings.capitalizeAndLower(memberName);
		final TransferResult result;
		final MOB newLeader;
		synchronized(characterLock)
		{
			if(!canUseCharacter(leader))
				return;
			result = store.transferLeadership(characterKey(leader), memberKey);
			newLeader = result == TransferResult.TRANSFERRED ? findOnlinePlayer(memberKey) : null;
		}
		switch(result)
		{
		case TRANSFERRED:
			leader.tell(L("You passed party leadership to @x1.", memberKey));
			if(newLeader != null)
				newLeader.tell(L("@x1 passed party leadership to you. Use PARTY PENDING to see pending entries.",
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
		if(!canManageParty(sender))
			return;

		final MOB recipient;
		final PendingResult result;
		synchronized(characterLock)
		{
			if(!canUseCharacter(sender))
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
				result = recipient == null ? null : store.invite(senderKey, characterKey(recipient));
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
			recipient.tell(L("@x1 invited you to their party. Use PARTY ACCEPT @x2 or PARTY PENDING.",
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
		}
	}

	/** {@inheritDoc} */
	@Override
	public void requestJoin(final MOB candidate, final String leaderName)
	{
		if(!canManageParty(candidate))
			return;

		final MOB leader;
		final PendingResult result;
		synchronized(characterLock)
		{
			if(!canUseCharacter(candidate))
				return;

			// Keep the online lookup and entry creation on the same names.
			leader = findOnlinePlayer(leaderName);
			result = leader == null ? null : store.requestJoin(characterKey(candidate), characterKey(leader));
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
				leader.tell(L("@x1 asked to join your party. Use PARTY APPROVE @x2 or PARTY PENDING.",
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

		// Round up so a live entry with less than a second left still shows time.
		for(final PendingView view : pending)
		{
			final PartyPendingEntry entry = view.getEntry();
			final Duration remaining = Duration.between(now, entry.getExpiresAt());
			final long roundedSeconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
			final String seconds = Long.toString(Math.max(0, roundedSeconds));
			final String id = Long.toString(entry.getId());
			final boolean candidate = key.equals(entry.getCandidateKey());

			if(entry.getType() == Type.INVITATION)
			{
				if(candidate)
					character.tell(L("@x1 invited you to their party. @x2 seconds left. Use PARTY ACCEPT @x3 or PARTY DECLINE @x3.",
							entry.getSenderKey(), seconds, id));
				else
					character.tell(L("Your party invited @x1. @x2 seconds left. Use PARTY CANCEL @x3.",
							entry.getCandidateKey(), seconds, id));
			}
			else
			{
				if(candidate)
					character.tell(L("You asked to join @x1's party. @x2 seconds left. Use PARTY CANCEL @x3.",
							view.getLeaderKey(), seconds, id));
				else
					character.tell(L("@x1 asked to join your party. @x2 seconds left. Use PARTY APPROVE @x3 or PARTY DECLINE @x3.",
							entry.getCandidateKey(), seconds, id));
			}
		}

		if(character.isInCombat())
			character.tell(L("You can act on pending entries after combat if they are still open."));
	}

	/** {@inheritDoc} */
	@Override
	public void acceptInvitation(final MOB character, final long invitationId)
	{
		if(canManageParty(character))
		{
			final JoinResult result = withCharacter(character, key -> store.acceptInvitation(key, invitationId));
			if(result != null)
				reportJoinResult(character, result, Type.INVITATION, null);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void approveRequest(final MOB leader, final long requestId)
	{
		if(!canManageParty(leader))
			return;

		final JoinResult result;
		final MOB candidate;
		synchronized(characterLock)
		{
			if(!canUseCharacter(leader))
				return;
			result = store.approveRequest(characterKey(leader), requestId);

			// Capture the recipient before a rename or deletion can reuse the key.
			final String candidateKey = result.getJoinedCandidateKey();
			candidate = candidateKey == null ? null : findOnlinePlayer(candidateKey);
		}
		reportJoinResult(leader, result, Type.REQUEST, candidate);
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

	/**
	 * Closes an entry and captures the other side before either name can change.
	 *
	 * @param actor the player who entered the command
	 * @param entryId the entry to close
	 * @param declined whether the actor is responding or withdrawing
	 */
	private void closePendingEntry(final MOB actor, final long entryId, final boolean declined)
	{
		if(!canManageParty(actor))
			return;

		final PendingView closed;
		final MOB other;
		synchronized(characterLock)
		{
			if(!canUseCharacter(actor))
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

	/**
	 * Reports the closure to the actor and to the other side if they are online.
	 *
	 * @param actor the player who entered the command
	 * @param closed the entry closed by the store, or {@code null} if unavailable
	 * @param other the player on the other side, or {@code null} if offline
	 * @param declined whether the actor turned it down or withdrew it
	 */
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

	/**
	 * Explains the outcome of an acceptance or approval after the store finishes.
	 *
	 * @param actor the player who entered the command
	 * @param result the outcome from the store
	 * @param type the entry type handled by the command
	 * @param candidate the newly approved player, if online
	 */
	private void reportJoinResult(final MOB actor, final JoinResult result, final Type type, final MOB candidate)
	{
		final boolean approval = type == Type.REQUEST;
		switch(result.getStatus())
		{
		case JOINED:
			if(approval)
			{
				final String candidateKey = result.getJoinedCandidateKey();
				actor.tell(L("@x1 joined your party. Use PARTY to see the roster.", candidateKey));

				if(candidate != null)
					candidate.tell(L("@x1 approved your request. You joined the party. Use PARTY to see the roster.",
							actor.Name()));
			}
			else
				actor.tell(L("You joined the party. Use PARTY to see the roster."));
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
		}
	}

	/**
	 * Checks the actor before a party management command changes state.
	 *
	 * @param character the actor trying to make the change
	 * @return whether the player can make the change now
	 */
	private boolean canManageParty(final MOB character)
	{
		if(!isPlayerCharacter(character))
		{
			if(character != null)
				character.tell(L("Only player characters can use parties."));
			return false;
		}

		if(character.isInCombat())
		{
			character.tell(L("Wait until combat ends before using that party command."));
			return false;
		}
		return true;
	}

	/**
	 * Finds an active player in this game by their exact character name.
	 * The session list includes players in other rooms and checks that they are in-game.
	 * Call this while holding characterLock so the returned object matches its name.
	 *
	 * @param name the character name supplied by a player
	 * @return the live character, or {@code null} if nobody matches
	 */
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
		}
		return super.shutdown();
	}
}
