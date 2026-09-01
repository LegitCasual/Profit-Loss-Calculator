/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TeleportChargesTest
{
	// Amulet of glory variation group
	private static final int GLORY4 = ItemID.AMULET_OF_GLORY_4;
	private static final int GLORY3 = 1710;
	private static final int GLORY1 = 1706;
	private static final int GLORY0 = ItemID.AMULET_OF_GLORY; // uncharged
	private static final int DUEL1 = ItemID.RING_OF_DUELING_1; // ring of dueling(1)

	private final Map<Integer, Integer> charges = new HashMap<>();

	{
		charges.put(GLORY4, 4);
		charges.put(GLORY3, 3);
		charges.put(1708, 2);
		charges.put(GLORY1, 1);
		charges.put(GLORY0, 0);
		charges.put(DUEL1, 1);
	}

	private static Map<Integer, Integer> held(int... ids)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int id : ids)
		{
			m.merge(id, 1, Integer::sum);
		}
		return m;
	}

	@Test
	public void watchesTheExpectedGroups()
	{
		assertTrue(TeleportCharges.variantIds().contains(GLORY4));
		assertTrue(TeleportCharges.variantIds().contains(ItemID.RING_OF_DUELING_8));
	}

	@Test
	public void oneTeleportDropsOneTier()
	{
		final List<TeleportCharges.Charge> r =
			TeleportCharges.detect(held(GLORY4), held(GLORY3), charges);
		assertEquals(1, r.size());
		assertEquals(GLORY4, r.get(0).getFromId());
		assertEquals(GLORY3, r.get(0).getToId());
		assertEquals(1, r.get(0).getChargesUsed());
	}

	@Test
	public void lastChargeConsumesTheItem()
	{
		final List<TeleportCharges.Charge> r =
			TeleportCharges.detect(held(DUEL1), new HashMap<>(), charges);
		assertEquals(1, r.size());
		assertEquals(DUEL1, r.get(0).getFromId());
		assertEquals(-1, r.get(0).getToId());
	}

	@Test
	public void equippingOrBankingIsNotATeleport()
	{
		// no change
		assertTrue(TeleportCharges.detect(held(GLORY4), held(GLORY4), charges).isEmpty());
		// glory gained (withdrew one) - not a teleport
		assertTrue(TeleportCharges.detect(held(GLORY4), held(GLORY4, GLORY3), charges).isEmpty());
	}
}
