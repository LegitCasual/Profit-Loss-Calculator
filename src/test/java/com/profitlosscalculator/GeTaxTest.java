/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class GeTaxTest
{
	private static final int ITEM = ItemID.DRAGON_BONES;

	@Test
	public void twoPercentFlooredPerItem()
	{
		assertEquals(20L, GeTax.tax(ITEM, 1_000, 1));      // 2% of 1000
		assertEquals(2_000L, GeTax.tax(ITEM, 1_000, 100)); // per item, times qty
	}

	@Test
	public void nothingUnderFiftyGp()
	{
		assertEquals(0L, GeTax.tax(ITEM, 49, 1_000));
		assertEquals(0L, GeTax.tax(ITEM, 1, 1_000_000));
	}

	@Test
	public void sellerNets98AtPrice99Or100()
	{
		assertEquals(1L, GeTax.tax(ITEM, 99, 1));    // 99 - 1
		assertEquals(2L, GeTax.tax(ITEM, 100, 1));   // 100 - 2  (both leave 98)
	}

	@Test
	public void cappedAtFiveMillionPerOffer()
	{
		// 1M each, 1000 sold -> raw tax 20M, capped
		assertEquals(GeTax.CAP, GeTax.tax(ITEM, 1_000_000, 1_000));
	}

	@Test
	public void bondsAreExempt()
	{
		assertTrue(GeTax.isExempt(ItemID.OSRS_BOND));
		assertEquals(0L, GeTax.tax(ItemID.OSRS_BOND, 8_000_000, 5));
	}

	@Test
	public void zeroOrNegativeInputs()
	{
		assertEquals(0L, GeTax.tax(ITEM, 0, 100));
		assertEquals(0L, GeTax.tax(ITEM, 1_000, 0));
	}
}
