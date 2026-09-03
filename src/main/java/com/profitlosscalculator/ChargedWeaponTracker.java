/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.EnumMap;
import java.util.Map;

/**
 * Counts charges spent per {@link ChargedWeapon} this session. The plugin calls
 * {@link #recordAttack} once per attack it attributes to a charged weapon (from the attack
 * spot-anim); each attack is one charge. A short per-weapon tick gap stops a lingering
 * attack graphic from being counted twice. Pure and unit-testable.
 */
class ChargedWeaponTracker
{
	/** Real consecutive attacks are always at least this many ticks apart (the fastest of
	 *  these weapons is 3-tick); anything closer is the same attack graphic seen twice. */
	static final int MIN_ATTACK_GAP_TICKS = 2;

	private final Map<ChargedWeapon, Long> spent = new EnumMap<>(ChargedWeapon.class);
	private final Map<ChargedWeapon, Integer> lastAttackTick = new EnumMap<>(ChargedWeapon.class);

	/** Forget the tally (session start). */
	void reset()
	{
		spent.clear();
		lastAttackTick.clear();
	}

	/**
	 * Record one attack with {@code weapon} at game tick {@code tick}.
	 *
	 * @return true if it counted (false = debounced as a repeat of the same attack)
	 */
	boolean recordAttack(ChargedWeapon weapon, int tick)
	{
		final Integer last = lastAttackTick.get(weapon);
		if (last != null && tick - last < MIN_ATTACK_GAP_TICKS)
		{
			return false;
		}
		lastAttackTick.put(weapon, tick);
		spent.merge(weapon, 1L, Long::sum);
		return true;
	}

	/** weapon -&gt; charges spent this session (only weapons that have spent any). */
	Map<ChargedWeapon, Long> spent()
	{
		return spent;
	}
}
