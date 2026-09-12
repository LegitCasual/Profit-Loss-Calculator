/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Map;

/**
 * Neutralises Blue Moon's "Weapon Freeze" special attack (Perilous Moons).
 *
 * <p>The special rips the player's wielded weapon out of the equipment slot and seals it in a
 * block of ice for a handful of ticks; punching the ice returns the weapon untouched. To the
 * plugin's per-tick inventory + equipment diff that round trip looks like the weapon being
 * spent - a thrown weapon's whole stack "fired", or a skilling material - and then picked
 * back up as loot. Neither is real.
 *
 * <p>Fed each tick with {@link net.runelite.api.gameval.VarbitID#PMOON_BOSS_BLUE_WEARPREVENT}
 * (non-zero exactly while a weapon is frozen) and the item in the weapon slot. While a freeze
 * is in effect - and for a short grace period after, covering the tick the weapon reappears -
 * {@link #filter} drops that weapon from the tick's losses and gains so nothing books it, and
 * {@link #active} tells the caller to hold ammo accrual too.
 *
 * <p>Pure and non-injected, like {@link AmmoTracker} / {@link SkillingTracker}.
 */
class BlueMoonWeaponFreeze
{
	/** Ticks after the wear-prevent varbit clears that the weapon's return is still absorbed. */
	private static final int THAW_GRACE_TICKS = 10;

	/** The weapon slot's {itemId, quantity} as of the last tick no freeze was in effect. */
	private long[] lastWielded;
	/** {itemId, quantity} currently held out of the diff, or {@code null} when nothing is. */
	private long[] frozen;
	/** {@code client.getTickCount()} of the last frozen tick seen, or {@code -1} for never. */
	private int lastFrozenTick = -1;

	/** Forget everything (run boundary). */
	void reset()
	{
		lastWielded = null;
		frozen = null;
		lastFrozenTick = -1;
	}

	/**
	 * @param wearPrevent {@code PMOON_BOSS_BLUE_WEARPREVENT > 0} - a weapon is frozen right now
	 * @param weaponSlot  {itemId, quantity} in the equipped weapon slot, or {@code null} if the
	 *                    slot is empty
	 * @param tick        {@code client.getTickCount()}
	 */
	void update(boolean wearPrevent, long[] weaponSlot, int tick)
	{
		if (wearPrevent)
		{
			lastFrozenTick = tick;
			if (frozen == null)
			{
				// by now the weapon may already be out of the slot - fall back to what was
				// wielded on the last un-frozen tick
				final long[] w = weaponSlot != null ? weaponSlot : lastWielded;
				if (w != null)
				{
					frozen = new long[]{w[0], w[1]};
				}
			}
			return;
		}

		if (weaponSlot != null)
		{
			lastWielded = weaponSlot;
		}
		if (frozen != null && !withinGrace(tick))
		{
			// the special is long over (or the tick clock reset on a hop) and we never saw the
			// weapon come back - stop holding it so a later, unrelated loss isn't swallowed
			frozen = null;
		}
	}

	/** True while a frozen weapon's movements should be ignored (freeze active, or just ended). */
	boolean active(int tick)
	{
		return frozen != null || withinGrace(tick);
	}

	private boolean withinGrace(int tick)
	{
		return lastFrozenTick >= 0 && tick >= lastFrozenTick && tick - lastFrozenTick <= THAW_GRACE_TICKS;
	}

	/**
	 * Remove the frozen weapon from this tick's inventory diff: its disappearance from
	 * {@code losses} (going into the ice) and its reappearance in {@code gains} (coming back
	 * out). Clears the hold once the weapon is confirmed back.
	 */
	void filter(Map<Integer, Integer> losses, Map<Integer, Integer> gains)
	{
		if (frozen == null)
		{
			return;
		}
		final int id = (int) frozen[0];
		losses.remove(id);

		final Integer back = gains.get(id);
		if (back != null)
		{
			final int surplus = back - (int) frozen[1];
			if (surplus > 0)
			{
				gains.put(id, surplus);
			}
			else
			{
				gains.remove(id);
			}
			frozen = null;
		}
	}
}
