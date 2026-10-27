/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Did a spell's runes actually leave? A manual cast is only charged once they do, so a click
 * the game refuses (Vengeance on cooldown, a thrall already out, too few runes) costs nothing.
 *
 * <p>The rule: every rune type the spell needs (after staff-provided runes are taken out)
 * must show up in the tick's losses - either itself or as a combination rune that stands in
 * for it (a dust rune is an air and an earth rune at once). Requiring every type, not just
 * one, is what stops a refused Vengeance from being confirmed by an Ice Barrage cast the same
 * tick (they share death runes; only Vengeance needs astrals). A Kodai wand's free cast loses
 * nothing and so is - correctly - not charged.
 */
final class RuneSpend
{
	/** combination rune item id -&gt; the runes one of it counts as */
	private static final Map<Integer, Set<Rune>> COMBINATIONS;

	static
	{
		final Map<Integer, Set<Rune>> m = new HashMap<>();
		m.put(ItemID.DUSTRUNE, EnumSet.of(Rune.AIR, Rune.EARTH));
		m.put(ItemID.MISTRUNE, EnumSet.of(Rune.AIR, Rune.WATER));
		m.put(ItemID.SMOKERUNE, EnumSet.of(Rune.AIR, Rune.FIRE));
		m.put(ItemID.MUDRUNE, EnumSet.of(Rune.WATER, Rune.EARTH));
		m.put(ItemID.STEAMRUNE, EnumSet.of(Rune.WATER, Rune.FIRE));
		m.put(ItemID.LAVARUNE, EnumSet.of(Rune.EARTH, Rune.FIRE));
		m.put(ItemID.SUNFIRERUNE, EnumSet.of(Rune.FIRE));
		m.put(ItemID.AETHERRUNE, EnumSet.of(Rune.SOUL, Rune.COSMIC));
		COMBINATIONS = Collections.unmodifiableMap(m);
	}

	private RuneSpend()
	{
	}

	/**
	 * If {@code losses} (item id -&gt; qty lost this tick) covers every rune type in
	 * {@code needed}, takes this cast's share out of it - so a second cast the same tick can't
	 * claim the same runes - and returns true. Otherwise leaves {@code losses} alone.
	 */
	static boolean claim(Map<Rune, Integer> needed, Map<Integer, Integer> losses)
	{
		for (Rune r : needed.keySet())
		{
			if (losses.getOrDefault(r.getItemId(), 0) <= 0 && !combinationCovers(r, losses))
			{
				return false;
			}
		}

		// plain runes first, then whatever is still owed from the combination runes - one
		// combination rune pays one of each rune it counts as
		final Map<Rune, Integer> owed = new EnumMap<>(Rune.class);
		for (Map.Entry<Rune, Integer> e : needed.entrySet())
		{
			final int left = take(losses, e.getKey().getItemId(), e.getValue());
			if (left > 0)
			{
				owed.put(e.getKey(), left);
			}
		}
		for (Map.Entry<Integer, Set<Rune>> c : COMBINATIONS.entrySet())
		{
			int want = 0;
			for (Rune r : c.getValue())
			{
				want = Math.max(want, owed.getOrDefault(r, 0));
			}
			if (want == 0)
			{
				continue;
			}
			final int took = want - take(losses, c.getKey(), want);
			for (Rune r : c.getValue())
			{
				owed.computeIfPresent(r, (k, v) -> v - took > 0 ? v - took : null);
			}
		}
		return true;
	}

	private static boolean combinationCovers(Rune rune, Map<Integer, Integer> losses)
	{
		for (Map.Entry<Integer, Set<Rune>> c : COMBINATIONS.entrySet())
		{
			if (c.getValue().contains(rune) && losses.getOrDefault(c.getKey(), 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** Takes up to {@code qty} of {@code id} out of {@code losses}; returns how much was still owed. */
	private static int take(Map<Integer, Integer> losses, int id, int qty)
	{
		final int have = losses.getOrDefault(id, 0);
		final int took = Math.min(have, qty);
		if (took <= 0)
		{
			return qty;
		}
		if (have - took > 0)
		{
			losses.put(id, have - took);
		}
		else
		{
			losses.remove(id);
		}
		return qty - took;
	}
}
