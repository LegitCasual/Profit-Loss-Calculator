/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-NPC / raid encounters whose only reward is a single shared chest.
 *
 * <p>Keyed by the chest's {@code LootReceived} source name (verified against RuneLite's own
 * {@code LootTrackerPlugin} source, not guessed) -&gt; the Target Farm label a run of it is
 * counted against. The client gives no reliable way to attribute a shared chest to which of
 * the underlying NPCs actually died (RuneLite's own {@code !kc} chat command treats a chest
 * opening as the whole kill count for exactly this content - {@code !kc barrows}, {@code !kc
 * lunar chest} - not a count of individual brothers/bosses), so a farm targeting one of these
 * labels counts a "kill" the moment its chest is looted rather than off any individual boss's
 * {@code ActorDeath} - which for some of them (Perilous Moons) doesn't even fire in a way the
 * client can see. Each label is a plain, atomic entry in {@link KnownMobNames} (not a
 * {@code GROUPS} label that expands to sub-names) - farming "Moons of Peril" targets this one
 * bucket, not three.
 *
 * <p>A plain session books the fight under each boss's own name and the reward under the
 * chest's, which splits one encounter into a pure-loss row per boss and a pure-profit chest
 * row. {@link #fold} merges them back under the label when history is read; the records on
 * disk keep the names they were written with.
 *
 * <p>Pure, like {@link EncounterAdds}.
 */
final class ChestEncounters
{
	/** Chest loot source (lower-case) -&gt; encounter label. */
	private static final Map<String, String> LABEL_BY_CHEST = new LinkedHashMap<>();
	/** Chest source or member NPC (lower-case) -&gt; encounter label, for {@link #fold}. */
	private static final Map<String, String> LABEL_BY_NAME = new HashMap<>();

	static
	{
		encounter("Barrows", "Barrows", "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled");
		encounter("Moons of Peril", "Lunar Chest", "Blood Moon", "Blue Moon", "Eclipse Moon");
		// raid rooms are many NPCs, not a fixed few - only the chest folds, which already carries
		// the raid's own name
		encounter("Chambers of Xeric", "Chambers of Xeric");
		encounter("Theatre of Blood", "Theatre of Blood");
		encounter("Tombs of Amascut", "Tombs of Amascut");
	}

	private static void encounter(String label, String chest, String... members)
	{
		LABEL_BY_CHEST.put(chest.toLowerCase(Locale.ROOT), label);
		LABEL_BY_NAME.put(chest.toLowerCase(Locale.ROOT), label);
		for (String m : members)
		{
			LABEL_BY_NAME.put(m.toLowerCase(Locale.ROOT), label);
		}
	}

	private ChestEncounters()
	{
	}

	/** The encounter label for a chest's loot source name, or null if it isn't one. */
	static String labelForChest(String lootSource)
	{
		return lootSource == null ? null : LABEL_BY_CHEST.get(lootSource.trim().toLowerCase(Locale.ROOT));
	}

	/** The encounter {@code name} (a chest or one of its NPCs) belongs to, else {@code name}. */
	static String encounterOf(String name)
	{
		return name == null ? null : LABEL_BY_NAME.getOrDefault(name.trim().toLowerCase(Locale.ROOT), name);
	}

	/**
	 * A run's per-mob rows with each chest encounter's bosses and chest merged into one row
	 * under its label. Returns {@code perMob} itself when nothing needs merging.
	 */
	static Map<String, RunRecord.MobRun> fold(Map<String, RunRecord.MobRun> perMob)
	{
		if (perMob == null || perMob.keySet().stream().allMatch(k -> k == null || k.equals(encounterOf(k))))
		{
			return perMob;
		}
		final Map<String, RunRecord.MobRun> out = new LinkedHashMap<>();
		final Map<String, Integer> chestKills = new HashMap<>();
		perMob.forEach((name, mr) ->
		{
			if (mr != null)
			{
				out.merge(encounterOf(name), mr, ChestEncounters::merge);
				if (labelForChest(name) != null && mr.getKills() > 0)
				{
					chestKills.merge(encounterOf(name), mr.getKills(), Integer::sum);
				}
			}
		});
		// a chest opening is the encounter's kill; boss kills only stand in for runs recorded
		// before chests were counted (Barrows brothers die visibly - 6 "kills" a trip)
		chestKills.forEach((label, kills) ->
		{
			final RunRecord.MobRun mr = out.get(label);
			out.put(label, new RunRecord.MobRun(kills, mr.getGained(), mr.getDropped(), mr.getCost(),
				mr.getDeaths(), mr.getItems()));
		});
		return out;
	}

	private static RunRecord.MobRun merge(RunRecord.MobRun a, RunRecord.MobRun b)
	{
		final Map<Long, long[]> items = new LinkedHashMap<>();   // id -> {id, qty, gp}
		for (RunRecord.MobRun mr : new RunRecord.MobRun[]{a, b})
		{
			if (mr.getItems() == null)
			{
				continue;
			}
			for (long[] it : mr.getItems())
			{
				if (it == null || it.length < 2)
				{
					continue;
				}
				final long[] agg = items.computeIfAbsent(it[0], id -> new long[]{id, 0, 0});
				agg[1] += it[1];
				agg[2] += it.length >= 3 ? it[2] : 0;
			}
		}
		final List<long[]> merged = new ArrayList<>(items.values());
		merged.sort((x, y) -> Long.compare(y[2], x[2]));
		return new RunRecord.MobRun(a.getKills() + b.getKills(), a.getGained() + b.getGained(),
			a.getDropped() + b.getDropped(), a.getCost() + b.getCost(), a.getDeaths() + b.getDeaths(), merged);
	}
}
