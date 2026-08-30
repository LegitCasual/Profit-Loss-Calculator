/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;

/**
 * Values a set of lost items and estimates the Item Retrieval Service fee.
 *
 * <p>When the player actually reclaims, the real fee is read from the
 * "Payment has been taken from your ...: N x Coins" chat line - this estimate is only the
 * pre-fill / the fallback when that line is missed. Current (2024+) gravestone tiers:
 *
 * <ul>
 *     <li>item worth &lt; 100k: free</li>
 *     <li>100k - 1m: 1,000 per item</li>
 *     <li>1m - 10m: 10,000 per item</li>
 *     <li>10m+: 100,000 per item</li>
 * </ul>
 *
 * total capped at 500,000; ironmen pay half.
 */
class DeathCostService
{
	static final long FEE_CAP = 500_000L;

	private final ItemManager itemManager;

	@Inject
	DeathCostService(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/** Full GE value of everything lost (the worst case - never reclaimed). */
	long geValue(Map<Integer, Integer> lostItems)
	{
		long total = 0;
		for (Map.Entry<Integer, Integer> e : lostItems.entrySet())
		{
			total += (long) Math.max(0, itemManager.getItemPrice(e.getKey())) * e.getValue();
		}
		return total;
	}

	/** Gravestone reclaim fee estimate for these items. */
	long graveFeeEstimate(Map<Integer, Integer> lostItems, boolean ironman)
	{
		long total = 0;
		for (Map.Entry<Integer, Integer> e : lostItems.entrySet())
		{
			final long unit = Math.max(0, itemManager.getItemPrice(e.getKey()));
			final int qty = e.getValue();
			if (unit >= 100_000)
			{
				total += perItemFee(unit) * qty;
			}
			else if (unit * (long) qty >= 100_000)
			{
				// a single valuable stack (e.g. thousands of runes)
				total += perItemFee(unit * (long) qty);
			}
		}
		total = Math.min(FEE_CAP, total);
		return ironman ? total / 2 : total;
	}

	/** Pure: the per-item fee tier for a given item value. */
	static long perItemFee(long value)
	{
		if (value < 100_000L)
		{
			return 0L;
		}
		if (value < 1_000_000L)
		{
			return 1_000L;
		}
		if (value < 10_000_000L)
		{
			return 10_000L;
		}
		return 100_000L;
	}
}
