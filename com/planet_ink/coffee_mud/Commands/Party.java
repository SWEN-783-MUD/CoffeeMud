package com.planet_ink.coffee_mud.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMParms;
import com.planet_ink.coffee_mud.core.CMStrings;
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
			showParty(mob, parties.findParty(mob));
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
			showActionUsage(mob, action);
			return false;
		}

		switch(action)
		{
		case "HELP":
			showUsage(mob);
			break;
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
			showActionUsage(mob, action);
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
			if(!argument.matches("[+-]?[0-9]+"))
			{
				if(action.equals("ACCEPT"))
					parties.acceptInvitation(mob, argument);
				else
					parties.approveRequest(mob, argument);
				break;
			}
			// Numeric selectors retain their range checks before reaching the store.
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
			showActionUsage(mob, action);
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
		case ID_LIMIT:
			mob.tell(L("A party cannot be created right now. Please contact an administrator."));
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

		final String selector = action.equals("ACCEPT") || action.equals("APPROVE") ? L("<name or id>") : L("<id>");
		mob.tell(L("Use PARTY @x1 @x2. Use PARTY PENDING to see your invitations and requests.", action, selector));
		return 0;
	}

	private void showUsage(final MOB mob)
	{
		final StringBuilder help = new StringBuilder(L("^HParty commands^N\n\r"));
		appendHelpLine(help, "PARTY", "View your party");
		appendHelpLine(help, "PARTY CREATE", "Start a party");
		appendHelpLine(help, "PARTY LEAVE", "Leave your party");
		appendHelpLine(help, "PARTY PENDING", "View invitations and requests");

		help.append(L("\n\r^HJoining a party^N\n\r"));
		appendHelpLine(help, "PARTY REQUEST <leader>", "Ask to join a party");
		appendHelpLine(help, "PARTY ACCEPT <name or id>", "Accept an invitation");

		help.append(L("\n\r^HLeader commands^N\n\r"));
		appendHelpLine(help, "PARTY INVITE <name>", "Invite a player");
		appendHelpLine(help, "PARTY APPROVE <name or id>", "Approve a join request");
		appendHelpLine(help, "PARTY LEADER <name>", "Pass leadership to a member");

		help.append(L("\n\r^HInvitations and requests^N\n\r"));
		appendHelpLine(help, "PARTY DECLINE <id>", "Decline an incoming invitation or request");
		appendHelpLine(help, "PARTY CANCEL <id>", "Withdraw a request or party invitation");
		help.append(L("\n\rUse HELP PARTY for details, or HELP FOLLOW for travelling together."));
		mob.tell(help.toString());
	}

	private void appendHelpLine(final StringBuilder help, final String syntax, final String description)
	{
		help.append("  ").append(CMStrings.padRight(L(syntax), 28)).append("  ").append(L(description)).append("\n\r");
	}

	private void showParty(final MOB mob, final PartySnapshot party)
	{
		if(party == null)
		{
			mob.tell(L("You are not in a party.\n\r"
					+ "Use PARTY CREATE to start one, or PARTY REQUEST <leader> to ask to join.\n\r"
					+ "Use PARTY PENDING to check invitations, or PARTY HELP for commands."));
			return;
		}

		final List<String> members = new ArrayList<String>();
		for(final String member : party.getMembers())
			members.add(member.equalsIgnoreCase(mob.Name()) ? L("@x1 (you)", member) : member);

		final String leader = party.getLeader().equalsIgnoreCase(mob.Name())
				? L("You are the party leader.") : L("Party leader: @x1", party.getLeader());
		mob.tell(L("^HYour party^N\n\r@x1\n\r\n\rMembers (@x2): @x3",
				leader, Integer.toString(members.size()), CMParms.toListString(members)));
	}

	private void showActionUsage(final MOB mob, final String action)
	{
		final String argument;
		final String example;
		switch(action)
		{
		case "CREATE":
		case "LEAVE":
		case "PENDING":
		case "HELP":
			argument = "";
			example = "";
			break;
		case "INVITE":
		case "LEADER":
			argument = " <name>";
			example = " Bob";
			break;
		case "REQUEST":
			argument = " <leader>";
			example = " Alice";
			break;
		case "ACCEPT":
		case "APPROVE":
			argument = " <name or id>";
			example = " Alice";
			break;
		case "DECLINE":
		case "CANCEL":
			argument = " <id>";
			example = " 12";
			break;
		default:
			mob.tell(L("Unknown party command '@x1'.", action));
			showUsage(mob);
			return;
		}
		mob.tell(L("Usage: PARTY @x1@x2\n\rExample: PARTY @x1@x3", action, argument, example));
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
