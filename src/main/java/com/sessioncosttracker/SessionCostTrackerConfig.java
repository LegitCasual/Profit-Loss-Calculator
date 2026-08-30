/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SessionCostTrackerConfig.GROUP)
public interface SessionCostTrackerConfig extends Config
{
	String GROUP = "sessioncosttracker";

	@ConfigSection(
		name = "Trips",
		description = "How trips are cut",
		position = 0
	)
	String tripsSection = "trips";

	@ConfigSection(
		name = "Death cost",
		description = "How the Death's Office reclaim fee is estimated",
		position = 1
	)
	String deathSection = "death";

	@ConfigSection(
		name = "Ammo & teleports",
		description = "Extra cost sources, all priced live off the GE",
		position = 2
	)
	String extrasSection = "extras";

	@ConfigItem(
		keyName = "showOverlay",
		name = "In-game overlay",
		description = "Show the current trip cost and session total as an overlay while a session is running",
		position = 0
	)
	default boolean showOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "potionDoseAware",
		name = "Dose-aware potion cost",
		description = "Charge a dosed potion's GE price divided by its dose count per sip, instead of the full price",
		position = 1
	)
	default boolean potionDoseAware()
	{
		return true;
	}

	@ConfigItem(
		keyName = "writeSessionFile",
		name = "Write session log file",
		description = "Append every event to a JSON Lines file under .runelite/session-cost-tracker/",
		position = 2
	)
	default boolean writeSessionFile()
	{
		return true;
	}

	@Range(min = 1, max = 64)
	@ConfigItem(
		keyName = "tripDebounceTiles",
		name = "Trip debounce distance",
		description = "The player must get at least this far from the last bank/GE before a new bank/GE visit cuts a fresh trip",
		section = tripsSection,
		position = 0
	)
	default int tripDebounceTiles()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "autoConfirmDeathFeeOnStop",
		name = "Auto-confirm fees on stop",
		description = "When a session stops, confirm any returned-but-unconfirmed death fees at their estimated value instead of leaving them 'at risk'",
		section = deathSection,
		position = 0
	)
	default boolean autoConfirmDeathFeeOnStop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackAmmo",
		name = "Track ammo",
		description = "Charge for arrows, bolts, darts, thrown weapons, chinchompas and cannonballs that leave your possession. Ava's recovery and cannon pickup are not charged.",
		section = extrasSection,
		position = 0
	)
	default boolean trackAmmo()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackTeleports",
		name = "Track teleport charges",
		description = "Charge for jewellery teleport charges (glory, ring of dueling, games necklace, …) and teleport tablets broken",
		section = extrasSection,
		position = 1
	)
	default boolean trackTeleports()
	{
		return true;
	}
}
