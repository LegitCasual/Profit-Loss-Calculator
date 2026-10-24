/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DashboardStatsTest
{
	private static final ZoneId UTC = ZoneOffset.UTC;
	private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
	private static final Set<DashboardStats.Category> ALL = EnumSet.allOf(DashboardStats.Category.class);

	private static RunRecord.MobRun mob(int kills, long gained, long cost)
	{
		return new RunRecord.MobRun(kills, gained, gained, cost, 0, Collections.emptyList());
	}

	private static RunRecord run(String start, long durationSec, Object... mobKv)
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		for (int i = 0; i < mobKv.length; i += 2)
		{
			perMob.put((String) mobKv[i], (RunRecord.MobRun) mobKv[i + 1]);
		}
		return new RunRecord(SessionHistory.SCHEMA, "session", start, "end", durationSec, "GE", perMob);
	}

	private static DashboardStats.Result compute(List<RunRecord> runs, DashboardStats.Range range,
		Set<DashboardStats.Category> cats)
	{
		return DashboardStats.compute(new DashboardStats.Input(runs, Collections.emptyList(), Collections.emptyList()),
			range, cats, NOW, UTC);
	}

	private static SessionHistory.RealisedEntry sale(String mob, int itemId, long qty, long projected, long net,
		long tax, String at)
	{
		return new SessionHistory.RealisedEntry(1, "ge", "run", mob, itemId, qty, net + tax, tax, net, projected, at);
	}

	private static SessionHistory.CostLine cost(DashboardStats.CostType type, String label, long gp, String at)
	{
		return new SessionHistory.CostLine(at, type, label, gp, 1);
	}

	@Test
	public void lootGroupsItemsAcrossRunsForIncludedActivities()
	{
		final List<RunRecord> runs = Arrays.asList(
			run("2026-10-24T08:00:00Z", 600, "Vorkath",
				new RunRecord.MobRun(1, 5_000, 5_000, 0, 0, Arrays.asList(new long[]{536, 2, 4_000}, new long[]{995, 1_000, 1_000}))),
			run("2026-10-24T09:00:00Z", 600, "Vorkath",
				new RunRecord.MobRun(1, 2_000, 2_000, 0, 0, Collections.singletonList(new long[]{536, 1, 2_000}))),
			run("2026-10-24T10:00:00Z", 600, "Fishing",
				new RunRecord.MobRun(0, 900, 900, 0, 0, Collections.singletonList(new long[]{317, 9, 900}))));

		final List<DashboardStats.ItemTotal> loot = compute(runs, DashboardStats.Range.ALL,
			EnumSet.of(DashboardStats.Category.COMBAT)).getLoot();
		assertEquals(2, loot.size());
		assertEquals(536, loot.get(0).getItemId());
		assertEquals(3, loot.get(0).getQty());
		assertEquals(6_000, loot.get(0).getGp());
	}

	@Test
	public void driftComparesSaleWithLootTimeValue()
	{
		final DashboardStats.Input in = new DashboardStats.Input(Collections.emptyList(), Arrays.asList(
			sale("Vorkath", 536, 10, 30_000, 27_000, 500, "2026-10-24T08:00:00Z"),
			sale("Vorkath", 536, 5, 15_000, 16_000, 300, "2026-10-24T09:00:00Z"),
			sale("Vorkath", 11286, 1, 1_000_000, 1_200_000, 20_000, "2026-10-01T09:00:00Z"),   // outside 7d
			sale("Fishing", 317, 100, 9_000, 8_000, 0, "2026-10-24T09:00:00Z")),
			Collections.emptyList());

		final List<DashboardStats.Drift> d = DashboardStats.compute(in, DashboardStats.Range.WEEK,
			EnumSet.of(DashboardStats.Category.COMBAT), NOW, UTC).getDrift();
		assertEquals(1, d.size());
		assertEquals(15, d.get(0).getQty());
		assertEquals(45_000, d.get(0).getProjected());
		assertEquals(43_000, d.get(0).getNet());
		assertEquals(-2_000, d.get(0).drift());
	}

	@Test
	public void costsGroupByTypeAndLabelAndFillOther()
	{
		final List<RunRecord> runs = Collections.singletonList(
			run("2026-10-24T08:00:00Z", 600, "Vorkath", mob(1, 0, 10_000)));
		final DashboardStats.Input in = new DashboardStats.Input(runs, Collections.emptyList(), Arrays.asList(
			cost(DashboardStats.CostType.SUPPLIES, "Shark", 1_000, "2026-10-24T08:01:00Z"),
			cost(DashboardStats.CostType.SUPPLIES, "Shark", 1_000, "2026-10-24T08:02:00Z"),
			cost(DashboardStats.CostType.SPELLS, "Vengeance", 3_000, "2026-10-24T08:03:00Z"),
			cost(DashboardStats.CostType.SKILLING, "Fishing materials", 500, "2026-10-24T08:04:00Z")));

		final DashboardStats.Costs all = DashboardStats.compute(in, DashboardStats.Range.ALL, ALL, NOW, UTC).getCosts();
		// 10k run cost - 5.5k logged = 4.5k not itemised
		assertEquals(DashboardStats.CostType.OTHER, all.getTypes().get(0).getType());
		assertEquals(4_500, all.getTypes().get(0).getGp());
		assertEquals("Vengeance", all.getItems().get(0).getLabel());
		assertEquals(2_000, find(all.getItems(), "Shark").getGp());
		assertEquals(2, find(all.getItems(), "Shark").getCount());

		final DashboardStats.Costs skilling = DashboardStats.compute(in, DashboardStats.Range.ALL,
			EnumSet.of(DashboardStats.Category.SKILLING), NOW, UTC).getCosts();
		assertEquals(1, skilling.getTypes().size());
		assertEquals(DashboardStats.CostType.SKILLING, skilling.getTypes().get(0).getType());
	}

	private static DashboardStats.CostItem find(List<DashboardStats.CostItem> items, String label)
	{
		return items.stream().filter(c -> c.getLabel().equals(label)).findFirst().orElse(null);
	}

	@Test
	public void parsesCostLinesFromSessionLog()
	{
		final SessionHistory.CostLine potion = SessionHistory.costLine(new JsonParser().parse(
			"{\"ts\":\"2026-10-22T13:31:50Z\",\"event\":\"consumable\",\"itemId\":2434,\"item\":\"Prayer potion(4)\",\"qty\":2,\"gp\":18000}")
			.getAsJsonObject());
		assertEquals(DashboardStats.CostType.SUPPLIES, potion.getType());
		assertEquals("Prayer potion(4)", potion.getLabel());
		assertEquals(18_000, potion.getGp());
		assertEquals(2, potion.getCount());

		final SessionHistory.CostLine skill = SessionHistory.costLine(new JsonParser().parse(
			"{\"ts\":\"2026-10-22T13:31:50Z\",\"event\":\"skilling\",\"skill\":\"Fletching\",\"materialGp\":420}")
			.getAsJsonObject());
		assertEquals("Fletching materials", skill.getLabel());
		assertEquals(420, skill.getGp());

		// free events and non-cost events are skipped
		assertNull(SessionHistory.costLine(new JsonParser().parse(
			"{\"event\":\"consumable\",\"item\":\"Moonlight potion(1)\",\"qty\":1,\"gp\":0}").getAsJsonObject()));
		assertNull(SessionHistory.costLine(new JsonParser().parse(
			"{\"event\":\"loot\",\"source\":\"Ground\"}").getAsJsonObject()));
	}

	private static DashboardStats.Activity find(DashboardStats.Result r, String name)
	{
		return r.getActivities().stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
	}

	@Test
	public void categorisesSkillsMobsAndIdle()
	{
		assertEquals(DashboardStats.Category.SKILLING, DashboardStats.categoryOf("Fishing"));
		assertEquals(DashboardStats.Category.COMBAT, DashboardStats.categoryOf("Vorkath"));
		assertEquals(DashboardStats.Category.NOT_IN_COMBAT, DashboardStats.categoryOf(""));
	}

	@Test
	public void rangeDropsOlderRuns()
	{
		final List<RunRecord> runs = Arrays.asList(
			run("2026-10-25T08:00:00Z", 3600, "Vorkath", mob(10, 2_000_000, 200_000)),
			run("2026-10-10T08:00:00Z", 3600, "Vorkath", mob(10, 1_000_000, 100_000)));

		final DashboardStats.Result week = compute(runs, DashboardStats.Range.WEEK, ALL);
		assertEquals(1, week.getRuns());
		assertEquals(1_800_000, week.getNet());

		final DashboardStats.Result all = compute(runs, DashboardStats.Range.ALL, ALL);
		assertEquals(2, all.getRuns());
		assertEquals(2_700_000, all.getNet());
		assertEquals(20, all.getKills());
	}

	@Test
	public void categoryFilterDropsActivitiesAndEmptyRuns()
	{
		final List<RunRecord> runs = Arrays.asList(
			run("2026-10-24T08:00:00Z", 1800, "Fishing", mob(0, 300_000, 0), "", mob(0, 0, 5_000)),
			run("2026-10-24T10:00:00Z", 3600, "Zulrah", mob(20, 3_000_000, 400_000)));

		final DashboardStats.Result r = compute(runs, DashboardStats.Range.ALL,
			EnumSet.of(DashboardStats.Category.SKILLING));
		assertEquals(1, r.getRuns());
		assertEquals(300_000, r.getNet());
		assertEquals(1, r.getActivities().size());
		assertEquals(1800, r.getTrackedSec());
	}

	@Test
	public void gpPerHourOnlyFromSoloRuns()
	{
		final List<RunRecord> runs = Arrays.asList(
			// solo Vorkath hour, with a teleport there booked to the idle bucket
			run("2026-10-24T08:00:00Z", 3600, "Vorkath", mob(20, 4_000_000, 500_000), "", mob(0, 0, 100_000)),
			// mixed session - Vorkath's share can't be timed
			run("2026-10-24T12:00:00Z", 7200, "Vorkath", mob(10, 2_000_000, 200_000), "Fishing", mob(0, 100_000, 0)));

		final DashboardStats.Result r = compute(runs, DashboardStats.Range.ALL, ALL);
		final DashboardStats.Activity vork = find(r, "Vorkath");
		assertEquals(3600, vork.getTimedSec());
		assertEquals(3_400_000, vork.gpPerHour());
		assertEquals(5_300_000, vork.getNet());
		assertFalse(find(r, "Fishing").hasRate());
	}

	@Test
	public void bestPerHourNeedsMinimumTime()
	{
		final List<RunRecord> runs = Arrays.asList(
			run("2026-10-24T08:00:00Z", 600, "Lucky", mob(1, 5_000_000, 0)),
			run("2026-10-24T09:00:00Z", 3600, "Steady", mob(50, 1_000_000, 0)));

		final DashboardStats.Result r = compute(runs, DashboardStats.Range.ALL, ALL);
		assertEquals("Steady", r.bestPerHour(1800).getName());
		assertEquals("Lucky", r.bestPerHour(0).getName());
		assertNull(compute(Collections.emptyList(), DashboardStats.Range.ALL, ALL).bestPerHour(0));
	}

	@Test
	public void noBestPerHourWhenEverythingLoses()
	{
		final List<RunRecord> runs = Collections.singletonList(
			run("2026-10-24T08:00:00Z", 3600, "Guard", mob(9, 500, 1_000)));
		assertNull(compute(runs, DashboardStats.Range.ALL, ALL).bestPerHour(0));
	}

	@Test
	public void dropsAllZeroActivities()
	{
		final List<RunRecord> runs = Collections.singletonList(
			run("2026-10-24T08:00:00Z", 600, "Hunter", mob(0, 0, 0), "Giant rat", mob(3, 0, 0)));
		final DashboardStats.Result r = compute(runs, DashboardStats.Range.ALL, ALL);
		assertNull(find(r, "Hunter"));
		// kills with no loot still happened
		assertEquals(3, find(r, "Giant rat").getKills());
	}

	@Test
	public void dailyBucketsFillGaps()
	{
		final List<RunRecord> runs = Arrays.asList(
			run("2026-10-20T08:00:00Z", 60, "Vorkath", mob(1, 100, 0)),
			run("2026-10-22T08:00:00Z", 60, "Vorkath", mob(1, 300, 50)),
			run("2026-10-22T18:00:00Z", 60, "Vorkath", mob(1, 200, 0)));

		final List<DashboardStats.Bucket> b = compute(runs, DashboardStats.Range.ALL, ALL).getBuckets();
		assertEquals(6, b.size());   // 20th .. 25th
		assertEquals(LocalDate.of(2026, 10, 20), b.get(0).getStart());
		assertEquals(100, b.get(0).getNet());
		assertEquals(0, b.get(1).getNet());
		assertEquals(450, b.get(2).getNet());

		// a 7d range starts the series a week back, even before the first run
		assertEquals(8, compute(runs, DashboardStats.Range.WEEK, ALL).getBuckets().size());
	}

	@Test
	public void longSpansBucketByWeek()
	{
		final List<RunRecord> runs = Collections.singletonList(
			run("2026-01-01T08:00:00Z", 60, "Vorkath", mob(1, 100, 0)));
		final List<DashboardStats.Bucket> b = compute(runs, DashboardStats.Range.ALL, ALL).getBuckets();
		assertTrue(b.size() < DashboardStats.DAILY_LIMIT_DAYS);
		assertEquals(7, b.get(0).getDays());
		assertEquals(100, b.get(0).getNet());
	}

	@Test
	public void skipsBadAndFutureStarts()
	{
		final List<RunRecord> runs = Arrays.asList(
			run("not a date", 60, "Vorkath", mob(1, 100, 0)),
			run("2027-01-01T00:00:00Z", 60, "Vorkath", mob(1, 100, 0)),
			null);
		assertEquals(0, compute(runs, DashboardStats.Range.ALL, ALL).getRuns());
	}
}
