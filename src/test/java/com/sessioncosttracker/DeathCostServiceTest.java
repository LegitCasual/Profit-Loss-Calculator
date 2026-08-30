/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DeathCostServiceTest
{
	@Test
	public void perItemFeeTiers()
	{
		assertEquals(0, DeathCostService.perItemFee(99_999));
		assertEquals(1_000, DeathCostService.perItemFee(100_000));
		assertEquals(1_000, DeathCostService.perItemFee(999_999));
		assertEquals(10_000, DeathCostService.perItemFee(1_000_000));
		assertEquals(10_000, DeathCostService.perItemFee(9_999_999));
		assertEquals(100_000, DeathCostService.perItemFee(10_000_000));
	}

	@Test
	public void lostDiffFindsMissingAndReducedStacks()
	{
		Map<Integer, Integer> before = new HashMap<>();
		before.put(995, 200_000);   // coins
		before.put(4151, 1);        // whip
		before.put(561, 500);       // nature runes

		Map<Integer, Integer> after = new HashMap<>();
		after.put(995, 200_000);    // kept coins
		after.put(561, 120);        // kept 3 items -> most natures dropped

		Map<Integer, Integer> lost = ContainerSnapshot.lost(before, after);
		assertEquals(2, lost.size());
		assertEquals(Integer.valueOf(1), lost.get(4151));
		assertEquals(Integer.valueOf(380), lost.get(561));
		assertFalse(lost.containsKey(995));
	}

	@Test
	public void coversDetectsItemReturn()
	{
		Map<Integer, Integer> lost = new HashMap<>();
		lost.put(4151, 1);
		lost.put(561, 380);

		Map<Integer, Integer> stillMissing = new HashMap<>();
		stillMissing.put(561, 120);
		assertFalse(ContainerSnapshot.covers(stillMissing, lost));

		Map<Integer, Integer> allBack = new HashMap<>();
		allBack.put(4151, 1);
		allBack.put(561, 500);
		assertTrue(ContainerSnapshot.covers(allBack, lost));
	}
}
