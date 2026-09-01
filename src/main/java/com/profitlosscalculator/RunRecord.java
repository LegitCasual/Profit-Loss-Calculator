/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * One finished run - a plain session or a targeted farm - as written to
 * {@code .runelite/profit-loss-calculator/history.jsonl} (one JSON object per line). The
 * History tab reads these back and merges {@link #perMob} across every run, so a mob you
 * fought in three different sessions and one farm slots into a single row.
 */
@Value
class RunRecord
{
	int schema;
	/** "session" or "farm". */
	String kind;
	String start;
	String end;
	long durationSec;
	String valuation;

	/** NPC name -&gt; what that mob produced / cost this run. The empty-string key holds cost
	 *  that could not be tied to a mob (teleporting while not in combat, etc.). */
	Map<String, MobRun> perMob;

	@Value
	static class MobRun
	{
		int kills;
		/** collected loot value, frozen at run-end prices. */
		long gained;
		/** everything that dropped (collected or not), frozen at run-end prices. */
		long dropped;
		/** gp spent while fighting this mob - consumables, spells, teleports, ammo, deaths. */
		long cost;
		int deaths;
		/** collected items, {@code [ [itemId, quantity, gpAtRunEnd], ... ]} - gp frozen at run end. */
		List<long[]> items;
	}
}
