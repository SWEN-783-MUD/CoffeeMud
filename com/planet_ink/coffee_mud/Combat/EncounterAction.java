package com.planet_ink.coffee_mud.Combat;

/**
 * An action and its immutable, action-specific parameters. The resolver owns
 * common validation, resource spending, and turn advancement. Implementations
 * must not spend resources or advance turns themselves.
 */
public interface EncounterAction
{
	/** @return a nonnegative action cost; End Turn is free */
	public int getActionCost();

	/** Action-specific pre_use checks. Failed validation must have no effects. */
	public ValidationResult validate(GeneralEncounter encounter, EncounterParticipant participant);

	/**
	 * Applies validated effects. A rejected outcome must leave state unchanged.
	 * Unexpected exceptions propagate; effects are not automatically rolled back.
	 */
	public ActionOutcome execute(GeneralEncounter encounter, EncounterParticipant participant);

	/** Optional post_use effects, after resource spending and before advancement. */
	public default void postUse(final GeneralEncounter encounter, final EncounterParticipant participant,
			final ActionOutcome outcome)
	{
	}
}
