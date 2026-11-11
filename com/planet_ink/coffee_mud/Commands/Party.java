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

		final String action = commands.get(1).toUpperCase(Locale.ROOT);
		if(commands.size() == 3)
		{
			executeWithArgument(mob, parties, action, commands.get(2));
			return false;
		}
		if(commands.size() != 2)
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
		case "PENDING":
			parties.showPendingEntries(mob);
			break;
		default:
			showUsage(mob);
			break;
		}
		return false;
	}

	private void executeWithArgument(final MOB mob, final PartyLibrary parties,
			final String action, final String argument)
	{
		switch(action)
		{
		case "INVITE":
			parties.sendInvitation(mob, argument);
			break;
		case "REQUEST":
			parties.requestJoin(mob, argument);
			break;
		case "LEADER":
			parties.transferLeadership(mob, argument);
			break;
		case "ACCEPT":
		case "APPROVE":
		case "DECLINE":
		case "CANCEL":
			final long id = readPendingId(mob, action, argument);
			if(id > 0)
			{
				switch(action)
				{
				case "ACCEPT":
					parties.acceptInvitation(mob, id);
					break;
				case "APPROVE":
					parties.approveRequest(mob, id);
					break;
				case "DECLINE":
					parties.declineEntry(mob, id);
					break;
				case "CANCEL":
					parties.cancelEntry(mob, id);
					break;
				}
			}
			break;
		default:
			showUsage(mob);
			break;
		}
	}

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

	private long readPendingId(final MOB mob, final String action, final String idText)
	{
		try
		{
			final long id = Long.parseLong(idText);
			if(id > 0)
				return id;
		}
		catch(final NumberFormatException error)
		{
			// Invalid text and IDs outside the supported range use the same help.
		}

		mob.tell(L("Use PARTY @x1 <id>. Use PARTY PENDING to find the ID.", action));
		return 0;
	}

	private void showUsage(final MOB mob)
	{
		mob.tell(L("Use PARTY, PARTY CREATE, PARTY LEAVE, PARTY LEADER <name>, PARTY INVITE <name>, PARTY REQUEST <leader>, "
				+ "PARTY PENDING, PARTY ACCEPT <id>, PARTY APPROVE <id>, PARTY DECLINE <id>, or PARTY CANCEL <id>. "
				+ "Use HELP PARTY for details."));
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
