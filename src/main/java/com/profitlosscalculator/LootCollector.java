/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

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

	/** Loot the player dropped back on the floor - taken out of its event's collected so it
	 *  stops counting (and reads as missed), restored if it's picked back up. */
	private static final class Dropped
	{
		private final IncomeEvent event;
		private final int id;
		private int qty;

		private Dropped(IncomeEvent event, int id, int qty)
		{
			this.event = event;
			this.id = id;
			this.qty = qty;
		}
	}

	private final List<Open> open = new ArrayList<>();
	private final List<Dropped> dropped = new ArrayList<>();

	void clear()
	{
		open.clear();
		dropped.clear();
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

	/**
	 * The player dropped {@code qty} of {@code id}: take it back out of the collected loot in
	 * {@code income}, newest event first, so it no longer counts toward net. Skilling output
	 * and alch coins aren't loot and are left alone.
	 *
	 * @return how many were un-collected - the rest was the player's own stuff
	 */
	int uncollect(List<IncomeEvent> income, int id, int qty)
	{
		int left = qty;
		for (int i = income.size() - 1; i >= 0 && left > 0; i--)
		{
			final IncomeEvent e = income.get(i);
			if (e.getType() == IncomeEvent.Type.SKILLING || e.getType() == IncomeEvent.Type.ALCH)
			{
				continue;
			}
			final int took = e.uncollect(id, left);
			if (took > 0)
			{
				dropped.add(new Dropped(e, id, took));
				left -= took;
			}
		}
		return qty - left;
	}

	/**
	 * Re-credit loot the player dropped and has now picked back up, most recently dropped
	 * first. Mutates {@code gains} down as it consumes them.
	 *
	 * @return true if anything was restored
	 */
	boolean recollect(Map<Integer, Integer> gains)
	{
		boolean moved = false;
		for (int i = dropped.size() - 1; i >= 0; i--)
		{
			final Dropped d = dropped.get(i);
			final int have = gains.getOrDefault(d.id, 0);
			if (have <= 0)
			{
				continue;
			}
			final int took = d.event.collect(d.id, Math.min(have, d.qty));
			if (took > 0)
			{
				gains.put(d.id, have - took);
				d.qty -= took;
				moved = true;
			}
		}
		dropped.removeIf(d -> d.qty <= 0);
		gains.values().removeIf(v -> v <= 0);
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
