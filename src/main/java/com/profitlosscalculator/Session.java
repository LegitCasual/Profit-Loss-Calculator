/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import lombok.Getter;
import lombok.Setter;

/**
 * One Start-to-Stop run. Holds every cost event, every income event, every death, the ammo
 * tally and the boss kill count. Can be paused - while paused the plugin stops accruing to
 * it.
 *
 * <p>A run is one of two modes. A plain <b>session</b> ({@code targetMob == null}) is a flat
 * "record my profit / loss for this stretch of time" ledger. A <b>targeted farm</b>
 * ({@code targetMob} set) only attributes kills and loot for that one mob - every cost while
 * it runs is charged to the farm, and the panel shows a per-kill net.
 */
@Getter
class Session
{
	private final Instant startTime;

	@Setter
	private Instant endTime;

	@Setter
	private boolean paused;

	/** Non-null =&gt; this run is a targeted farm of that mob (exact, case-insensitive name). */
	@Setter
	private String targetMob;

	private final List<CostEvent> events = new ArrayList<>();
	private final List<IncomeEvent> income = new ArrayList<>();
	private final List<DeathEntry> deaths = new ArrayList<>();
	private final List<BossKill> kills = new ArrayList<>();

	/** Ammo used this session: item id -&gt; {fired, recovered, net}. */
	private Map<Integer, long[]> ammoStats = new LinkedHashMap<>();
	private long ammoGp;

	/** Runes consumed by spell casts this session: rune item id -&gt; quantity. */
	private final Map<Integer, Integer> runesUsed = new LinkedHashMap<>();

	private int deathSeq;

	/** Plain-session kill tally (a targeted farm counts {@link #kills} buckets instead). */
	private int killCount;

	/** NPC name -&gt; kills of it this run. Empty key is never used. */
	private final Map<String, Integer> killsByMob = new LinkedHashMap<>();

	/** NPC name -&gt; gp spent while fighting it (consumables, spells, teleports, ammo). The
	 *  empty-string key holds cost incurred while not in combat. Deaths are added here on
	 *  resolution. */
	private final Map<String, Long> costByMob = new LinkedHashMap<>();

	Session(Instant startTime)
	{
		this.startTime = startTime;
	}

	/** Bump the kill tally, and the per-mob count for {@code mob} (if given). */
	void bumpKill(String mob)
	{
		killCount++;
		if (mob != null && !mob.isEmpty())
		{
			killsByMob.merge(mob, 1, Integer::sum);
		}
	}

	/** Charge {@code gp} against {@code mob} ({@code null}/empty =&gt; the "not in combat" bucket). */
	void addMobCost(String mob, long gp)
	{
		if (gp == 0)
		{
			return;
		}
		costByMob.merge(mob == null ? "" : mob, gp, Long::sum);
	}

	/** True when this run is a targeted farm rather than a plain session. */
	boolean isTargeted()
	{
		return targetMob != null && !targetMob.isEmpty();
	}

	int nextDeathId()
	{
		return ++deathSeq;
	}

	void add(CostEvent event)
	{
		events.add(event);
	}

	void add(IncomeEvent event)
	{
		income.add(event);
	}

	void add(DeathEntry death)
	{
		deaths.add(death);
	}

	/** Open a new kill bucket for the farmed mob; later matching loot is attributed to it. */
	BossKill addBossKill(String name)
	{
		final BossKill kill = new BossKill(kills.size() + 1, Instant.now(), name);
		kills.add(kill);
		return kill;
	}

	/** Record runes spent on a cast (rune item id -&gt; quantity), for the loss icon grid. */
	void addRunes(Map<Integer, Integer> runes)
	{
		runes.forEach((id, qty) -> runesUsed.merge(id, qty, Integer::sum));
	}

	BossKill lastKill()
	{
		return kills.isEmpty() ? null : kills.get(kills.size() - 1);
	}

	/** Kill count for the panel: farm buckets when targeted, else the plain tally (never
	 *  less than the number of buckets, so direct {@link #addBossKill} in tests still counts). */
	int getBossKills()
	{
		return isTargeted() ? kills.size() : Math.max(killCount, kills.size());
	}

	/** Replace the ammo tally (the tracker hands back a fresh running total each time). */
	void setAmmo(Map<Integer, long[]> stats, long gp)
	{
		this.ammoStats = stats;
		this.ammoGp = gp;
	}

	long consumableTotal()
	{
		return typeTotal(CostEvent.Type.CONSUMABLE);
	}

	long spellTotal()
	{
		return typeTotal(CostEvent.Type.SPELL);
	}

	long teleportTotal()
	{
		return typeTotal(CostEvent.Type.TELEPORT);
	}

	private long typeTotal(CostEvent.Type type)
	{
		return events.stream()
			.filter(e -> e.getType() == type)
			.mapToLong(CostEvent::getGp)
			.sum();
	}

	/** Never negative - picking your own ammo back up can briefly push the tally down. */
	long ammoTotal()
	{
		return Math.max(0, ammoGp);
	}

	/** Death cost that has resolved and been confirmed (or locked in as a full loss). */
	long confirmedDeathTotal()
	{
		return deaths.stream()
			.filter(DeathEntry::isCounted)
			.mapToLong(DeathEntry::getResolvedCost)
			.sum();
	}

	/** Value of deaths still waiting on resolution/confirmation. */
	long atRiskTotal()
	{
		return deaths.stream().mapToLong(DeathEntry::atRiskValue).sum();
	}

	/** Total gp spent this session (supplies, spells, teleports, ammo, confirmed deaths). */
	long total()
	{
		return consumableTotal() + spellTotal() + teleportTotal() + ammoTotal() + confirmedDeathTotal();
	}

	/**
	 * Value of every income event, priced by the caller's function (which chooses whether
	 * to look at what dropped or only what was collected).
	 */
	long incomeTotal(ToLongFunction<IncomeEvent> pricer)
	{
		return income.stream().mapToLong(pricer).sum();
	}
}
