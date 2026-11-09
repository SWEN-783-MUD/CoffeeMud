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
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;

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
		if(!canChangeInvitations(sender))
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
			sender.tell(L("Your invitation to @x1 is already pending. Invitation ID is @x2.",
					recipient.Name(), Long.toString(result.getEntry().getId())));
			break;
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
	public void showPendingInvitations(final MOB character)
	{
		if(!isPlayerCharacter(character))
			return;

		final Instant now = Instant.now();
		final List<PartyPendingEntry> pending = store.pendingEntries(characterKey(character));
		if(pending.isEmpty())
		{
			character.tell(L("You have no pending party invitations."));
			return;
		}

		// Round up so a live invite with less than a second left still shows time.
		for(final PartyPendingEntry invite : pending)
		{
			final Duration remaining = Duration.between(now, invite.getExpiresAt());
			final long seconds = Math.max(0, remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1));
			character.tell(L("@x1 invited you to their party. @x2 seconds left. Use PARTY ACCEPT @x3.",
					invite.getSenderKey(), Long.toString(seconds), Long.toString(invite.getId())));
		}

		if(character.isInCombat())
			character.tell(L("You can accept after combat if the invitation is still open."));
	}

	/** {@inheritDoc} */
	@Override
	public void acceptInvitation(final MOB character, final long invitationId)
	{
		if(!canChangeInvitations(character))
			return;

		switch(store.acceptInvitation(characterKey(character), invitationId).getStatus())
		{
		case JOINED:
			character.tell(L("You joined the party. Use PARTY to see the roster."));
			break;
		case ALREADY_MEMBER:
			character.tell(L("You already belong to a party. Use PARTY LEAVE before joining another."));
			break;
		case ENTRY_UNAVAILABLE:
			character.tell(L("That invitation is unavailable. Use PARTY PENDING to see your invitations."));
			break;
		case EXPIRED:
			character.tell(L("That party invitation has expired."));
			break;
		case PARTY_FULL:
			character.tell(L("That party is full. You can try again if a seat opens before the invitation expires."));
			break;
		}
	}

	/**
	 * Checks the caller before an invite or acceptance changes party state.
	 *
	 * @param character the actor trying to make the change
	 * @return whether the player can make the change now
	 */
	private boolean canChangeInvitations(final MOB character)
	{
		if(!isPlayerCharacter(character))
		{
			if(character != null)
				character.tell(L("Only player characters can use parties."));
			return false;
		}

		if(character.isInCombat())
		{
			character.tell(L("Wait until combat ends before sending or accepting party invitations."));
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
