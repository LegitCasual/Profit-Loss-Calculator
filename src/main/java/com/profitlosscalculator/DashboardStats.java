/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * The numbers behind the {@link DashboardWindow}: every recorded {@link RunRecord}, cut down
 * to a date range and a set of activity categories, rolled into headline totals, a net-per-day
 * series and one row per activity (mob or skill).
 *
 * <p>Pure - no Swing, no client - so it can be unit tested and recomputed on every filter
 * change.
 */
final class DashboardStats
{
	/** Past this many days the net-over-time series switches to one bucket per week. */
	static final int DAILY_LIMIT_DAYS = 90;

	@Getter
	@RequiredArgsConstructor
	enum Range
	{
		DAY("24h", 1),
		WEEK("7d", 7),
		MONTH("30d", 30),
		ALL("All", 0);

		private final String label;
		/** 0 = no lower bound. */
		private final int days;
	}

	@Getter
	@RequiredArgsConstructor
	enum Category
	{
		COMBAT("Combat"),
		SKILLING("Skilling"),
		NOT_IN_COMBAT("Not in combat");

		private final String label;
	}

	/** Skilling income / material cost is booked under the skill's name (see
	 *  {@code reconcileSkilling}), so a perMob key that is a tracked skill's name is skilling. */
	private static final Set<String> SKILL_NAMES = new HashSet<>();

	static
	{
		for (Skill s : SkillingTracker.TRACKED)
		{
			SKILL_NAMES.add(s.getName());
		}
	}

	private DashboardStats()
	{
	}

	static Category categoryOf(String name)
	{
		if (name == null || name.isEmpty())
		{
			return Category.NOT_IN_COMBAT;
		}
		return SKILL_NAMES.contains(name) ? Category.SKILLING : Category.COMBAT;
	}

	static Result compute(List<RunRecord> runs, Range range, Set<Category> categories, Instant now, ZoneId zone)
	{
		final Instant from = range.getDays() > 0 ? now.minus(Duration.ofDays(range.getDays())) : null;
		final Map<String, ActivityAcc> acts = new LinkedHashMap<>();
		final Map<LocalDate, Long> netByDay = new LinkedHashMap<>();
		LocalDate firstDay = null;
		int runCount = 0;
		long trackedSec = 0;

		for (RunRecord r : runs)
		{
			if (r == null || r.getPerMob() == null)
			{
				continue;
			}
			final Instant start = parse(r.getStart());
			if (start == null || (from != null && start.isBefore(from)) || start.isAfter(now))
			{
				continue;
			}

			// a run's wall-clock time can only be pinned on an activity when it was the run's
			// only one - a plain session mixing several mobs / skills has no per-activity split
			final String solo = soloActivity(r.getPerMob());
			boolean counted = false;
			long runNet = 0;
			for (Map.Entry<String, RunRecord.MobRun> e : r.getPerMob().entrySet())
			{
				final RunRecord.MobRun mr = e.getValue();
				final String name = e.getKey() == null ? SessionHistory.UNATTRIBUTED : e.getKey();
				if (mr == null || !categories.contains(categoryOf(name)))
				{
					continue;
				}
				final long net = mr.getGained() - mr.getCost();
				final ActivityAcc a = acts.computeIfAbsent(name, n -> new ActivityAcc());
				a.runs++;
				a.kills += mr.getKills();
				a.gained += mr.getGained();
				a.cost += mr.getCost();
				if (name.equals(solo))
				{
					a.timedSec += r.getDurationSec();
					// the run's not-in-combat cost (teleports there, etc.) is part of the trip
					final RunRecord.MobRun idle = r.getPerMob().get(SessionHistory.UNATTRIBUTED);
					a.timedNet += net - (idle == null ? 0 : idle.getCost());
				}
				runNet += net;
				counted = true;
			}
			if (!counted)
			{
				continue;
			}
			runCount++;
			trackedSec += Math.max(0, r.getDurationSec());
			final LocalDate day = start.atZone(zone).toLocalDate();
			netByDay.merge(day, runNet, Long::sum);
			if (firstDay == null || day.isBefore(firstDay))
			{
				firstDay = day;
			}
		}

		long gained = 0;
		long cost = 0;
		int kills = 0;
		final List<Activity> list = new ArrayList<>();
		for (Map.Entry<String, ActivityAcc> e : acts.entrySet())
		{
			final ActivityAcc a = e.getValue();
			gained += a.gained;
			cost += a.cost;
			kills += a.kills;
			list.add(new Activity(e.getKey(), categoryOf(e.getKey()), a.runs, a.kills, a.gained, a.cost,
				a.gained - a.cost, a.timedSec, a.timedNet));
		}
		list.sort(Comparator.comparingLong(Activity::getNet).reversed());

		final LocalDate today = now.atZone(zone).toLocalDate();
		final LocalDate seriesStart = from != null ? from.atZone(zone).toLocalDate() : firstDay;
		return new Result(runCount, gained - cost, gained, cost, kills, trackedSec,
			buckets(netByDay, seriesStart, today), list);
	}

