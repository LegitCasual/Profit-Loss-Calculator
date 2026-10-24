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
import java.util.EnumSet;
import java.util.HashMap;
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

	@Getter
	@RequiredArgsConstructor
	enum CostType
	{
		SUPPLIES("Supplies"),
		SPELLS("Spells"),
		TELEPORTS("Teleports"),
		DEATHS("Deaths"),
		SKILLING("Skilling materials"),
		/** run cost the session logs don't itemise - ammo, weapon charges, unlogged sessions. */
		OTHER("Ammo, charges & other");

		private final String label;
	}

	/** Everything the dashboard reads, copied off the history executor. */
	@Value
	static class Input
	{
		List<RunRecord> runs;
		List<SessionHistory.RealisedEntry> realised;
		List<SessionHistory.CostLine> costs;
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

	static Result compute(Input in, Range range, Set<Category> categories, Instant now, ZoneId zone)
	{
		final Instant from = range.getDays() > 0 ? now.minus(Duration.ofDays(range.getDays())) : null;
		final Map<String, ActivityAcc> acts = new LinkedHashMap<>();
		final Map<LocalDate, Long> netByDay = new LinkedHashMap<>();
		final Map<Integer, long[]> lootByItem = new HashMap<>();   // id -> {qty, gp}
		LocalDate firstDay = null;
		int runCount = 0;
		long trackedSec = 0;

		for (RunRecord r : in.getRuns())
		{
			if (r == null || r.getPerMob() == null)
			{
				continue;
			}
			final Instant start = parse(r.getStart());
			if (!inRange(start, from, now))
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
				// a skill whose XP ticked with no item change books an all-zero row - nothing to show
				if (mr == null || !categories.contains(categoryOf(name))
					|| (mr.getKills() == 0 && mr.getGained() == 0 && mr.getCost() == 0))
				{
					continue;
				}
				final long net = mr.getGained() - mr.getCost();
				final ActivityAcc a = acts.computeIfAbsent(name, n -> new ActivityAcc());
				a.runs++;
				a.kills += mr.getKills();
				a.gained += mr.getGained();
				a.cost += mr.getCost();
				if (mr.getItems() != null)
				{
					for (long[] it : mr.getItems())
					{
						if (it != null && it.length >= 3 && it[1] > 0)
						{
							final long[] agg = lootByItem.computeIfAbsent((int) it[0], k -> new long[2]);
							agg[0] += it[1];
							agg[1] += it[2];
						}
					}
				}
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

		final List<ItemTotal> loot = new ArrayList<>();
		lootByItem.forEach((id, v) -> loot.add(new ItemTotal(id, v[0], v[1])));
		loot.sort(Comparator.comparingLong(ItemTotal::getGp).reversed());

		final LocalDate today = now.atZone(zone).toLocalDate();
		final LocalDate seriesStart = from != null ? from.atZone(zone).toLocalDate() : firstDay;
		return new Result(runCount, gained - cost, gained, cost, kills, trackedSec,
			buckets(netByDay, seriesStart, today), list, loot,
			drift(in.getRealised(), from, now, categories),
			costs(in.getCosts(), from, now, categories, cost));
	}

	/** Sales in range whose loot came from an included activity, grouped by item. */
	private static List<Drift> drift(List<SessionHistory.RealisedEntry> realised, Instant from, Instant now,
		Set<Category> categories)
	{
		final Map<Integer, long[]> byItem = new LinkedHashMap<>();   // id -> {qty, projected, net, tax}
		for (SessionHistory.RealisedEntry re : realised)
		{
			if (re == null || !inRange(parse(re.getSoldAt()), from, now) || !categories.contains(categoryOf(re.getMob())))
			{
				continue;
			}
			final long[] a = byItem.computeIfAbsent(re.getItemId(), k -> new long[4]);
			a[0] += re.getQty();
			a[1] += re.getProjected();
			a[2] += re.getNet();
			a[3] += re.getTax();
		}
		final List<Drift> out = new ArrayList<>();
		byItem.forEach((id, a) -> out.add(new Drift(id, a[0], a[1], a[2], a[3])));
		out.sort((x, y) -> Long.compare(Math.abs(y.drift()), Math.abs(x.drift())));
		return out;
	}

	/**
	 * Logged cost lines in range, by type and by label. Skilling materials follow the Skilling
	 * filter; every other line follows Combat / Not in combat (the logs don't say which mob).
	 * When every category is included, run cost the logs don't account for (ammo, charges,
	 * sessions with no log) is shown as {@link CostType#OTHER}.
	 */
	private static Costs costs(List<SessionHistory.CostLine> lines, Instant from, Instant now,
		Set<Category> categories, long runCost)
	{
		final boolean combat = categories.contains(Category.COMBAT) || categories.contains(Category.NOT_IN_COMBAT);
		final Map<CostType, Long> byType = new LinkedHashMap<>();
		final Map<String, CostItem> byLabel = new LinkedHashMap<>();
		long logged = 0;
		for (SessionHistory.CostLine cl : lines)
		{
			if (cl == null || cl.getType() == null || !inRange(parse(cl.getAt()), from, now))
			{
				continue;
			}
			final boolean skilling = cl.getType() == CostType.SKILLING;
			if (skilling ? !categories.contains(Category.SKILLING) : !combat)
			{
				continue;
			}
			logged += cl.getGp();
			byType.merge(cl.getType(), cl.getGp(), Long::sum);
			final String label = cl.getLabel() == null ? cl.getType().getLabel() : cl.getLabel();
			final CostItem prev = byLabel.get(label);
			byLabel.put(label, new CostItem(label, cl.getType(),
				(prev == null ? 0 : prev.getGp()) + cl.getGp(), (prev == null ? 0 : prev.getCount()) + cl.getCount()));
		}
		if (categories.containsAll(EnumSet.allOf(Category.class)) && runCost > logged)
		{
			byType.put(CostType.OTHER, runCost - logged);
		}

		final List<TypeTotal> types = new ArrayList<>();
		byType.forEach((t, gp) -> types.add(new TypeTotal(t, gp)));
		types.sort(Comparator.comparingLong(TypeTotal::getGp).reversed());
		final List<CostItem> items = new ArrayList<>(byLabel.values());
		items.sort(Comparator.comparingLong(CostItem::getGp).reversed());
		return new Costs(types, items);
	}

	private static boolean inRange(Instant t, Instant from, Instant now)
	{
		return t != null && (from == null || !t.isBefore(from)) && !t.isAfter(now);
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
		/** looted items from included activities, gp descending. */
		List<ItemTotal> loot;
		/** sold / alched items, biggest drift (either way) first. */
		List<Drift> drift;
		Costs costs;

		/** The best gp/hr among activities with at least {@code minSec} of timed runs, or null
		 *  when none of them is making money - the least-bad loss isn't a "best". */
		Activity bestPerHour(long minSec)
		{
			Activity best = null;
			for (Activity a : activities)
			{
				if (a.getTimedSec() >= minSec && a.getCategory() != Category.NOT_IN_COMBAT && a.gpPerHour() > 0
					&& (best == null || a.gpPerHour() > best.gpPerHour()))
				{
					best = a;
				}
			}
			return best;
		}
	}

	@Value
	static class ItemTotal
	{
		int itemId;
		long qty;
		/** value frozen at each run's end. */
		long gp;
	}

	/** What an item actually sold (or alched) for against what it was valued at when looted. */
	@Value
	static class Drift
	{
		int itemId;
		long qty;
		/** value at loot time (frozen at run end). */
		long projected;
		/** received, after GE tax. */
		long net;
		long tax;

		long drift()
		{
			return net - projected;
		}
	}

	@Value
	static class Costs
	{
		/** gp descending. */
		List<TypeTotal> types;
		/** per item / spell / teleport label, gp descending. */
		List<CostItem> items;
	}

	@Value
	static class TypeTotal
	{
		CostType type;
		long gp;
	}

	@Value
	static class CostItem
	{
		String label;
		CostType type;
		long gp;
		/** units used / casts / events. */
		long count;
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
