/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Skill;
import org.junit.Test;

public class SkillingTrackerTest
{
	private static Map<Skill, Integer> xp(Skill skill, int value)
	{
		final Map<Skill, Integer> m = new EnumMap<>(Skill.class);
		m.put(skill, value);
		return m;
	}

	@Test
	public void untrackedSkillIsIgnored()
	{
		final SkillingTracker t = new SkillingTracker();
		t.reset(Collections.emptyMap());
		assertFalse(t.record(Skill.ATTACK, 1_000, 10));
		assertFalse(t.record(Skill.MAGIC, 5_000, 10));
		assertNull(t.activeSkill(10));
	}

	@Test
	public void firstHitOnAnUnseededSkillJustSeeds()
	{
		final SkillingTracker t = new SkillingTracker();
		t.reset(Collections.emptyMap());
		// no baseline yet - treated as the seed, not an action
		assertFalse(t.record(Skill.FISHING, 12_000, 5));
		assertNull(t.activeSkill(5));
		// now a real gain counts
		assertTrue(t.record(Skill.FISHING, 12_040, 6));
		assertSame(Skill.FISHING, t.activeSkill(6));
	}

	@Test
	public void positiveDeltaAfterSeedIsAnAction()
	{
		final SkillingTracker t = new SkillingTracker();
		t.reset(xp(Skill.FLETCHING, 500_000));
		assertTrue(t.record(Skill.FLETCHING, 500_058, 42));
		assertSame(Skill.FLETCHING, t.activeSkill(42));
	}

	@Test
	public void equalOrNegativeDeltaIsNotAnAction()
	{
		final SkillingTracker t = new SkillingTracker();
		t.reset(xp(Skill.SMITHING, 100));
		assertFalse(t.record(Skill.SMITHING, 100, 7));   // no change (e.g. a re-emit)
		assertNull(t.activeSkill(7));
	}

	@Test
	public void activeSkillOnlyHoldsForOneTick()
	{
		final SkillingTracker t = new SkillingTracker();
		t.reset(xp(Skill.MINING, 0));
		t.record(Skill.MINING, 35, 100);
		assertSame(Skill.MINING, t.activeSkill(100));   // same tick
		assertSame(Skill.MINING, t.activeSkill(101));   // next tick still counts
		assertNull(t.activeSkill(102));                 // gone
		assertNull(t.activeSkill(99));                  // never in the past
	}

	@Test
	public void resetReSeedsAndClearsActive()
	{
		final SkillingTracker t = new SkillingTracker();
		t.reset(xp(Skill.COOKING, 1_000));
		t.record(Skill.COOKING, 1_030, 3);
		assertSame(Skill.COOKING, t.activeSkill(3));

		t.reset(xp(Skill.COOKING, 1_030));
		assertNull(t.activeSkill(3));
		// the storm of StatChanged after a relogin matches the fresh baseline -> no action
		assertFalse(t.record(Skill.COOKING, 1_030, 4));
	}
}
