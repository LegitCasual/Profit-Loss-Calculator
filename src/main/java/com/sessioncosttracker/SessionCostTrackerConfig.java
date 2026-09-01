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
		name = "Death cost",
		description = "How the Death's Office reclaim fee is estimated",
		position = 0
	)
	String deathSection = "death";

	@ConfigSection(
		name = "Ammo & teleports",
		description = "Extra cost sources, all priced live off the GE",
		position = 1
	)
	String extrasSection = "extras";

	@ConfigSection(
		name = "Income",
		description = "What counts as gp coming in, and how it is valued",
		position = 2
	)
	String incomeSection = "income";

	@ConfigItem(
		keyName = "showOverlay",
		name = "In-game overlay",
		description = "Show the running session net and kill tally as an overlay while a session is running",
		position = 1
	)
	default boolean showOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showIncomeList",
		name = "Show income list (Session tab)",
		description = "On the Session tab, also show income grouped one row per source. The Targeted tab always shows its 'Other income'.",
		position = 4
	)
	default boolean showIncomeList()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCostList",
		name = "Show cost list",
		description = "Also show a flat time-ordered list of every consumable / spell / teleport / ammo cost (both tabs).",
		position = 5
	)
	default boolean showCostList()
	{
		return false;
	}

	@ConfigItem(
		keyName = "potionDoseAware",
		name = "Dose-aware potion cost",
		description = "Charge a dosed potion's GE price divided by its dose count per sip, instead of the full price",
		position = 2
	)
	default boolean potionDoseAware()
	{
		return true;
	}

	@ConfigItem(
		keyName = "writeSessionFile",
		name = "Write session log file",
		description = "Append every event to a JSON Lines file under .runelite/session-cost-tracker/",
		position = 3
	)
	default boolean writeSessionFile()
	{
		return true;
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

	@ConfigItem(
		keyName = "trackLoot",
		name = "Track loot",
		description = "Count monster drops, PvP kills, reward chests and pickpockets as income. Needs RuneLite's built-in Loot Tracker plugin enabled (it is by default).",
		section = incomeSection,
		position = 0
	)
	default boolean trackLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackPickups",
		name = "Track ground pickups",
		description = "Count items you take off the ground (or telegrab) that aren't already credited to one of your kills. Picking your own dropped junk back up will count.",
		section = incomeSection,
		position = 1
	)
	default boolean trackPickups()
	{
		return true;
	}

	@ConfigItem(
		keyName = "countUncollectedDrops",
		name = "Count uncollected drops",
		description = "Count the full value of everything that dropped toward profit, even loot left on the ground. Off = only loot you actually picked up counts; the rest shows as 'potential'.",
		section = incomeSection,
		position = 2
	)
	default boolean countUncollectedDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "incomeValuation",
		name = "Value loot at",
		description = "GE price, High Alchemy value, or whichever is higher. Coins are always face value. Cost is always GE priced.",
		section = incomeSection,
		position = 3
	)
	default IncomeValuation.Mode incomeValuation()
	{
		return IncomeValuation.Mode.GE;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "ignoreIncomeBelow",
		name = "Hide loot under (gp)",
		description = "Drops worth less than this are left out of the income list and the totals. 0 shows everything.",
		section = incomeSection,
		position = 4
	)
	default int ignoreIncomeBelow()
	{
		return 0;
	}

	// Internal state, not a user setting - set once the first-run notice has been shown.
	@ConfigItem(
		keyName = "welcomeSeen",
		name = "welcomeSeen",
		description = "Internal - the first-run notice has been shown",
		hidden = true
	)
	default boolean welcomeSeen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "welcomeSeen",
		name = "welcomeSeen",
		description = "Internal - the first-run notice has been shown",
		hidden = true
	)
	void welcomeSeen(boolean seen);
}
