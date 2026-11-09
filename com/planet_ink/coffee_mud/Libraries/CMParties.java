package com.planet_ink.coffee_mud.Libraries;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingView;

/** Handles live players and feedback around the party store's membership rules. */
public class CMParties extends StdLibrary implements PartyLibrary
{
	/** The shared store for this server run, replaced when the library shuts down. */
	private volatile PartyStore store = new InMemoryPartyStore();

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
	public CreateResult createParty(final MOB character)
	{
		if(!isPlayerCharacter(character))
			return CreateResult.NOT_PLAYER;
		if(character.isInCombat())
			return CreateResult.IN_COMBAT;
		if(store.create(characterKey(character)) == null)
			return CreateResult.ALREADY_MEMBER;
		return CreateResult.CREATED;
	}

	/** {@inheritDoc} */
	@Override
	public PartySnapshot findParty(final MOB character)
	{
		if(!isPlayerCharacter(character))
			return null;
		return store.findByMember(characterKey(character));
	}

	/** {@inheritDoc} */
	@Override
	public boolean leaveParty(final MOB character)
	{
		return isPlayerCharacter(character) && store.leave(characterKey(character));
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

		// Check the sender before looking up another player. The store rechecks leadership when writing.
		final PartyStore currentStore = store;
		final String senderKey = characterKey(sender);
		final PartySnapshot party = currentStore.findByMember(senderKey);
		if((party == null) || !senderKey.equals(party.getLeader()))
		{
			sender.tell(L("Only a party leader can send invitations."));
			return;
		}

		// Resolve the live player before asking the store to record the invite.
		final MOB recipient = findOnlinePlayer(recipientName);
		if(recipient == null)
		{
			sender.tell(L("No online player has the character name @x1.", recipientName));
			return;
		}

		final PendingResult result = currentStore.invite(senderKey, characterKey(recipient));
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

		// Use the same online lookup as invitations. The store checks current leadership.
		final MOB leader = findOnlinePlayer(leaderName);
		if(leader == null)
		{
			candidate.tell(L("No online player has the character name @x1.", leaderName));
			return;
		}

		final PartyStore currentStore = store;
		final String leaderKey = characterKey(leader);
		final PendingResult result = currentStore.requestJoin(characterKey(candidate), leaderKey);
		switch(result.getStatus())
		{
		case CREATED:
		{
			final PartyPendingEntry entry = result.getEntry();
			final String id = Long.toString(entry.getId());
			candidate.tell(L("You asked to join @x1's party. Request ID is @x2.", leader.Name(), id));

			// If leadership changed while the request was opening, the new leader can see it in PARTY PENDING.
			final PartySnapshot current = currentStore.findByMember(leaderKey);
			if((current != null) && (current.getId() == entry.getPartyId()) && leaderKey.equals(current.getLeader()))
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
		if(!isPlayerCharacter(character))
			return;

		final Instant now = Instant.now();
		final String key = characterKey(character);
		final List<PendingView> pending = store.pendingEntries(key);
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
			reportJoinResult(character, store.acceptInvitation(characterKey(character), invitationId), Type.INVITATION);
	}

	/** {@inheritDoc} */
	@Override
	public void approveRequest(final MOB leader, final long requestId)
	{
		if(canManageParty(leader))
			reportJoinResult(leader, store.approveRequest(characterKey(leader), requestId), Type.REQUEST);
	}

	/** {@inheritDoc} */
	@Override
	public void declineEntry(final MOB actor, final long entryId)
	{
		if(canManageParty(actor))
			reportClosedEntry(actor, store.decline(characterKey(actor), entryId), true);
	}

	/** {@inheritDoc} */
	@Override
	public void cancelEntry(final MOB actor, final long entryId)
	{
		if(canManageParty(actor))
			reportClosedEntry(actor, store.cancel(characterKey(actor), entryId), false);
	}

	/**
	 * Reports the closure to the actor and to the other side if they are online.
	 *
	 * @param actor the player who entered the command
	 * @param closed the entry closed by the store, or {@code null} if unavailable
	 * @param declined whether the actor turned it down or withdrew it
	 */
	private void reportClosedEntry(final MOB actor, final PendingView closed, final boolean declined)
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

		// Use the leader captured when the entry closed, even if someone else sent the original invite.
		final String otherKey = characterKey(actor).equals(entry.getCandidateKey())
				? closed.getLeaderKey() : entry.getCandidateKey();
		final MOB other = findOnlinePlayer(otherKey);
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
	 */
	private void reportJoinResult(final MOB actor, final JoinResult result, final Type type)
	{
		final boolean approval = type == Type.REQUEST;
		switch(result.getStatus())
		{
		case JOINED:
			if(approval)
			{
				final String candidateKey = result.getJoinedCandidateKey();
				actor.tell(L("@x1 joined your party. Use PARTY to see the roster.", candidateKey));

				// Approval uses the character key even if the candidate logged out.
				final MOB candidate = findOnlinePlayer(candidateKey);
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
			character.tell(L("Wait until combat ends before acting on party invitations or requests."));
			return false;
		}
		return true;
	}

	/**
	 * Finds an active player in this game by their exact character name.
	 * The session list includes players in other rooms and checks that they are in-game.
	 *
	 * @param name the character name supplied by a player
	 * @return the live character, or {@code null} if nobody matches
	 */
	private MOB findOnlinePlayer(final String name)
	{
		for(final Session session : CMLib.sessions().localOnlineIterableAllHosts())
		{
			final MOB player = session.mob();
			if(isPlayerCharacter(player) && (player.session() == session) && player.Name().equalsIgnoreCase(name))
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
		store = new InMemoryPartyStore(Instant::now, lifetime, memberLimit);
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
		store = new InMemoryPartyStore();
		return super.shutdown();
	}
}
