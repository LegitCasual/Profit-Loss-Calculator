/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RuneSpendTest
{
	private static Map<Integer, Integer> lost(int... idQty)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put(idQty[i], idQty[i + 1]);
		}
		return m;
	}

	@Test
	public void exactRunesConfirmAndAreClaimed()
	{
		final Map<Integer, Integer> losses = lost(ItemID.EARTHRUNE, 10, ItemID.ASTRALRUNE, 4, ItemID.DEATHRUNE, 2);
		assertTrue(RuneSpend.claim(Spell.VENGEANCE.getRunes(), losses));
		assertTrue(losses.isEmpty());
	}

	@Test
	public void aMissingRuneTypeIsNotACast()
	{
		// a refused Vengeance next to something else spending earth + death: no astrals left
		final Map<Integer, Integer> losses = lost(ItemID.EARTHRUNE, 10, ItemID.DEATHRUNE, 2);
		assertFalse(RuneSpend.claim(Spell.VENGEANCE.getRunes(), losses));
		assertEquals(lost(ItemID.EARTHRUNE, 10, ItemID.DEATHRUNE, 2), losses);
	}

	@Test
	public void nothingLostIsNotACast()
	{
		assertFalse(RuneSpend.claim(Spell.RESURRECT_GREATER_GHOST.getRunes(), new HashMap<>()));
	}

	@Test
	public void claimingLeavesTheRestForTheNextCast()
	{
		// Vengeance + Ice Barrage on one tick: 6 death left, 2 are Vengeance's
		final Map<Integer, Integer> losses = lost(ItemID.EARTHRUNE, 10, ItemID.ASTRALRUNE, 4,
			ItemID.DEATHRUNE, 6, ItemID.WATERRUNE, 6, ItemID.BLOODRUNE, 2);
		assertTrue(RuneSpend.claim(Spell.VENGEANCE.getRunes(), losses));
		assertEquals(lost(ItemID.DEATHRUNE, 4, ItemID.WATERRUNE, 6, ItemID.BLOODRUNE, 2), losses);
		assertTrue(RuneSpend.claim(Spell.ICE_BARRAGE.getRunes(), losses));
		assertTrue(losses.isEmpty());
	}

	@Test
	public void oneCombinationRunePaysForBothItsRunes()
	{
		// Earth Blast: 4 earth + 3 air + 1 death, paid with 4 dust
		final Map<Integer, Integer> losses = lost(ItemID.DUSTRUNE, 4, ItemID.DEATHRUNE, 1);
		assertTrue(RuneSpend.claim(Spell.EARTH_BLAST.getRunes(), losses));
		assertTrue(losses.isEmpty());
	}

	@Test
	public void combinationRunesTopUpPlainOnes()
	{
		// Fire Wave: 7 fire + 5 air + 1 blood; 2 fire runes + 5 smoke runes (smoke = air + fire)
		final Map<Integer, Integer> losses = lost(ItemID.FIRERUNE, 2, ItemID.SMOKERUNE, 5, ItemID.BLOODRUNE, 1);
		assertTrue(RuneSpend.claim(Spell.FIRE_WAVE.getRunes(), losses));
		assertTrue(losses.isEmpty());
	}

	@Test
	public void aetherRunesStandInForSoulAndCosmic()
	{
		// Mark of Darkness: 1 cosmic + 1 soul, both from one aether rune
		final Map<Integer, Integer> losses = lost(ItemID.AETHERRUNE, 1);
		assertTrue(RuneSpend.claim(Spell.MARK_OF_DARKNESS.getRunes(), losses));
		assertTrue(losses.isEmpty());
	}

	@Test
	public void unrelatedLossesAreLeftAlone()
	{
		final Map<Integer, Integer> losses = lost(ItemID.FIRERUNE, 5, ItemID.NATURERUNE, 1, ItemID.RUNE_PLATEBODY, 1);
		assertTrue(RuneSpend.claim(Spell.HIGH_ALCHEMY.getRunes(), losses));
		assertEquals(lost(ItemID.RUNE_PLATEBODY, 1), losses);
	}
}
