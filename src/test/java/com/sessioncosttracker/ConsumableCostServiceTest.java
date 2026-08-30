/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ConsumableCostServiceTest
{
	@Test
	public void sipIsTheDropFromCurrentDoseToOneLower()
	{
		// Prayer potion(4) 9,000 -> (3) 6,800: one sip destroys 2,200
		assertEquals(2_200, ConsumableCostService.sipCost(4, 9_000, 6_800, 5));
		// (3) 6,800 -> (2) 4,500
		assertEquals(2_300, ConsumableCostService.sipCost(3, 6_800, 4_500, 5));
	}

	@Test
	public void lastSipLeavesAnEmptyVial()
	{
		// (1) 2,300 -> empty vial 5
		assertEquals(2_295, ConsumableCostService.sipCost(1, 2_300, -1, 5));
	}

	@Test
	public void fallsBackToEvenSplitWhenLowerDoseUnpriced()
	{
		assertEquals(2_250, ConsumableCostService.sipCost(4, 9_000, -1, 5));
		assertEquals(2_267, ConsumableCostService.sipCost(3, 6_800, -1, 5));
	}

	@Test
	public void neverNegativeAndZeroWhenUnpriced()
	{
		assertEquals(0, ConsumableCostService.sipCost(4, 0, 6_800, 5));
		// lower-dose price above the full price (thin market) clamps at 0 rather than going negative
		assertEquals(0, ConsumableCostService.sipCost(2, 100, 4_500, 5));
	}
}
