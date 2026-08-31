/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionHistoryTest
{
	private static RunRecord.MobRun mob(int kills, long gained, long cost, long[]... items)
	{
		return new RunRecord.MobRun(kills, gained, gained, cost, 0,
			new ArrayList<>(Arrays.asList(items)));
	}

	private static RunRecord run(String kind, String start, Object... mobKv)
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		for (int i = 0; i < mobKv.length; i += 2)
		{
			perMob.put((String) mobKv[i], (RunRecord.MobRun) mobKv[i + 1]);
		}
		return new RunRecord(SessionHistory.SCHEMA, kind, start, "end", 600, "GE", perMob);
	}

	private static JsonObject obj(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	@Test
	public void emptyHistory()
	{
		SessionHistory.Snapshot s = SessionHistory.aggregate(Collections.emptyList());
		assertEquals(0, s.getRuns());
		assertEquals(0, s.getNet());
		assertTrue(s.getMobs().isEmpty());
	}

	@Test
	public void mergesOneMobAcrossASessionAndAFarm()
	{
		RunRecord farm = run("farm", "2026-01-01T00:00:00Z",
			"Vorkath", mob(10, 6_200_000, 1_200_000, new long[]{536, 20, 4_000}));
		RunRecord session = run("session", "2026-01-02T00:00:00Z",
			"Vorkath", mob(6, 3_400_000, 500_000, new long[]{536, 12, 2_400}, new long[]{11235, 1, 2_000_000}),
			"Zombie", mob(40, 100_000, 0, new long[]{526, 40, 4_000}),
			"", mob(0, 0, 9_000));   // teleport home while not in combat

		SessionHistory.Snapshot s = SessionHistory.aggregate(Arrays.asList(farm, session));

		assertEquals(2, s.getRuns());
		assertEquals(9_700_000 - 1_700_000 - 9_000, s.getNet());
		assertEquals(9_700_000, s.getGained());
		assertEquals(1_709_000, s.getCost());
		assertEquals(56, s.getKills());

		SessionHistory.MobStats vork = s.getMobs().get(0);
		assertEquals("Vorkath", vork.getName());
		assertEquals(16, vork.getKills());
		assertEquals(2, vork.getRuns());
		assertEquals(9_600_000, vork.getGained());
		assertEquals(1_700_000, vork.getCost());
		assertEquals(7_900_000, vork.getNet());
		assertEquals(7_900_000 / 16, vork.getGpPerKill());
		assertEquals(2, vork.getRunList().size());
		// items merged, gp-descending
		assertEquals(11235L, vork.getItems().get(0)[0]);
		assertEquals(536L, vork.getItems().get(1)[0]);
		assertEquals(32L, vork.getItems().get(1)[1]);

		// the "not in combat" bucket always sinks to the bottom regardless of net
		SessionHistory.MobStats last = s.getMobs().get(s.getMobs().size() - 1);
		assertEquals("", last.getName());
		assertEquals(-9_000, last.getNet());
	}

	@Test
	public void mobsSortByNetNotGains()
	{
		RunRecord r = run("session", "2026-01-01T00:00:00Z",
			"BigLoot", mob(5, 10_000_000, 9_500_000),   // net 500k
			"Cheap", mob(50, 2_000_000, 200_000));      // net 1.8M
		assertEquals("Cheap", SessionHistory.aggregate(Collections.singletonList(r)).getMobs().get(0).getName());
	}

	@Test
	public void migrateV2FarmRecordBecomesAOneMobFarm()
	{
		RunRecord rr = SessionHistory.migrateV2(obj("{\"schema\":2,\"mob\":\"Brutus\","
			+ "\"start\":\"2026-08-31T10:52:41Z\",\"end\":\"2026-08-31T10:59:38Z\",\"durationSec\":416,"
			+ "\"valuation\":\"HIGHEST\",\"kills\":10,\"net\":9448,\"cost\":1000,\"collected\":10448,"
			+ "\"dropped\":12770,\"otherIncome\":0,\"deaths\":0,\"items\":[[33107,3,651]]}"));
		assertEquals("farm", rr.getKind());
		RunRecord.MobRun mr = rr.getPerMob().get("Brutus");
		assertEquals(10, mr.getKills());
		assertEquals(10448, mr.getGained());
		assertEquals(1000, mr.getCost());
		assertEquals(651L, mr.getItems().get(0)[2]);
	}

	@Test
	public void migrateV1KeepsSingleMobDropsMixed()
	{
		RunRecord single = SessionHistory.migrateV1(obj("{\"schema\":1,\"cost\":1000,"
			+ "\"start\":\"2026-08-31T10:52:41Z\",\"durationSec\":416,\"valuation\":\"HIGHEST\","
			+ "\"perMob\":{\"Brutus\":{\"kills\":10,\"collected\":10448,\"dropped\":12770,\"items\":[[33107,3,651]]}}}"));
		assertEquals("Brutus", single.getPerMob().keySet().iterator().next());
		assertEquals(10448, single.getPerMob().get("Brutus").getGained());
		assertEquals(1000, single.getPerMob().get("Brutus").getCost());

		assertNull(SessionHistory.migrateV1(obj("{\"schema\":1,\"cost\":0,\"perMob\":{"
			+ "\"Cow\":{\"kills\":2,\"collected\":0,\"dropped\":450,\"items\":[]},"
			+ "\"Brutus\":{\"kills\":1,\"collected\":651,\"dropped\":868,\"items\":[]}}}")));
		assertNull(SessionHistory.migrateV1(obj("{\"schema\":1,\"cost\":0,\"perMob\":{}}")));
	}

	@Test
	public void nameMatchIsExactCaseInsensitiveTagStripped()
	{
		assertTrue(SessionCostTrackerPlugin.nameMatches("Brutus", "brutus"));
		assertTrue(SessionCostTrackerPlugin.nameMatches("<col=00ffff>Brutus</col>", "Brutus"));
		assertTrue(!SessionCostTrackerPlugin.nameMatches("Cow calf", "Cow"));
		assertTrue(!SessionCostTrackerPlugin.nameMatches(null, "Cow"));
	}
}
