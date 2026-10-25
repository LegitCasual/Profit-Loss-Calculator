/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class ChestEncountersTest
{
	@Test
	public void chestSourcesMapToTheirLabel()
	{
		assertEquals("Moons of Peril", ChestEncounters.labelForChest("Lunar Chest"));
		assertEquals("Barrows", ChestEncounters.labelForChest(" barrows "));
		assertNull(ChestEncounters.labelForChest("Blood Moon"));
		assertNull(ChestEncounters.labelForChest(null));
	}

	@Test
	public void bossesAndChestFoldIntoOneRow()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("Blood Moon", new RunRecord.MobRun(1, 0, 0, 32_000, 0, Collections.emptyList()));
		perMob.put("Blue Moon", new RunRecord.MobRun(1, 0, 0, 20_000, 1, Collections.emptyList()));
		perMob.put("Lunar Chest", new RunRecord.MobRun(0, 74_188, 80_000, 0, 0,
			Arrays.asList(new long[]{995, 50_000, 50_000}, new long[]{565, 100, 24_188})));
		perMob.put("", new RunRecord.MobRun(0, 0, 0, 5_000, 0, Collections.emptyList()));

		final Map<String, RunRecord.MobRun> folded = ChestEncounters.fold(perMob);
		assertEquals(2, folded.size());
		final RunRecord.MobRun moons = folded.get("Moons of Peril");
		assertEquals(2, moons.getKills());
		assertEquals(74_188, moons.getGained());
		assertEquals(80_000, moons.getDropped());
		assertEquals(52_000, moons.getCost());
		assertEquals(1, moons.getDeaths());
		assertArrayEquals(new long[]{995, 50_000, 50_000}, moons.getItems().get(0));
		assertEquals(5_000, folded.get("").getCost());
		// the record itself is left as written
		assertEquals(4, perMob.size());
	}

	@Test
	public void chestOpeningsAreTheKillsWhenCounted()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("Dharok the Wretched", new RunRecord.MobRun(1, 0, 0, 1_000, 0, Collections.emptyList()));
		perMob.put("Verac the Defiled", new RunRecord.MobRun(1, 0, 0, 1_000, 0, Collections.emptyList()));
		perMob.put("Barrows", new RunRecord.MobRun(1, 90_000, 90_000, 0, 0, Collections.emptyList()));
		assertEquals(1, ChestEncounters.fold(perMob).get("Barrows").getKills());
	}

	@Test
	public void bossKillsStandInOnOlderRecords()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("Dharok the Wretched", new RunRecord.MobRun(1, 0, 0, 1_000, 0, Collections.emptyList()));
		perMob.put("Verac the Defiled", new RunRecord.MobRun(1, 0, 0, 1_000, 0, Collections.emptyList()));
		perMob.put("Barrows", new RunRecord.MobRun(0, 90_000, 90_000, 0, 0, Collections.emptyList()));
		assertEquals(2, ChestEncounters.fold(perMob).get("Barrows").getKills());
	}

	@Test
	public void chestOpensAreCountedFromTheSessionLog()
	{
		final Map<String, Integer> opens = SessionHistory.chestOpens(Arrays.asList(
			"{\"event\":\"session_start\",\"startedAt\":\"2026-10-22T13:18:04.980655Z\"}",
			"{\"event\":\"loot\",\"kind\":\"EVENT_LOOT\",\"source\":\"Lunar Chest\",\"items\":{}}",
			"{\"event\":\"loot\",\"kind\":\"NPC_LOOT\",\"source\":\"Brutus\",\"items\":{}}",
			"{\"event\":\"loot\",\"kind\":\"EVENT_LOOT\",\"source\":\"Lunar Chest\",\"items\":{}}",
			"not json"));
		assertEquals(1, opens.size());
		assertEquals(Integer.valueOf(2), opens.get("Lunar Chest"));
	}

	@Test
	public void nothingToFoldReturnsTheSameMap()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("Vorkath", new RunRecord.MobRun(3, 100, 100, 10, 0, Collections.emptyList()));
		perMob.put("Moons of Peril", new RunRecord.MobRun(1, 100, 100, 10, 0, Collections.emptyList()));
		assertSame(perMob, ChestEncounters.fold(perMob));
	}

	@Test
	public void historyShowsOneMoonsRowPerRun()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("Eclipse Moon", new RunRecord.MobRun(1, 0, 0, 10_000, 0, Collections.emptyList()));
		perMob.put("Lunar Chest", new RunRecord.MobRun(0, 60_000, 60_000, 0, 0, Collections.emptyList()));
		final RunRecord run = new RunRecord(SessionHistory.SCHEMA, "session", "2026-10-24T08:00:00Z",
			"2026-10-24T08:10:00Z", 600, "GE", perMob);

		final SessionHistory.Snapshot snap = SessionHistory.aggregate(Collections.singletonList(run),
			Collections.emptyList());
		assertEquals(1, snap.getMobs().size());
		assertEquals("Moons of Peril", snap.getMobs().get(0).getName());
		assertEquals(1, snap.getMobs().get(0).getRuns());
		assertEquals(50_000, snap.getMobs().get(0).getNet());
	}
}
