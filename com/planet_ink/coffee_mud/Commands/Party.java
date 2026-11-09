package com.planet_ink.coffee_mud.Commands;

import java.util.List;
import java.util.Locale;

import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMParms;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;

/** Reads PARTY commands and sends the requested action to the party library. */
public class Party extends StdCommand
{
	/** The command name registered with CoffeeMud. */
	private final String[] access = I(new String[] {"PARTY"});

	/**
	 * Supplies the name players use to reach the command.
	 *
	 * @return the registered command words
	 */
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Uses the shared party library. Command tests can provide an isolated one.
	 *
	 * @return the library to use for this command
	 */
	protected PartyLibrary parties()
	{
		return CMLib.parties();
	}

	/**
	 * Checks the command shape and runs the requested party action.
	 *
	 * @param mob the player entering the command
	 * @param commands the command words including PARTY
	 * @param metaFlags CoffeeMud's command flags
	 * @return {@code false}, as the action is handled here
	 */
	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
	{
		final PartyLibrary parties = parties();
		if(!parties.isPlayerCharacter(mob))
		{
			mob.tell(L("Only player characters can use parties."));
			return false;
		}
		if(commands.isEmpty())
		{
			showUsage(mob);
			return false;
		}

		if(commands.size() == 1)
		{
			final PartySnapshot party = parties.findParty(mob);
			if(party == null)
				mob.tell(L("You are not in a party. Use PARTY CREATE to start one."));
			else
				mob.tell(L("Your party leader is @x1.\n\rMembers are @x2.",
					party.getLeader(), CMParms.toListString(party.getMembers())));
			return false;
		}

		// Only INVITE and ACCEPT take another word after the action.
		final String action = commands.get(1).toUpperCase(Locale.ROOT);
		final int wordCount = ("INVITE".equals(action) || "ACCEPT".equals(action)) ? 3 : 2;
		if(commands.size() != wordCount)
		{
			showUsage(mob);
			return false;
		}

		switch(action)
		{
		case "CREATE":
			createParty(mob, parties);
			break;
		case "LEAVE":
			if(parties.leaveParty(mob))
				mob.tell(L("You left the party."));
			else
				mob.tell(L("You are not in a party."));
			break;
		case "INVITE":
			parties.sendInvitation(mob, commands.get(2));
			break;
		case "PENDING":
			parties.showPendingInvitations(mob);
			break;
		case "ACCEPT":
			acceptInvitation(mob, parties, commands.get(2));
			break;
		default:
			showUsage(mob);
			break;
		}
		return false;
	}

	/**
	 * Reports the result of creating a party.
	 *
	 * @param mob the player creating the party
	 * @param parties the library handling the change
	 */
	private void createParty(final MOB mob, final PartyLibrary parties)
	{
		switch(parties.createParty(mob))
		{
		case CREATED:
			mob.tell(L("You created a party. You are its leader."));
			break;
		case ALREADY_MEMBER:
			mob.tell(L("You are already in a party. Use PARTY LEAVE before creating another."));
			break;
		case IN_COMBAT:
			mob.tell(L("Wait until combat ends before creating a party."));
			break;
		case NOT_PLAYER:
			mob.tell(L("Only player characters can use parties."));
			break;
		}
	}

	/**
	 * Reads an invitation ID before sending an acceptance to the library.
	 *
	 * @param mob the player accepting an invitation
	 * @param parties the library handling the change
	 * @param idText the invitation ID typed by the player
	 */
	private void acceptInvitation(final MOB mob, final PartyLibrary parties, final String idText)
	{
		long invitationId;
		try
		{
			invitationId = Long.parseLong(idText);
		}
		catch(final NumberFormatException error)
		{
			invitationId = 0;
		}

		if(invitationId > 0)
			parties.acceptInvitation(mob, invitationId);
		else
			mob.tell(L("Use PARTY ACCEPT <id>. Use PARTY PENDING to find the invitation ID."));
	}

	/**
	 * Lists the supported forms after invalid input.
	 *
	 * @param mob the player who needs help
	 */
	private void showUsage(final MOB mob)
	{
		mob.tell(L("Use PARTY, PARTY CREATE, PARTY LEAVE, PARTY INVITE <name>, PARTY PENDING, or PARTY ACCEPT <id>."));
	}

	/**
	 * Keeps party commands free during exploration.
	 *
	 * @param mob the player entering the command
	 * @param commands the command words
	 * @return zero action cost
	 */
	@Override
	public double actionsCost(final MOB mob, final List<String> commands)
	{
		return 0.0;
	}

	/**
	 * Keeps the allowed party commands free during combat.
	 *
	 * @param mob the player entering the command
	 * @param commands the command words
	 * @return zero action cost
	 */
	@Override
	public double combatActionsCost(final MOB mob, final List<String> commands)
	{
		return 0.0;
	}

	/**
	 * Keeps party choices with the player making them.
	 *
	 * @return {@code false}, so another actor cannot order a party command
	 */
	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
