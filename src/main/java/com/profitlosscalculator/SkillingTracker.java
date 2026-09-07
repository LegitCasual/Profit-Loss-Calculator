/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Watches non-combat skill XP so the plugin can tell which ticks were a skilling action.
 *
 * <p>Every {@code StatChanged} is fed in through {@link #record}. A tracked skill whose total
 * XP rose becomes the {@link #activeSkill(int) active skill} for that tick and the next one -
 * the plugin then books whatever the inventory / worn / rune-pouch diff shows that tick as
 * that skill's materials (out) and product (in). Combat skills, Magic (alch / superheat /
 * enchant are handled elsewhere and would double-count runes), and the skills whose XP lands
 * far from the item change (Farming, Agility, Thieving) are deliberately not tracked.
 *
 * <p>Pure and non-injected - a {@code new} field on the plugin, like {@link AmmoTracker}.
 */
class SkillingTracker
{
	/** A skilling action's XP and its item change land within this many ticks of each other. */
	private static final int WINDOW_TICKS = 1;

	/** Skills whose XP gain reliably coincides with the material-in / product-out on that tick. */
	static final Set<Skill> TRACKED = Collections.unmodifiableSet(EnumSet.of(
		Skill.MINING, Skill.FISHING, Skill.WOODCUTTING, Skill.HUNTER,
		Skill.COOKING, Skill.FIREMAKING, Skill.SMITHING, Skill.CRAFTING,
		Skill.FLETCHING, Skill.HERBLORE, Skill.RUNECRAFT, Skill.CONSTRUCTION,
		Skill.PRAYER));

	/** Tracked skill -&gt; last total XP seen. Absent = not seeded yet (first hit is a no-op). */
	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);

	private Skill activeSkill;
	private int activeTick = Integer.MIN_VALUE;

	/**
	 * Seed the XP baseline from the current totals (empty map = leave unseeded, e.g. logged
	 * out) and clear any active skill. Called on run start, on resume, and on logout so a
	 * later XP jump - a relogin's stat storm, or XP gained while paused - is not read as an
	 * action.
	 */
	void reset(Map<Skill, Integer> currentXp)
	{
		xp.clear();
		if (currentXp != null)
		{
			for (Skill s : TRACKED)
			{
				final Integer v = currentXp.get(s);
				if (v != null)
				{
					xp.put(s, v);
				}
			}
		}
		activeSkill = null;
		activeTick = Integer.MIN_VALUE;
	}

	/**
	 * Feed in a {@code StatChanged}. Returns true when this is a real gain for a tracked skill
	 * (and so this tick is a skilling action); false for an untracked skill, the first
	 * (seeding) hit, or a non-positive delta.
	 */
	boolean record(Skill skill, int totalXp, int tick)
	{
		if (skill == null || !TRACKED.contains(skill))
		{
			return false;
		}
		final Integer prev = xp.put(skill, totalXp);
		if (prev == null || totalXp <= prev)
		{
			return false;
		}
		activeSkill = skill;
		activeTick = tick;
		return true;
	}

	/** The skill acted on this tick (or last tick), or {@code null} if none recently. */
	Skill activeSkill(int tick)
	{
		return activeSkill != null && tick - activeTick <= WINDOW_TICKS && tick >= activeTick
			? activeSkill : null;
	}
}
