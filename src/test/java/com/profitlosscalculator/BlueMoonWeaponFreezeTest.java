/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BlueMoonWeaponFreezeTest
{
	private static final int SCYTHE = 22325;
	private static final int DARTS = 11230;

	private static long[] weapon(int id, int qty)
	{
		return new long[]{id, qty};
	}

	private static Map<Integer, Integer> map(Object... kv)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((Integer) kv[i], (Integer) kv[i + 1]);
		}
		return m;
	}

	/** The whole point: a melee weapon ripped out and handed back books nothing. */
	@Test
	public void weaponRoundTripIsNeitherLossNorLoot()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();

		// wielding the scythe, no freeze
		f.update(false, weapon(SCYTHE, 1), 100);
		assertFalse(f.active(100));

		// tick 101: freeze fires, the weapon is already out of the slot
		f.update(true, null, 101);
		assertTrue(f.active(101));
		final Map<Integer, Integer> losses = map(SCYTHE, 1);
		final Map<Integer, Integer> gains = map();
		f.filter(losses, gains);
		assertTrue("weapon loss must be swallowed", losses.isEmpty());

		// ticks 102-107: still frozen, punching ice
		for (int t = 102; t <= 107; t++)
		{
			f.update(true, null, t);
		}

		// tick 108: ice broken, varbit clears, weapon back in the diff as a gain
		f.update(false, weapon(SCYTHE, 1), 108);
		final Map<Integer, Integer> back = map(SCYTHE, 1);
		f.filter(map(), back);
		assertTrue("weapon return must be swallowed", back.isEmpty());

		// hold released once the weapon is confirmed back
		f.update(false, weapon(SCYTHE, 1), 109);
		assertFalse(f.active(120));
	}

	/** A stacked thrown weapon: only the amount that was frozen is absorbed on the way back. */
	@Test
	public void onlyTheFrozenQuantityIsAbsorbed()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();
		f.update(false, weapon(DARTS, 5000), 10);

		f.update(true, weapon(DARTS, 5000), 11);
		f.filter(map(DARTS, 5000), map());

		// weapon back, plus 200 darts genuinely picked up off the floor during the scramble
		f.update(false, weapon(DARTS, 5200), 15);
		final Map<Integer, Integer> gains = map(DARTS, 5200);
		f.filter(map(), gains);
		assertEquals(Integer.valueOf(200), gains.get(DARTS));
	}

	/** Capture falls back to the last wielded weapon when the slot is already empty. */
	@Test
	public void capturesLastWieldedWhenSlotEmptyAtFreeze()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();
		f.update(false, weapon(SCYTHE, 1), 1);
		f.update(false, weapon(SCYTHE, 1), 2);

		f.update(true, null, 3);
		final Map<Integer, Integer> losses = map(SCYTHE, 1);
		f.filter(losses, map());
		assertTrue(losses.isEmpty());
	}

	/** The weapon can reappear a tick or two after the varbit clears - stay active until it
	 *  does so ammo accrual and {@link BlueMoonWeaponFreeze#filter} keep covering it. */
	@Test
	public void staysActiveUntilTheWeaponIsBack()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();
		f.update(false, weapon(SCYTHE, 1), 50);
		f.update(true, null, 51);

		f.update(false, null, 52);        // varbit off, weapon not back yet
		assertTrue(f.active(52));
		f.update(false, null, 53);
		assertTrue(f.active(53));

		f.update(false, weapon(SCYTHE, 1), 54);
		final Map<Integer, Integer> gains = map(SCYTHE, 1);
		f.filter(map(), gains);
		assertTrue("late return still swallowed", gains.isEmpty());
		assertFalse(f.active(70));
	}

	/** If the weapon never comes back, the hold is dropped after the grace window so it can't
	 *  swallow an unrelated later loss. */
	@Test
	public void staleHoldIsReleasedAndDoesNotSwallowLaterLosses()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();
		f.update(false, weapon(SCYTHE, 1), 0);
		f.update(true, null, 1);

		for (int t = 2; t <= 20; t++)
		{
			f.update(false, null, t);
		}
		assertFalse(f.active(20));

		final Map<Integer, Integer> losses = map(SCYTHE, 1);
		f.filter(losses, map());
		assertEquals(Integer.valueOf(1), losses.get(SCYTHE));
	}

	@Test
	public void filterIsNoOpWithoutAFreeze()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();
		f.update(false, weapon(SCYTHE, 1), 5);
		final Map<Integer, Integer> losses = map(SCYTHE, 1);
		final Map<Integer, Integer> gains = map(DARTS, 30);
		f.filter(losses, gains);
		assertEquals(Integer.valueOf(1), losses.get(SCYTHE));
		assertEquals(Integer.valueOf(30), gains.get(DARTS));
	}

	@Test
	public void resetClearsEverything()
	{
		final BlueMoonWeaponFreeze f = new BlueMoonWeaponFreeze();
		f.update(false, weapon(SCYTHE, 1), 1);
		f.update(true, null, 2);
		assertTrue(f.active(2));

		f.reset();
		assertFalse(f.active(2));
		final Map<Integer, Integer> losses = map(SCYTHE, 1);
		f.filter(losses, map());
		assertEquals(Integer.valueOf(1), losses.get(SCYTHE));
	}
}
