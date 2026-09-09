/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
		assertTrue(Session.nameMatches("Brutus", "brutus"));
		assertTrue(Session.nameMatches("<col=00ffff>Brutus</col>", "Brutus"));
		assertTrue(!Session.nameMatches("Cow calf", "Cow"));
		assertTrue(!Session.nameMatches(null, "Cow"));
	}

	@Test
	public void nameMatchTreatsLeadingTheAsOptional()
	{
		// the Slayer assignment name carries "The "; the NPC name does not (and vice versa)
		assertTrue(Session.nameMatches("Kalphite Queen", "The Kalphite Queen"));
		assertTrue(Session.nameMatches("The Whisperer", "Whisperer"));
		assertTrue(Session.nameMatches("<col=00ffff>The Nightmare</col>", "the nightmare"));
		// "The" must be a whole leading word - not part of another name
		assertTrue(!Session.nameMatches("Theatre usher", "atre usher"));
	}

	@Test
	public void slayerRunsGetAPerKillRateLikeFarms()
	{
		RunRecord slayer = run("slayer", "2026-01-01T00:00:00Z",
			"Aberrant spectre", mob(10, 100_000, 20_000));
		SessionHistory.Snapshot s = SessionHistory.aggregate(Collections.singletonList(slayer));

		SessionHistory.MobStats m = s.getMobs().get(0);
		assertEquals(600 / 10, m.getSecPerKill());
	}

	// ------------------------------------------------------------------ global-by-item realised

	private static SessionHistory.RealisedEntry re(String kind, int itemId, long qty, long gross,
		long tax, long projected, String at)
	{
		return new SessionHistory.RealisedEntry(1, kind, null, null, itemId, qty, gross, tax,
			gross - tax, projected, at);
	}

	private static SessionHistory.BankEntry bank(int itemId, long signedQty, long unit)
	{
		return new SessionHistory.BankEntry(1, null, null, itemId, signedQty, unit, "t");
	}

	private static SessionHistory.ItemStat item(SessionHistory.Snapshot s, int id)
	{
		return s.getItems().stream().filter(i -> i.getItemId() == id).findFirst().orElse(null);
	}

	@Test
	public void lootedOfSumsAcrossEveryRunAndMob()
	{
		RunRecord a = run("farm", "2026-01-01T00:00:00Z",
			"Brutus", mob(1, 60_000, 0, new long[]{536, 15, 60_000}));   // 15 @ 4000
		RunRecord b = run("session", "2026-01-02T00:00:00Z",
			"Cow", mob(1, 4_000, 0, new long[]{536, 30, 90_000}),        // 30 @ 3000
			"Goblin", mob(1, 0, 0));
		long[] lt = SessionHistory.lootedOf(Arrays.asList(a, b), 536);
		assertEquals(45, lt[0]);
		assertEquals(150_000, lt[1]);
	}

	@Test
	public void itemLedgerCapsSoldAtLootedAndProRatesProceeds()
	{
		// looted 15 T-bone worth 4000 each; sold 20 for 60k net (3k each)
		RunRecord r = run("farm", "2026-01-01T00:00:00Z",
			"Brutus", mob(10, 60_000, 0, new long[]{536, 15, 60_000}));
		SessionHistory.Snapshot s = SessionHistory.aggregate(Collections.singletonList(r),
			Collections.singletonList(re("ge", 536, 20, 63_000, 3_000, 60_000, "t")),
			Collections.emptyList());

		SessionHistory.ItemStat st = item(s, 536);
		assertEquals(15, st.getLootedQty());
		assertEquals(20, st.getSoldQty());
		assertEquals(15, st.getEffectiveSold());              // capped at looted
		assertEquals(0, st.getUnsoldQty());
		assertEquals(60_000 * 15 / 20, st.getSoldNet());      // 45k - pro-rated to 15
		assertEquals(45_000, s.getRealisedNet());
		assertEquals(45_000, s.getActualNet());               // all sold, no cost
	}

	@Test
	public void itemLedgerLeavesUnsoldAtSnapshotValue()
	{
		// looted 15 @ 4000 = 60k potential, cost 10k; sell 8 for 30k net
		RunRecord r = run("farm", "2026-01-01T00:00:00Z",
			"Brutus", mob(10, 60_000, 10_000, new long[]{536, 15, 60_000}));
		SessionHistory.Snapshot s = SessionHistory.aggregate(Collections.singletonList(r),
			Collections.singletonList(re("ge", 536, 8, 31_000, 1_000, 32_000, "t")),
			Collections.emptyList());

		SessionHistory.ItemStat st = item(s, 536);
		assertEquals(8, st.getEffectiveSold());
		assertEquals(7, st.getUnsoldQty());
		assertEquals(28_000, st.getUnsoldValue());            // 7 * 4000
		assertEquals(60_000 - 10_000, s.getNet());            // potential net unchanged
		assertEquals(30_000 + 28_000 - 10_000, s.getActualNet());   // realised + unsold - cost
	}

	@Test
	public void bankedValueClampsToUnsoldGlobally()
	{
		RunRecord r = run("farm", "2026-01-01T00:00:00Z",
			"Brutus", mob(10, 100_000, 0, new long[]{536, 100, 100_000}));   // 100 @ 1000
		List<SessionHistory.BankEntry> banked = Arrays.asList(
			bank(536, 80, 1_000), bank(536, -10, 1_000));   // net 70 banked
		List<SessionHistory.RealisedEntry> realised = Collections.singletonList(
			re("ge", 536, 40, 40_000, 0, 40_000, "t"));      // 40 sold -> 60 unsold

		SessionHistory.Snapshot s = SessionHistory.aggregate(
			Collections.singletonList(r), realised, banked);
		assertEquals(60_000, s.getBankedValue());             // min(70, 60) * 1000
	}

	@Test
	public void round18RealisedLineStillFoldsByItemId()
	{
		RunRecord r = run("farm", "2026-01-01T00:00:00Z",
			"Vorkath", mob(1, 10_000, 0, new long[]{536, 10, 10_000}));
		// a round-19 line carries runStart + mob; the new aggregate ignores them and sums by itemId
		SessionHistory.RealisedEntry old = new SessionHistory.RealisedEntry(
			1, "ge", "2026-01-01T00:00:00Z", "Vorkath", 536, 4, 4_100, 100, 4_000, 4_000, "t");
		SessionHistory.Snapshot s = SessionHistory.aggregate(Collections.singletonList(r),
			Collections.singletonList(old), Collections.emptyList());
		assertEquals(4_000, s.getRealisedNet());
		assertEquals(4, item(s, 536).getEffectiveSold());
	}

	@Test
	public void realisedEntryWithoutKindReadsAsGe()
	{
		SessionHistory.RealisedEntry old =
			new SessionHistory.RealisedEntry(1, null, null, null, 536, 1, 100, 2, 98, 100, "t");
		assertEquals("ge", old.kindOrGe());
	}

	@Test
	public void salesListIsNewestFirst()
	{
		RunRecord r = run("farm", "2026-01-01T00:00:00Z",
			"Vorkath", mob(1, 10_000, 0, new long[]{536, 10, 10_000}));
		List<SessionHistory.RealisedEntry> realised = Arrays.asList(
			re("ge", 536, 5, 5_000, 100, 5_000, "2026-02-01T00:00:00Z"),
			re("alch", 536, 2, 4_000, 0, 2_000, "2026-02-03T00:00:00Z"));

		SessionHistory.Snapshot s = SessionHistory.aggregate(
			Collections.singletonList(r), realised, Collections.emptyList());
		assertEquals(2, s.getSales().size());
		assertEquals("2026-02-03T00:00:00Z", s.getSales().get(0).getAt());
		assertEquals("alch", s.getSales().get(0).getKind());
	}
}
