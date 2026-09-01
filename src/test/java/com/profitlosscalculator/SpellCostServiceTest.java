/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SpellCostServiceTest
{
	/** flat per-rune price table keyed by rune item id */
	private final Map<Integer, Integer> prices = new HashMap<>();
	private final IntUnaryOperator ge = id -> prices.getOrDefault(id, 0);

	private void price(Rune rune, int gp)
	{
		prices.put(rune.getItemId(), gp);
	}

	@Test
	public void highAlchemyCostsFireAndNature()
	{
		price(Rune.FIRE, 5);
		price(Rune.NATURE, 200);
		SpellCostService.Priced p = SpellCostService.price(Spell.HIGH_ALCHEMY, EnumSet.noneOf(Rune.class), ge);
		// 5 fire * 5 + 1 nature * 200
		assertEquals(225, p.getGp());
		assertSame(Spell.HIGH_ALCHEMY, p.getSpell());
	}

	@Test
	public void fireStaffRemovesFireRuneFromFireBolt()
	{
		price(Rune.FIRE, 5);
		price(Rune.AIR, 4);
		price(Rune.CHAOS, 90);
		Set<Rune> fireStaff = Staff.FIRE.getRunes();
		SpellCostService.Priced p = SpellCostService.price(Spell.FIRE_BOLT, fireStaff, ge);
		// fire free; 3 air * 4 + 1 chaos * 90
		assertEquals(102, p.getGp());
		assertTrue(p.getBreakdown().containsKey("Fire (staff)"));
		// staff-provided fire is not in the consumed-runes map
		assertNull(p.getRunesUsed().get(Rune.FIRE));
		assertEquals(Integer.valueOf(3), p.getRunesUsed().get(Rune.AIR));
		assertEquals(Integer.valueOf(1), p.getRunesUsed().get(Rune.CHAOS));
	}

	@Test
	public void mudStaffCoversBothWaterAndEarth()
	{
		price(Rune.WATER, 5);
		price(Rune.EARTH, 5);
		price(Rune.NATURE, 100);
		SpellCostService.Priced p = SpellCostService.price(Spell.BONES_TO_BANANAS, Staff.MUD.getRunes(), ge);
		// earth + water free, 1 nature * 100
		assertEquals(100, p.getGp());
	}

	@Test
	public void windStrikeWithAirStaffChargesOnlyMind()
	{
		price(Rune.AIR, 5);
		price(Rune.MIND, 12);
		SpellCostService.Priced p = SpellCostService.price(Spell.WIND_STRIKE, Staff.AIR.getRunes(), ge);
		assertEquals(12, p.getGp());
	}

	@Test
	public void staffLookupByItemId()
	{
		assertSame(Staff.AIR, Staff.byItemId(Staff.AIR.getItemIds()[0]));
		assertNull(Staff.byItemId(995)); // coins
		assertNull(Staff.byItemId(-1));
	}

	@Test
	public void unknownComponentIsNotASpell()
	{
		assertNull(Spell.byComponent(0));
		assertNull(Spell.byComponent(123456));
	}

	@Test
	public void everySpellComponentIsUnique()
	{
		Map<Integer, Spell> seen = new HashMap<>();
		for (Spell s : Spell.values())
		{
			Spell prev = seen.put(s.getComponentId(), s);
			assertNull("duplicate component for " + s + " / " + prev, prev);
			assertTrue("empty rune table for " + s, !s.getRunes().isEmpty());
		}
	}
}
