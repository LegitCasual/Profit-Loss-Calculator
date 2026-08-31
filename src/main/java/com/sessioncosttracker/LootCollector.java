/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decides how much of what dropped actually made it into the bag. Every monster / PvP loot
 * event is registered here; each tick the plugin feeds in the inventory gains and this
 * attributes them, oldest drop first, to the things that dropped - capped so nothing is
 * counted twice.
 *
 * <p>A fresh gain is credited automatically (you walked over the pile). A gain of an item
 * that dropped a while ago is only credited when the plugin says it was a deliberate "Take"
 * ({@link #collectViaClick}). Drops older than {@link #MAX_AGE_TICKS} are written off as
 * "potential only". Pure and unit-testable.
 */
class LootCollector
{
	/** An inventory gain within this many ticks of a drop is assumed to be that loot. */
	static final int COLLECT_WINDOW_TICKS = 60;

	/** After this, an uncollected drop is potential-only and no longer auto-matched. */
	static final int MAX_AGE_TICKS = 500;

	private static final class Open
	{
		private final IncomeEvent event;
		private final int tick;

		private Open(IncomeEvent event, int tick)
		{
			this.event = event;
			this.tick = tick;
		}
	}

	private final List<Open> open = new ArrayList<>();

	void clear()
	{
		open.clear();
	}

	/** Register a monster / PvP loot drop to watch for pickups. */
	void add(IncomeEvent drop, int tick)
	{
		open.add(new Open(drop, tick));
	}

	/**
	 * Attribute this tick's inventory gains to recent drops - no click needed, you walked
	 * over the pile. Mutates {@code gains} down as it consumes them.
	 *
	 * @return true if anything was attributed
	 */
	boolean correlate(Map<Integer, Integer> gains, int tick)
	{
		return apply(gains, tick, true);
	}

	/**
	 * Attribute deliberately taken items to any still-open drop regardless of age - the
	 * player clicked Take on something that dropped a while back.
	 *
	 * @return true if anything was attributed
	 */
	boolean collectViaClick(Map<Integer, Integer> picked)
	{
		return apply(picked, 0, false);
	}

	private boolean apply(Map<Integer, Integer> src, int tick, boolean respectWindow)
	{
		boolean moved = false;
		for (Open o : open)
		{
			if (respectWindow && tick - o.tick > COLLECT_WINDOW_TICKS)
			{
				continue;
			}
			for (Map.Entry<Integer, Integer> g : src.entrySet())
			{
				final int have = g.getValue();
				if (have <= 0)
				{
					continue;
				}
				final int took = o.event.collect(g.getKey(), have);
				if (took > 0)
				{
					g.setValue(have - took);
					moved = true;
				}
			}
		}
		src.values().removeIf(v -> v <= 0);
		open.removeIf(o -> o.event.fullyCollected());
		return moved;
	}

	void expire(int tick)
	{
		open.removeIf(o -> tick - o.tick > MAX_AGE_TICKS);
	}

	int openDrops()
	{
		return open.size();
	}
}
