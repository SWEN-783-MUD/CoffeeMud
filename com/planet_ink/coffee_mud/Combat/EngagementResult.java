package com.planet_ink.coffee_mud.Combat;

public class EngagementResult {
    
    private final Outcome outcome;
    private final Encounter encounter;
    private final String reason;
    
    
    private EngagementResult(final Outcome outcome, final Encounter encounter, final String reason) {
        this.outcome = outcome;
        this.encounter = encounter;
        this.reason = reason;
    }
    
    /**
     * Creates an EngagementResult representing a newly created encounter.
     * @param encounter the newly created encounter
     * @return an EngagementResult with outcome CREATED and the provided encounter
     * @throws IllegalArgumentException if the provided encounter is null
     */
    public static EngagementResult created(final Encounter encounter) {
        if (encounter == null) {
            throw new IllegalArgumentException("Encounter cannot be null for a created engagement result");
        }
        
        return new EngagementResult(Outcome.CREATED, encounter, "");
    }
    
    /**
     * Creates an EngagementResult representing a reused encounter.
     * @param encounter the reused encounter
     * @return an EngagementResult with outcome REUSED and the provided encounter
     * @throws IllegalArgumentException if the provided encounter is null
     */
    public static EngagementResult reused(final Encounter encounter)
    {
        if (encounter == null)
        {
            throw new IllegalArgumentException("Encounter cannot be null for a reused engagement result");
        }

        return new EngagementResult(Outcome.REUSED, encounter, "");
    }
    
    /**
     * Creates an EngagementResult representing a rejected engagement.
     * @param reason the reason for rejection
     * @return an EngagementResult with outcome REJECTED and the provided reason
     * @throws IllegalArgumentException if the provided reason is null or empty
     */
    public static EngagementResult rejected(final String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason cannot be null or empty for a rejected engagement result");
        }
        return new EngagementResult(Outcome.REJECTED, null, reason);
    }
    
    public Outcome getOutcome() {
        return outcome;
    }
    
    public Encounter getEncounter() {
        return encounter;
    }
    
    public String getReason() {
        return reason;
    }
    
    
    public enum Outcome
    {
        CREATED,
        REUSED,
        REJECTED
    }
    
}
