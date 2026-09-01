/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/**
 * Helpers for turning an {@link ItemContainer} into an {@code itemId -> quantity} map and
 * doing multiset arithmetic on those maps. Same idea as the inventory diff in RuneLite's
 * loot tracker ({@code LootTrackerPlugin.onItemContainerChanged}).
 */
final class ContainerSnapshot
{
	private ContainerSnapshot()
	{
	}

	static Map<Integer, Integer> of(ItemContainer container)
	{
		Map<Integer, Integer> map = new HashMap<>();
		if (container == null)
		{
			return map;
		}
		for (Item item : container.getItems())
		{
			int id = item.getId();
			if (id <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			map.merge(id, item.getQuantity(), Integer::sum);
		}
		return map;
	}

	static Map<Integer, Integer> union(Map<Integer, Integer> a, Map<Integer, Integer> b)
	{
		Map<Integer, Integer> out = new HashMap<>(a);
		b.forEach((k, v) -> out.merge(k, v, Integer::sum));
		return out;
	}

	/**
	 * Positive counts present in {@code before} but missing or reduced in {@code after}
	 * (i.e. what was lost going from before to after).
	 */
	static Map<Integer, Integer> lost(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Map<Integer, Integer> out = new HashMap<>();
		before.forEach((id, qty) ->
		{
			int diff = qty - after.getOrDefault(id, 0);
			if (diff > 0)
			{
				out.put(id, diff);
			}
		});
		return out;
	}

	/** True if {@code container} now covers every (id, qty) in {@code needed}. */
	static boolean covers(Map<Integer, Integer> container, Map<Integer, Integer> needed)
	{
		for (Map.Entry<Integer, Integer> e : needed.entrySet())
		{
			if (container.getOrDefault(e.getKey(), 0) < e.getValue())
			{
				return false;
			}
		}
		return true;
	}
}
