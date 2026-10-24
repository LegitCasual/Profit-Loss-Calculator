/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

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
		return DashboardStats.compute(runs, range, cats, NOW, UTC);
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
