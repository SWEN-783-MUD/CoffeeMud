package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

/** Execution result and resource usage, applied before shared turn follow-up. */
public final class ActionOutcome
{
	private final ValidationResult validation;
	private final boolean endTurn;
	private final boolean attack;
	private final int diagonalSteps;

	private ActionOutcome(final ValidationResult validation, final boolean endTurn,
			final boolean attack, final int diagonalSteps)
	{
		this.validation=Objects.requireNonNull(validation,"validation");
		if(diagonalSteps<0)
			throw new IllegalArgumentException("Diagonal usage cannot be negative.");
		this.endTurn=endTurn;
		this.attack=attack;
		this.diagonalSteps=diagonalSteps;
	}

	public static ActionOutcome completed()
	{
		return completed(false,0);
	}

	public static ActionOutcome completed(final boolean attack, final int diagonalSteps)
	{
		return new ActionOutcome(ValidationResult.valid(),false,attack,diagonalSteps);
	}

	public static ActionOutcome endTurn()
	{
		return new ActionOutcome(ValidationResult.valid(),true,false,0);
	}

	public static ActionOutcome rejected(final ValidationResult validation)
	{
		if(validation.isValid())
			throw new IllegalArgumentException("A rejected outcome requires failed validation.");
		return new ActionOutcome(validation,false,false,0);
	}

	public boolean isSuccessful() { return validation.isValid(); }
	public ValidationResult getValidation() { return validation; }
	public boolean endsTurn() { return endTurn; }
	public boolean isAttack() { return attack; }
	public int getDiagonalSteps() { return diagonalSteps; }
}
