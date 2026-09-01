/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Correlates a "Take" (or Telekinetic Grab) click with the inventory gain that follows,
 * so only items the player actually deliberately picked up off the ground are credited as
 * income - not bank withdrawals, un-notes, container emptying, or teleport-charge downgrades.
 *
 * <p>The plugin records an <em>intent</em> the moment a ground item is clicked, then feeds
 * each tick's inventory gains back in. A gain is credited only if it matches a recent
 * intent and is not otherwise spoken for (tracked ammo the ammo tracker owns, or an item
 * a loot event already counted). Pure and unit-testable.
 */
class PickupTracker
{
	/** How long a "Take" click stays live waiting for the item to arrive. */
	static final int INTENT_TTL_TICKS = 12;

	/** itemId -&gt; tick of the most recent Take/telegrab click on that item. */
	private final Map<Integer, Integer> intentTick = new HashMap<>();

	void reset()
	{
		intentTick.clear();
	}

	/** A ground item was clicked to be picked up (or telegrabbed). */
	void intend(int itemId, int tick)
	{
		if (itemId > 0)
		{
			intentTick.put(itemId, tick);
		}
	}

	/**
	 * @param gains    itemId -&gt; quantity gained across inventory + equipment this tick,
	 *                 already stripped of anything a loot event accounted for
	 * @param tick     current game tick
	 * @param excluded ids never credited here (ammo the ammo tracker already reconciles)
	 * @return the subset of {@code gains} to credit as a ground pickup; intents consumed
	 */
	Map<Integer, Integer> collect(Map<Integer, Integer> gains, int tick, Set<Integer> excluded)
	{
		final Map<Integer, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : gains.entrySet())
		{
			final int id = e.getKey();
			final int qty = e.getValue();
			if (qty <= 0 || excluded.contains(id))
			{
				continue;
			}
			final Integer intent = intentTick.get(id);
			if (intent == null || tick - intent > INTENT_TTL_TICKS)
			{
				continue;
			}
			out.put(id, qty);
			intentTick.remove(id);
		}
		intentTick.values().removeIf(t -> tick - t > INTENT_TTL_TICKS);
		return out;
	}
}
