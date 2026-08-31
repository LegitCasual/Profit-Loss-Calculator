/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PickupTrackerTest
{
	private static final int NATURE_RUNE = 561;
	private static final int SHARK = 385;
	private static final int BRONZE_ARROW = 882;

	private static Map<Integer, Integer> gains(Object... kv)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((Integer) kv[i], (Integer) kv[i + 1]);
		}
		return m;
	}

	private static Set<Integer> set(Integer... ids)
	{
		return new HashSet<>(java.util.Arrays.asList(ids));
	}

	@Test
	public void creditsAGainThatFollowsATakeClick()
	{
		final PickupTracker t = new PickupTracker();
		t.intend(NATURE_RUNE, 100);

		final Map<Integer, Integer> out = t.collect(gains(NATURE_RUNE, 45), 101, Collections.emptySet());
		assertEquals(Integer.valueOf(45), out.get(NATURE_RUNE));
	}

	@Test
	public void ignoresGainsWithNoMatchingIntent()
	{
		final PickupTracker t = new PickupTracker();
		t.intend(NATURE_RUNE, 100);

		// a shark appeared (ate nothing, banked nothing) but was never clicked to take
		assertTrue(t.collect(gains(SHARK, 3), 101, Collections.emptySet()).isEmpty());
	}

	@Test
	public void intentExpiresAfterItsWindow()
	{
		final PickupTracker t = new PickupTracker();
		t.intend(NATURE_RUNE, 100);

		final int tooLate = 100 + PickupTracker.INTENT_TTL_TICKS + 1;
		assertTrue(t.collect(gains(NATURE_RUNE, 45), tooLate, Collections.emptySet()).isEmpty());
	}

	@Test
	public void intentIsConsumedSoTheSameGainIsNotCountedTwice()
	{
		final PickupTracker t = new PickupTracker();
		t.intend(NATURE_RUNE, 100);

		assertEquals(1, t.collect(gains(NATURE_RUNE, 45), 101, Collections.emptySet()).size());
		// next tick the stack merges again (noted<->unnoted, whatever) - not a second pickup
		assertTrue(t.collect(gains(NATURE_RUNE, 45), 102, Collections.emptySet()).isEmpty());
	}

	@Test
	public void excludedAmmoIsLeftForTheAmmoTracker()
	{
		final PickupTracker t = new PickupTracker();
		t.intend(BRONZE_ARROW, 100);

		assertTrue(t.collect(gains(BRONZE_ARROW, 30), 101, set(BRONZE_ARROW)).isEmpty());
	}
}
