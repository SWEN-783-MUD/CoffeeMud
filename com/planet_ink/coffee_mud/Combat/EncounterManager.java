package com.planet_ink.coffee_mud.Combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Arrival;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Completed;

/**
 * Manages the current combat encounter for a single room.
 */
public class EncounterManager
{
	/** The room associated with this manager. */
	private final Room room;

	/** The room's current encounter. Writes use this manager's lock. */
	private volatile Encounter encounter;

	/** An optional encounter-owned rule that selects a hostile NPC after travel. */
	private Function<Completed, MOB> entryTrigger;

	/** Batches already offered to the entry rule. Old reports can be collected. */
	private final Set<Completed> checkedEntries = Collections.newSetFromMap(new WeakHashMap<Completed, Boolean>());

	/**
	 * Creates an encounter manager for the given room.
	 *
	 * @param room the room associated with this manager
	 * @throws NullPointerException if room is null
	 */
	public EncounterManager(final Room room)
	{
		this.room=Objects.requireNonNull(room, "room");
	}

	/**
	 * Returns the room associated with this manager.
	 *
	 * @return the associated room
	 */
	public Room getRoom()
	{
		return room;
	}

	/**
	 * @return whether the room is in the area selected by coffeemud.encounterArea
	 */
	public boolean isEnabled()
	{
		final String areaName=System.getProperty("coffeemud.encounterArea", "");
		return (!areaName.isEmpty())&&(room.getArea()!=null)
			&&areaName.equalsIgnoreCase(room.getArea().Name());
	}

	/**
	 * Checks whether the player can open an encounter against the NPC.
	 */
	public synchronized boolean canStartEncounter(final MOB player, final MOB target)
	{
		return canOpen(player,target) && !CMLib.tracking().isPartyTravelInProgress(player);
	}

	/**
	 * Rechecks room and participant state before opening the slot.
	 * Call while holding the manager lock.
	 *
	 * @param player the player initiating the encounter
	 * @param target the NPC they would face
	 * @return whether the room can open this encounter
	 */
	private boolean canOpen(final MOB player, final MOB target)
	{
		return (encounter==null)
			&&isEnabled()
			&&(player!=null)
			&&(!player.isMonster())
			&&(target!=null)
			&&target.isMonster()
			&&(player.location()==room)
			&&(target.location()==room)
			&&room.isInhabitant(player)
			&&room.isInhabitant(target)
			&&(!player.isInCombat())
			&&(!target.isInCombat())
			&&(!target.amDead())
			&&CMLib.flags().isAliveAwakeMobileUnbound(player,true)
			&&player.mayPhysicallyAttack(target);
	}

	/**
	 * Registers a pending encounter after rechecking the opening request.
	 *
	 * @return whether a new encounter was registered
	 */
	public synchronized boolean startEncounter(final MOB player, final MOB target)
	{
		final PartyLibrary parties = CMLib.parties();
		return parties.commitEncounterChange(() -> {
			if(!canStartEncounter(player,target))
				return false;

			final List<EncounterParticipant> participants = new ArrayList<EncounterParticipant>();
			participants.add(new EncounterParticipant(player,parties.explorationContext(player)));
			participants.add(new EncounterParticipant(target));
			encounter = new GeneralEncounter(participants);
			return true;
		});
	}

	/**
	 * Installs the room owner's entry rule. The rule receives a finished movement
	 * report and returns the NPC that would start an encounter, or null.
	 *
	 * @param trigger the encounter rule, or null to use only manual initiation
	 */
	public synchronized void setEntryTrigger(final Function<Completed, MOB> trigger)
	{
		entryTrigger = trigger;
	}

	/**
	 * Gives the entry rule one chance to react to a completed move.
	 * Runs room checks outside the manager lock and rechecks placement before opening.
	 *
	 * @param batch the successful arrivals and failed branches
	 */
	public void onPartyTravelCompleted(final Completed batch)
	{
		final Function<Completed, MOB> trigger;
		synchronized(this)
		{
			if((batch.destination != room) || !isEnabled() || (entryTrigger == null)
			|| batch.arrivals.isEmpty() || !checkedEntries.add(batch) || (encounter != null))
				return;
			trigger = entryTrigger;
		}

		final MOB target = trigger.apply(batch);
		final Arrival openingArrival = batch.arrivals.get(0);
		final MOB opener = openingArrival.player;
		if((target == null) || !room.okMessage(opener,
				CMClass.getMsg(opener,target,opener.fetchWieldedItem(),CMMsg.MSG_WEAPONATTACK,null)))
			return;

		synchronized(this)
		{
			final PartyLibrary parties = CMLib.parties();
			parties.commitEncounterChange(() -> {
				if(!canOpen(opener,target) || !parties.isCurrentArrival(openingArrival))
					return false;

				// Enroll only players who arrived in this move and are still here.
				final List<EncounterParticipant> participants = new ArrayList<EncounterParticipant>();
				for(final Arrival arrival : batch.arrivals)
				{
					final MOB player = arrival.player;
					if(parties.isCurrentArrival(arrival) && !player.amDead() && !player.isInCombat())
						participants.add(new EncounterParticipant(player,arrival.exploration));
				}
				participants.add(new EncounterParticipant(target));
				encounter = new GeneralEncounter(participants);
				return true;
			});
		}
	}

