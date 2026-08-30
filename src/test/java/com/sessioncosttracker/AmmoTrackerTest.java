/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AmmoTrackerTest
{
	private static final int ARROW = 884;
	private static final int BALL = 2;

	private static Map<Integer, Long> owned(Object... kv)
	{
		final Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((Integer) kv[i], ((Number) kv[i + 1]).longValue());
		}
		return m;
	}

	@Test
	public void countsOnlyTheDrop()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 1000));

		assertEquals(Long.valueOf(3), t.reconcile(owned(ARROW, 997), true).get(ARROW));
		// pick one back up - no charge, just re-base
		assertTrue(t.reconcile(owned(ARROW, 998), true).isEmpty());
		// and it stays re-based: firing from 998 now
		assertEquals(Long.valueOf(8), t.reconcile(owned(ARROW, 990), true).get(ARROW));
	}

	@Test
	public void deathWindowJustReBasesInsteadOfCharging()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 500));

		// died, lost the stack - not our cost to price
		assertTrue(t.reconcile(owned(), false).isEmpty());
		// reclaimed - comes back, still no charge
		assertTrue(t.reconcile(owned(ARROW, 500), false).isEmpty());
		// back to normal firing
		assertEquals(Long.valueOf(2), t.reconcile(owned(ARROW, 498), true).get(ARROW));
	}

	@Test
	public void handlesMultipleAmmoTypesAndFullDepletion()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 200, BALL, 30));

		final Map<Integer, Long> c = t.reconcile(owned(ARROW, 195, BALL, 24), true);
		assertEquals(Long.valueOf(5), c.get(ARROW));
		assertEquals(Long.valueOf(6), c.get(BALL));

		// cannon runs dry
		assertEquals(Long.valueOf(24), t.reconcile(owned(ARROW, 195), true).get(BALL));
		// reloading from the inventory later is a rise, not a refund
		assertTrue(t.reconcile(owned(ARROW, 195, BALL, 30), true).isEmpty());
	}
}
