package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

/** A shared prerequisite verdict, with a reason suitable for player feedback. */
public final class ValidationResult
{
	public enum Code
	{
		VALID, NOT_PLAYER, NO_ENCOUNTER, NOT_PARTICIPANT, PENDING, PAUSED,
		COMPLETED, NOT_YOUR_TURN, UNABLE_TO_ACT, INSUFFICIENT_ACTIONS,
		INVALID_ACTION, REJECTED, ALREADY_PROCESSED, RESOLUTION_IN_PROGRESS
	}

	private final Code code;
	private final String reason;

	private ValidationResult(final Code code, final String reason)
	{
		this.code=Objects.requireNonNull(code,"code");
		this.reason=Objects.requireNonNull(reason,"reason");
	}

	public static ValidationResult valid()
	{
		return new ValidationResult(Code.VALID,"");
	}

	public static ValidationResult rejected(final Code code, final String reason)
	{
		if((code==Code.VALID)||(reason==null)||reason.trim().isEmpty())
			throw new IllegalArgumentException("Rejection requires a failure code and reason.");
		return new ValidationResult(code,reason);
	}

	public boolean isValid() { return code==Code.VALID; }
	public Code getCode() { return code; }
	public String getReason() { return reason; }
}