	/**
	 * Checks whether the room's encounter currently restricts this participant.
	 * Party commands use this without taking the manager lock.
	 *
	 * @param player the character entering the command
	 * @return whether the character is enrolled in a pending or ongoing encounter
	 */
	public boolean restricts(final MOB player)
	{
		final Encounter current = encounter;
		if(!(current instanceof GeneralEncounter) || (((GeneralEncounter)current).getState() == GeneralEncounter.State.COMPLETED))
			return false;

		for(final EncounterParticipant participant : current.getParticipants())
		{
			if(participant.getMob() == player)
				return true;
		}
		return false;
	}

	/**
	 * Finds online teammates outside the current encounter using the live rosters.
	 * The encounter owner can use this when deciding whom to notify.
	 *
	 * @param current the encounter requesting candidates
	 * @return a snapshot of candidates, without granting encounter admission
	 */
	public List<MOB> outsidePartyMembers(final Encounter current)
	{
		if((current == null) || (encounter != current))
			return Collections.emptyList();

		final PartyLibrary parties = CMLib.parties();
		final Set<MOB> enrolled = new LinkedHashSet<MOB>();
		for(final EncounterParticipant participant : current.getParticipants())
			enrolled.add(participant.getMob());

		final Set<MOB> candidates = new LinkedHashSet<MOB>();
		for(final MOB participant : enrolled)
			candidates.addAll(parties.onlinePartyMembers(participant));
		candidates.removeAll(enrolled);
		return Collections.unmodifiableList(new ArrayList<MOB>(candidates));
	}

	/**
	 * Activates this room's pending encounter.
	 *
	 * @param current the encounter that has finished setup
	 * @return whether the room activated it
	 */
	public synchronized boolean activateEncounter(final GeneralEncounter current)
	{
		return (current != null) && (encounter == current) && current.activate();
	}

	/**
	 * Closes this room's active encounter and resets its participants' activities.
	 *
	 * @param current the encounter that has finished
	 * @return whether the room completed it
	 */
	public synchronized boolean completeEncounter(final GeneralEncounter current)
	{
		if((current == null) || (encounter != current) || (current.getState() != GeneralEncounter.State.ACTIVE))
			return false;

		final PartyLibrary parties = CMLib.parties();
		return parties.commitEncounterChange(() -> {
			// Keep the active phase visible until all participant resets have finished.
			for(final EncounterParticipant participant : current.getParticipants())
				parties.resetExplorationActivity(participant.getMob());
			current.complete();
			encounter = null;
			return true;
		});
	}

	/**
	 * @return the current encounter, or null if the room has none
	 */
	public Encounter getEncounter()
	{
		return encounter;
	}

	/**
	 * Registers an encounter if the room's slot is empty.
	 *
	 * @param newEncounter the encounter to register
	 * @return true if the encounter was registered
	 * @throws NullPointerException if newEncounter is null
	 */
	public synchronized boolean trySetEncounter(final Encounter newEncounter)
	{
		Objects.requireNonNull(newEncounter, "newEncounter");
		if(encounter!=null)
			return false;
		return CMLib.parties().commitEncounterChange(() -> {
			encounter = newEncounter;
			return true;
		});
	}

	/**
	 * Clears the slot only if it still holds the given encounter.
	 *
	 * @param finishedEncounter the encounter to clear
	 * @return true if the encounter was cleared
	 * @throws NullPointerException if finishedEncounter is null
	 */
	public synchronized boolean clearEncounter(final Encounter finishedEncounter)
	{
		Objects.requireNonNull(finishedEncounter, "finishedEncounter");
		if(encounter!=finishedEncounter)
			return false;
		return CMLib.parties().commitEncounterChange(() -> {
			encounter = null;
			return true;
		});
	}
}
