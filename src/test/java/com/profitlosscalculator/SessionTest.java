/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.time.Instant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionTest
{
	private static Session targetedSession(String... mobs)
	{
		final Session s = new Session(Instant.now());
		s.setMode(Session.RunMode.TARGETED);
		for (String mob : mobs)
		{
			s.addTargetMob(mob);
		}
		return s;
	}

	@Test
	public void addTargetMobDeduplicatesCaseInsensitively()
	{
		final Session s = targetedSession("Cow", "cow", "COW", "Chicken");

		assertEquals(2, s.getTargetMobs().size());
		// first-seen casing is kept
		assertEquals("Cow", s.getTargetMobs().get(0));
		assertEquals("Chicken", s.getTargetMobs().get(1));
	}

	@Test
	public void addTargetMobIgnoresBlankAndNull()
	{
		final Session s = targetedSession("Cow");
		s.addTargetMob(null);
		s.addTargetMob("   ");
		s.addTargetMob("");

		assertEquals(1, s.getTargetMobs().size());
	}

	@Test
	public void addTargetMobTrimsWhitespace()
	{
		final Session s = targetedSession();
		s.addTargetMob("  Brutus  ");

		assertEquals("Brutus", s.getTargetMobs().get(0));
	}

	@Test
	public void matchesTargetChecksEveryMobInTheGroup()
	{
		final Session s = targetedSession("Cow", "Chicken", "Brutus");

		assertTrue(s.matchesTarget("cow", null));
		assertTrue(s.matchesTarget("CHICKEN", null));
		assertTrue(s.matchesTarget("Brutus", null));
		assertFalse(s.matchesTarget("Giant rat", null));
	}

	@Test
	public void plainSessionNeverMatchesATarget()
	{
		final Session s = new Session(Instant.now());
		assertFalse(s.matchesTarget("Cow", null));
	}
}
