/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class IncomeValuationTest
{
	private static final int WHIP = 4151;
	private static final int RUNE_PLATEBODY = 1127;   // GE > HA
	private static final int GILDED_SCIMITAR = 12389; // HA > GE (alch fodder)
	private static final int COINS = 995;
	private static final int PLATINUM = 13204;
	private static final int UNTRADEABLE = 99999;

	private static final IncomeValuation.PriceLookup PRICES = new IncomeValuation.PriceLookup()
	{
		private final Map<Integer, int[]> table = new HashMap<>();

		{
			table.put(WHIP, new int[]{2_000_000, 72_000});
			table.put(RUNE_PLATEBODY, new int[]{38_000, 39_000});
			table.put(GILDED_SCIMITAR, new int[]{5_000, 1_920_000});
			table.put(UNTRADEABLE, new int[]{0, 0});
		}

		@Override
		public int ge(int itemId)
		{
			return table.getOrDefault(itemId, new int[]{0, 0})[0];
		}

		@Override
		public int ha(int itemId)
		{
			return table.getOrDefault(itemId, new int[]{0, 0})[1];
		}
	};

	@Test
	public void geMode()
	{
		assertEquals(2_000_000, IncomeValuation.value(WHIP, 1, IncomeValuation.Mode.GE, PRICES));
		assertEquals(10_000, IncomeValuation.value(GILDED_SCIMITAR, 2, IncomeValuation.Mode.GE, PRICES));
	}

	@Test
	public void highAlchMode()
	{
		assertEquals(72_000, IncomeValuation.value(WHIP, 1, IncomeValuation.Mode.HIGH_ALCH, PRICES));
		assertEquals(1_920_000, IncomeValuation.value(GILDED_SCIMITAR, 1, IncomeValuation.Mode.HIGH_ALCH, PRICES));
	}

	@Test
	public void highestMode()
	{
		// whip: GE wins
		assertEquals(2_000_000, IncomeValuation.value(WHIP, 1, IncomeValuation.Mode.HIGHEST, PRICES));
		// gilded scimitar: alch wins
		assertEquals(1_920_000, IncomeValuation.value(GILDED_SCIMITAR, 1, IncomeValuation.Mode.HIGHEST, PRICES));
		// rune platebody: alch just edges it
		assertEquals(39_000, IncomeValuation.value(RUNE_PLATEBODY, 1, IncomeValuation.Mode.HIGHEST, PRICES));
	}

	@Test
	public void coinsAndPlatinumAreFaceValueInEveryMode()
	{
		for (IncomeValuation.Mode m : IncomeValuation.Mode.values())
		{
			assertEquals(50_000, IncomeValuation.value(COINS, 50_000, m, PRICES));
			assertEquals(250_000, IncomeValuation.value(PLATINUM, 250, m, PRICES));
		}
	}

	@Test
	public void untradeableWithNoAlchValueIsZero()
	{
		assertEquals(0, IncomeValuation.value(UNTRADEABLE, 5, IncomeValuation.Mode.HIGHEST, PRICES));
	}

	@Test
	public void mapOverloadSumsTheBag()
	{
		final Map<Integer, Integer> bag = new HashMap<>();
		bag.put(WHIP, 1);
		bag.put(COINS, 30_000);
		bag.put(RUNE_PLATEBODY, 2);
		// GE: 2,000,000 + 30,000 + 76,000
		assertEquals(2_106_000, IncomeValuation.value(bag, IncomeValuation.Mode.GE, PRICES));
	}
}
