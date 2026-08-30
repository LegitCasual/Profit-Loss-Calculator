/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * One Start-to-Stop run. Holds every cost event, every death, the ammo tally and the boss
 * kill count. Can be paused - while paused the plugin stops accruing costs to it.
 */
@Getter
class Session
{
	private final Instant startTime;

	@Setter
	private Instant endTime;

	@Setter
	private boolean paused;

	private int bossKills;

	private final List<CostEvent> events = new ArrayList<>();
	private final List<DeathEntry> deaths = new ArrayList<>();

	/** Ammo used this session: item id -&gt; {fired, recovered, net}. */
	private Map<Integer, long[]> ammoStats = new LinkedHashMap<>();
	private long ammoGp;

	private int deathSeq;

	Session(Instant startTime)
	{
		this.startTime = startTime;
	}

	int nextDeathId()
	{
		return ++deathSeq;
	}

	void add(CostEvent event)
	{
		events.add(event);
	}

	void add(DeathEntry death)
	{
		deaths.add(death);
	}

	void addBossKill()
	{
		bossKills++;
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

	long total()
	{
		return consumableTotal() + spellTotal() + teleportTotal() + ammoTotal() + confirmedDeathTotal();
	}
}
