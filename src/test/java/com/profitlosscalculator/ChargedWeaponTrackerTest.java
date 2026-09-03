/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChargedWeaponTrackerTest
{
	private static final ChargedWeapon BOW = ChargedWeapon.VENATOR_BOW;
	private static final ChargedWeapon SHADOW = ChargedWeapon.TUMEKENS_SHADOW;

	@Test
	public void oneAttackIsOneCharge()
	{
		final ChargedWeaponTracker t = new ChargedWeaponTracker();
		assertTrue(t.recordAttack(BOW, 100));
		assertTrue(t.recordAttack(BOW, 103));
		assertTrue(t.recordAttack(BOW, 106));
		assertEquals(Long.valueOf(3), t.spent().get(BOW));
	}

	@Test
	public void aRepeatWithinTheGapIsDebounced()
	{
		final ChargedWeaponTracker t = new ChargedWeaponTracker();
		assertTrue(t.recordAttack(BOW, 100));
		assertFalse(t.recordAttack(BOW, 100));   // same tick - lingering graphic
		assertFalse(t.recordAttack(BOW, 101));   // still within MIN_ATTACK_GAP_TICKS
		assertTrue(t.recordAttack(BOW, 102));    // next real attack
		assertEquals(Long.valueOf(2), t.spent().get(BOW));
	}

	@Test
	public void weaponsAreCountedIndependently()
	{
		final ChargedWeaponTracker t = new ChargedWeaponTracker();
		t.recordAttack(BOW, 100);
		t.recordAttack(SHADOW, 100);   // different weapon, same tick - both count
		t.recordAttack(SHADOW, 105);
		assertEquals(Long.valueOf(1), t.spent().get(BOW));
		assertEquals(Long.valueOf(2), t.spent().get(SHADOW));
	}

	@Test
	public void resetClearsEverything()
	{
		final ChargedWeaponTracker t = new ChargedWeaponTracker();
		t.recordAttack(BOW, 100);
		t.reset();
		assertTrue(t.spent().isEmpty());
		// and the debounce clock is cleared too
		assertTrue(t.recordAttack(BOW, 100));
	}

	@Test
	public void costUsesTheRecipeAndLivePrices()
	{
		// Venator bow: 1 ancient essence / charge -> 100 * 20
		assertEquals(2_000L, ChargedWeapon.VENATOR_BOW.cost(100, id -> 20));
		// Tumeken's shadow: 2 soul + 5 chaos / charge -> (2*400 + 5*90) * 10 = 12,500
		assertEquals(12_500L, ChargedWeapon.TUMEKENS_SHADOW.cost(10, id ->
			id == net.runelite.api.gameval.ItemID.SOULRUNE ? 400
				: id == net.runelite.api.gameval.ItemID.CHAOSRUNE ? 90 : 0));
		// Eye of Ayak: 1 demon tear / charge
		assertEquals(5_160L, ChargedWeapon.EYE_OF_AYAK.cost(20, id -> 258));
	}

	@Test
	public void attackSignalLookupCoversAnimAndGraphic()
	{
		assertEquals(ChargedWeapon.VENATOR_BOW, ChargedWeapon.byAttackSignal(9858));   // anim
		assertEquals(ChargedWeapon.VENATOR_BOW, ChargedWeapon.byAttackSignal(2289));   // spot-anim
		assertEquals(ChargedWeapon.EYE_OF_AYAK, ChargedWeapon.byAttackSignal(12394));
		assertEquals(ChargedWeapon.TUMEKENS_SHADOW, ChargedWeapon.byAttackSignal(2125));
		assertNull(ChargedWeapon.byAttackSignal(1));
	}

	@Test
	public void materialsScaleWithCharges()
	{
		assertEquals(Integer.valueOf(50), ChargedWeapon.VENATOR_BOW.materials(50).get(
			net.runelite.api.gameval.ItemID.ANCIENT_ESSENCE));
		assertEquals(Integer.valueOf(200), ChargedWeapon.TUMEKENS_SHADOW.materials(100).get(
			net.runelite.api.gameval.ItemID.SOULRUNE));
		assertEquals(Integer.valueOf(500), ChargedWeapon.TUMEKENS_SHADOW.materials(100).get(
			net.runelite.api.gameval.ItemID.CHAOSRUNE));
	}
}
