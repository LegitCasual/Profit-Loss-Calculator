/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Prices a bag of looted items. Pure - the plugin passes in a {@link PriceLookup} backed by
 * {@code ItemManager}; unit tests pass a fake.
 *
 * <p>Three user-selectable bases (see {@link SessionCostTrackerConfig#incomeValuation()}):
 * live GE price, High Alchemy value, or the higher of the two. Coins and platinum tokens
 * are always taken at face value regardless of the mode.
 */
public final class IncomeValuation
{
	private IncomeValuation()
	{
	}

	public enum Mode
	{
		GE("GE price"),
		HIGH_ALCH("High alch value"),
		HIGHEST("Higher of GE / alch");

		private final String label;

		Mode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** Item id -&gt; price, in the two flavours we might want. Backed by {@code ItemManager}. */
	interface PriceLookup
	{
		int ge(int itemId);

		int ha(int itemId);
	}

	static long value(int itemId, int quantity, Mode mode, PriceLookup prices)
	{
		if (quantity <= 0)
		{
			return 0L;
		}
		if (itemId == ItemID.COINS)
		{
			return quantity;
		}
		if (itemId == ItemID.PLATINUM)
		{
			return quantity * 1000L;
		}

		final long ge = Math.max(0, prices.ge(itemId));
		final long ha = Math.max(0, prices.ha(itemId));
		final long unit;
		switch (mode)
		{
			case HIGH_ALCH:
				unit = ha;
				break;
			case HIGHEST:
				unit = Math.max(ge, ha);
				break;
			case GE:
			default:
				unit = ge;
				break;
		}
		return unit * quantity;
	}

	static long value(Map<Integer, Integer> items, Mode mode, PriceLookup prices)
	{
		long total = 0L;
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			total += value(e.getKey(), e.getValue(), mode, prices);
		}
		return total;
	}
}