	/** The one non-idle activity in a run, or null when there were several (or none). */
	static String soloActivity(Map<String, RunRecord.MobRun> perMob)
	{
		String solo = null;
		for (Map.Entry<String, RunRecord.MobRun> e : perMob.entrySet())
		{
			final RunRecord.MobRun mr = e.getValue();
			if (e.getKey() == null || e.getKey().isEmpty() || mr == null
				|| (mr.getKills() == 0 && mr.getGained() == 0 && mr.getCost() == 0))
			{
				continue;
			}
			if (solo != null)
			{
				return null;
			}
			solo = e.getKey();
		}
		return solo;
	}

	/** Contiguous buckets from {@code first} to {@code last} (empty days included as 0), one per
	 *  day, or one per week once the span passes {@value #DAILY_LIMIT_DAYS} days. */
	private static List<Bucket> buckets(Map<LocalDate, Long> netByDay, LocalDate first, LocalDate last)
	{
		final List<Bucket> out = new ArrayList<>();
		if (first == null || first.isAfter(last))
		{
			return out;
		}
		final int step = ChronoUnit.DAYS.between(first, last) >= DAILY_LIMIT_DAYS ? 7 : 1;
		for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(step))
		{
			long net = 0;
			for (int i = 0; i < step; i++)
			{
				net += netByDay.getOrDefault(d.plusDays(i), 0L);
			}
			out.add(new Bucket(d, step, net));
		}
		return out;
	}

	private static Instant parse(String iso)
	{
		try
		{
			return iso == null ? null : Instant.parse(iso);
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static final class ActivityAcc
	{
		private int runs;
		private int kills;
		private long gained;
		private long cost;
		private long timedSec;
		private long timedNet;
	}

	@Value
	static class Result
	{
		int runs;
		long net;
		long gained;
		long cost;
		int kills;
		/** wall-clock seconds of every run that touched an included activity. */
		long trackedSec;
		/** net-over-time series, oldest first. */
		List<Bucket> buckets;
		/** net descending. */
		List<Activity> activities;

		/** The best gp/hr among activities with at least {@code minSec} of timed runs. */
		Activity bestPerHour(long minSec)
		{
			Activity best = null;
			for (Activity a : activities)
			{
				if (a.getTimedSec() >= minSec && a.getCategory() != Category.NOT_IN_COMBAT
					&& (best == null || a.gpPerHour() > best.gpPerHour()))
				{
					best = a;
				}
			}
			return best;
		}
	}

	@Value
	static class Bucket
	{
		LocalDate start;
		/** 1 = a day, 7 = a week. */
		int days;
		long net;
	}

	@Value
	static class Activity
	{
		/** mob / skill name, or "" for the not-in-combat bucket. */
		String name;
		Category category;
		int runs;
		int kills;
		long gained;
		long cost;
		long net;
		/** wall-clock seconds of the runs where this was the only activity. */
		long timedSec;
		/** net earned over those timed runs. */
		long timedNet;

		long gpPerKill()
		{
			return kills > 0 ? net / kills : 0;
		}

		/** 0 when no run of this activity could be timed (see {@link #hasRate()}). */
		long gpPerHour()
		{
			return timedSec > 0 ? timedNet * 3600 / timedSec : 0;
		}

		boolean hasRate()
		{
			return timedSec > 0;
		}
	}
}
