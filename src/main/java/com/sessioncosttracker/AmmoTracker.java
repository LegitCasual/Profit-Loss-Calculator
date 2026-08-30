/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns "combined owned ammo quantity per item id" snapshots into per-item consumption.
 *
 * <p>The plugin feeds this the total quantity it can see for each ammo-ish item - the
 * equipped ammo/thrown slot, cannon loaded balls, Dizana's quiver - and this reports how
 * much each id dropped since the previous snapshot. A drop is consumption; a rise (picking
 * ammo back up, re-equipping, a shop buy) just re-bases with no cost. Ava's recovery never
 * decrements the slot, so recovered ammo is free automatically.
 *
 * <p>Pure and unit-testable - no RuneLite types.
 */
class AmmoTracker
{
	private final Map<Integer, Long> baseline = new HashMap<>();

	/** Ids currently carrying a baseline - the plugin also totals their inventory copies. */
	Set<Integer> tracked()
	{
		return Collections.unmodifiableSet(baseline.keySet());
	}

	/** Forget everything and treat {@code owned} as the new zero point (trip start / restock). */
	void reset(Map<Integer, Long> owned)
	{
		baseline.clear();
		owned.forEach((id, qty) ->
		{
			if (qty > 0)
			{
				baseline.put(id, qty);
			}
		});
	}

	/**
	 * @param owned  current combined owned quantity per ammo item id
	 * @param accrue when false, only re-base (used while a death is being resolved so the
	 *               death handler owns that loss instead)
	 * @return item id -&gt; units consumed since the last call; empty when nothing dropped
	 *         or {@code accrue} is false
	 */
	Map<Integer, Long> reconcile(Map<Integer, Long> owned, boolean accrue)
	{
		final Map<Integer, Long> consumed = new HashMap<>();

		final Set<Integer> ids = new HashSet<>(baseline.keySet());
		ids.addAll(owned.keySet());

		for (int id : ids)
		{
			final long before = baseline.getOrDefault(id, 0L);
			final long now = Math.max(0L, owned.getOrDefault(id, 0L));

			if (accrue && now < before)
			{
				consumed.put(id, before - now);
			}

			if (now <= 0L)
			{
				baseline.remove(id);
			}
			else
			{
				baseline.put(id, now);
			}
		}

		return consumed;
	}
}
