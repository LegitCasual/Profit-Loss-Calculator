/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LootCollectorTest
{
	private static final int WHIP = 4151;
	private static final int SHARK = 385;
	private static final int COINS = 995;

	private static IncomeEvent drop(Object... kv)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((Integer) kv[i], (Integer) kv[i + 1]);
		}
		return new IncomeEvent(IncomeEvent.Type.NPC_LOOT, Instant.now(), "Vorkath", m);
	}

	private static Map<Integer, Integer> gains(Object... kv)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((Integer) kv[i], (Integer) kv[i + 1]);
		}
		return m;
	}

	@Test
	public void freshDropStartsUncollected()
	{
		final IncomeEvent d = drop(WHIP, 1, COINS, 20_000);
		assertEquals(1, d.outstanding(WHIP));
		assertEquals(20_000, d.outstanding(COINS));
		assertFalse(d.fullyCollected());
		assertTrue(d.getCollected().isEmpty());
	}

	@Test
	public void aFreshGainIsCreditedToTheDropAndConsumed()
	{
		final LootCollector lc = new LootCollector();
		final IncomeEvent d = drop(WHIP, 1, COINS, 20_000);
		lc.add(d, 100);

		final Map<Integer, Integer> g = gains(WHIP, 1, COINS, 20_000);
		assertTrue(lc.correlate(g, 103));

		assertTrue(g.isEmpty());
		assertTrue(d.fullyCollected());
		assertEquals(Integer.valueOf(1), d.getCollected().get(WHIP));
		assertEquals(0, lc.openDrops());   // fully collected -> dropped from the watch list
	}

	@Test
	public void aGainBeyondWhatDroppedIsCappedLeftoverRemains()
	{
		final LootCollector lc = new LootCollector();
		final IncomeEvent d = drop(COINS, 10_000);
		lc.add(d, 100);

		// alched for 50k in the same window - only the 10k that dropped is attributable
		final Map<Integer, Integer> g = gains(COINS, 60_000);
		lc.correlate(g, 105);

		assertEquals(10_000, d.getCollected().get(COINS).intValue());
		assertEquals(Integer.valueOf(50_000), g.get(COINS));
	}

	@Test
	public void anOldGainIsNotAutoCreditedButAClickIs()
	{
		final LootCollector lc = new LootCollector();
		final IncomeEvent d = drop(SHARK, 3);
		lc.add(d, 100);

		final int late = 100 + LootCollector.COLLECT_WINDOW_TICKS + 5;
		final Map<Integer, Integer> auto = gains(SHARK, 3);
		assertFalse(lc.correlate(auto, late));
		assertEquals(Integer.valueOf(3), auto.get(SHARK));   // untouched

		// but a deliberate Take reconciles against the stale drop
		final Map<Integer, Integer> clicked = gains(SHARK, 3);
		assertTrue(lc.collectViaClick(clicked));
		assertTrue(clicked.isEmpty());
		assertTrue(d.fullyCollected());
	}

	@Test
	public void oldestDropIsFilledFirst()
	{
		final LootCollector lc = new LootCollector();
		final IncomeEvent first = drop(SHARK, 2);
		final IncomeEvent second = drop(SHARK, 2);
		lc.add(first, 100);
		lc.add(second, 104);

		lc.correlate(gains(SHARK, 3), 106);
		assertEquals(2, first.getCollected().get(SHARK).intValue());
		assertEquals(1, second.getCollected().get(SHARK).intValue());
	}

	@Test
	public void expireDropsStaleDrops()
	{
		final LootCollector lc = new LootCollector();
		lc.add(drop(WHIP, 1), 100);
		lc.expire(100 + LootCollector.MAX_AGE_TICKS + 1);
		assertEquals(0, lc.openDrops());
	}

	@Test
	public void clearForgetsEverything()
	{
		final LootCollector lc = new LootCollector();
		lc.add(drop(WHIP, 1), 100);
		lc.clear();
		assertEquals(0, lc.openDrops());
	}
}
