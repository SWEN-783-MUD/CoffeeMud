package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

/**
 * One immediate action attempt. This is not a queued or originating-turn token.
 * A request is consumed even when validation fails; retries need a new request.
 */
public final class ActionRequest
{
	private final GeneralEncounter encounter;
	private final EncounterParticipant participant;
	private final EncounterAction action;
	private boolean claimed;

	public ActionRequest(final GeneralEncounter encounter, final EncounterParticipant participant,
			final EncounterAction action)
	{
		this.encounter=Objects.requireNonNull(encounter,"encounter");
		this.participant=Objects.requireNonNull(participant,"participant");
		this.action=Objects.requireNonNull(action,"action");
	}

	public GeneralEncounter getEncounter() { return encounter; }
	public EncounterParticipant getParticipant() { return participant; }
	public EncounterAction getAction() { return action; }

	/** Called by the resolver; public for CoffeeMud's separate class loaders. */
	public synchronized boolean claim()
	{
		if(claimed)
			return false;
		claimed=true;
		return true;
	}
}
