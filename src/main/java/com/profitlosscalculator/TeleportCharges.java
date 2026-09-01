/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Detects a charged teleport jewellery item ticking down a charge between two
 * inventory+equipment snapshots (e.g. Amulet of glory(4) -&gt; (3) = one teleport).
 *
 * <p>Only the item groups below are watched - jewellery whose whole point is teleporting.
 * A teleport keeps the item but swaps it for the next-lower-charge variant, or, on the
 * final charge, consumes it outright. The detection is pure; the plugin supplies the
 * per-variant charge counts (parsed from item names) and does the live pricing.
 */
final class TeleportCharges
{
	/** One charged variant per group - {@link ItemVariationMapping} expands to the rest. */
	private static final int[] SEEDS = {
		ItemID.AMULET_OF_GLORY_4,          // amulet of glory (+ glory(t))
		ItemID.RING_OF_DUELING_8,
		ItemID.NECKLACE_OF_MINIGAMES_8,    // games necklace
		ItemID.JEWL_NECKLACE_OF_SKILLS_4,  // skills necklace
		ItemID.JEWL_BRACELET_OF_COMBAT_4,  // combat bracelet
		ItemID.NECKLACE_OF_PASSAGE_5,
		ItemID.BURNING_AMULET_5,
		ItemID.RING_OF_WEALTH_5,
		ItemID.SLAYER_RING_8,
		ItemID.RING_OF_RETURNING_5,
	};

	/** base item id -> every variant id in that group */
	private static final Map<Integer, Set<Integer>> GROUPS = new LinkedHashMap<>();

	static
	{
		for (int seed : SEEDS)
		{
			final int base = ItemVariationMapping.map(seed);
			GROUPS.computeIfAbsent(base, b -> new HashSet<>())
				.addAll(ItemVariationMapping.getVariations(base));
		}
	}

	private TeleportCharges()
	{
	}

	/** All variant ids across every watched group - used to build the charge-count map. */
	static Set<Integer> variantIds()
	{
		final Set<Integer> all = new HashSet<>();
		GROUPS.values().forEach(all::addAll);
		return all;
	}

	/** base item id -&gt; the variant ids in that group. */
	static Map<Integer, Set<Integer>> groups()
	{
		return Collections.unmodifiableMap(GROUPS);
	}

	@Value
	static class Charge
	{
		int base;
		/** variant held before the teleport (the thing that lost a charge). */
		int fromId;
		/** variant held after, or -1 if the last charge was spent and the item is gone. */
		int toId;
		long chargesUsed;
	}

	/**
	 * @param before      union(inventory, equipment) before the container change
	 * @param after       union(inventory, equipment) after it
	 * @param chargesById variant item id -&gt; charges it represents (0 for an uncharged item)
	 * @return one entry per group that lost charges in a way that looks like a teleport
	 */
	static List<Charge> detect(Map<Integer, Integer> before, Map<Integer, Integer> after,
		Map<Integer, Integer> chargesById)
	{
		final List<Charge> out = new ArrayList<>();

		for (Map.Entry<Integer, Set<Integer>> group : GROUPS.entrySet())
		{
			long chargesBefore = 0;
			long chargesAfter = 0;
			final Map<Integer, Integer> lost = new HashMap<>();
			final Map<Integer, Integer> gained = new HashMap<>();

			for (int id : group.getValue())
			{
				final int qb = before.getOrDefault(id, 0);
				final int qa = after.getOrDefault(id, 0);
				final int ch = chargesById.getOrDefault(id, 0);
				chargesBefore += (long) qb * ch;
				chargesAfter += (long) qa * ch;

				if (qa < qb)
				{
					lost.put(id, qb - qa);
				}
				else if (qa > qb)
				{
					gained.put(id, qa - qb);
				}
			}

			if (chargesAfter >= chargesBefore || lost.isEmpty())
			{
				continue;
			}

			final int lostCount = lost.values().stream().mapToInt(Integer::intValue).sum();
			final int gainedCount = gained.values().stream().mapToInt(Integer::intValue).sum();

			// a teleport either swaps one item for its next-lower tier (1 lost, 1 gained)
			// or spends the final charge and the item vanishes (1 lost, 0 gained). A bank
			// withdrawal or swapping two different glories won't match this shape.
			final boolean looksLikeTeleport =
				(lostCount == 1 && gainedCount == 1) || (lostCount == 1 && gainedCount == 0);
			if (!looksLikeTeleport)
			{
				continue;
			}

			final int fromId = lost.keySet().iterator().next();
			final int toId = gained.isEmpty() ? -1 : gained.keySet().iterator().next();
			out.add(new Charge(group.getKey(), fromId, toId, chargesBefore - chargesAfter));
		}

		return out;
	}
}
