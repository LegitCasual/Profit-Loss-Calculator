/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
	public void firedRecoveredAndNet()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 1000));

		assertTrue(t.reconcile(owned(ARROW, 940), true));   // fired 60
		assertTrue(t.reconcile(owned(ARROW, 955), true));   // picked 15 back up
		assertArrayEquals(new long[]{60, 15, 45}, t.stats().get(ARROW));
	}

	@Test
	public void aRiseBeyondWhatWasFiredIsARestockNotRecovery()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 100));

		t.reconcile(owned(ARROW, 90), true);          // fired 10
		t.reconcile(owned(ARROW, 5000), true);        // bought a pile
		// recovery capped at the 10 that were out there; net floors at 0
		assertArrayEquals(new long[]{10, 10, 0}, t.stats().get(ARROW));
		// and firing continues from the new baseline
		t.reconcile(owned(ARROW, 4990), true);
		assertArrayEquals(new long[]{20, 10, 10}, t.stats().get(ARROW));
	}

	@Test
	public void deathWindowOnlyReBases()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 500));

		assertFalse(t.reconcile(owned(), false));            // lost the lot to a death
		assertFalse(t.reconcile(owned(ARROW, 500), false));  // reclaimed
		assertNull(t.stats().get(ARROW));
		assertTrue(t.reconcile(owned(ARROW, 498), true));    // normal firing resumes
		assertArrayEquals(new long[]{2, 0, 2}, t.stats().get(ARROW));
	}

	@Test
	public void tracksSeveralAmmoTypes()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 200, BALL, 30));

		t.reconcile(owned(ARROW, 195, BALL, 24), true);
		assertArrayEquals(new long[]{5, 0, 5}, t.stats().get(ARROW));
		assertArrayEquals(new long[]{6, 0, 6}, t.stats().get(BALL));
	}

	@Test
	public void resetClearsTheTally()
	{
		final AmmoTracker t = new AmmoTracker();
		t.reset(owned(ARROW, 100));
		t.reconcile(owned(ARROW, 80), true);
		t.reset(owned(ARROW, 80));
		assertTrue(t.stats().isEmpty());
	}
}
