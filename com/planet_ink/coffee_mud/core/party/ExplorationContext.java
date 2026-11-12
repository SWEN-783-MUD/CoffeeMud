package com.planet_ink.coffee_mud.core.party;

import com.planet_ink.coffee_mud.Locales.interfaces.Room;

/** One character's exploration choice and approach to their current room. */
public final class ExplorationContext
{
	/** Used when the character entered without a known return connection. */
	public static final int UNKNOWN_ENTRY = -1;

	/** The exploration activities a player can choose this milestone. */
	public enum Activity
	{
		/** Explore without a special approach. */
		NORMAL("normal", "Normal"),
		/** Ask the encounter rules to consider this character's careful approach. */
		AVOID_NOTICE("avoid notice", "Avoid Notice");

		/** The text a player types after EXPLORE. */
		public final String command;

		public final String label;

		private Activity(final String command, final String label)
		{
			this.command = command;
			this.label = label;
		}

		/**
		 * Matches a player command without depending on its capitalization.
		 *
		 * @param text the words following EXPLORE
		 * @return the matching activity, or null if the choice is unknown
		 */
		public static Activity fromCommand(final String text)
		{
			for(final Activity activity : values())
			{
				if(activity.command.equalsIgnoreCase(text))
					return activity;
			}
			return null;
		}
	}

	/** The player's current choice, independent of party membership. */
	public final Activity activity;

	/** The room the approach describes, or null while the character is unplaced. */
	public final Room room;

	/** The direction from this room back through the entry, or {@link #UNKNOWN_ENTRY}. */
	public final int entrySide;

	/** Increments whenever this character's exploration context changes. */
	public final long revision;

	/**
	 * Creates a fixed view of the activity and entry.
	 *
	 * @param activity the selected activity
	 * @param room the room the character occupies
	 * @param entrySide the actual entry side, or {@link #UNKNOWN_ENTRY}
	 * @param revision the character's context revision
	 */
	public ExplorationContext(final Activity activity, final Room room, final int entrySide, final long revision)
	{
		this.activity = activity;
		this.room = room;
		this.entrySide = entrySide;
		this.revision = revision;
	}
}
