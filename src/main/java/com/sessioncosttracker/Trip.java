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

/**
 * The span between two bank/GE visits. Owns the cost events logged while it was active
 * plus any deaths that happened during it (deaths can still resolve after the trip has
 * closed - the resolved cost lands back here via {@link #tripId} on the entry).
 */
@Getter
class Trip
{
	private final int id;
	private final Instant startTime;
	private Instant endTime;

	private final List<CostEvent> events = new ArrayList<>();
	private final List<DeathEntry> deaths = new ArrayList<>();

	/** Ammo consumed during this trip: item id -&gt; units. Aggregated, not per-shot. */
	private final Map<Integer, Long> ammoUnits = new LinkedHashMap<>();
	private long ammoGp;

	Trip(int id, Instant startTime)
	{
		this.id = id;
		this.startTime = startTime;
	}

	void close(Instant when)
	{
		this.endTime = when;
	}

	void add(CostEvent event)
	{
		events.add(event);
	}

	void add(DeathEntry death)
	{
		deaths.add(death);
	}

	void addAmmo(int itemId, long units, long gp)
	{
		ammoUnits.merge(itemId, units, Long::sum);
		ammoGp += gp;
	}

	/** A trip with nothing logged, no deaths and no ammo used is collapsed out of the summary. */
	boolean isEmpty()
	{
		return events.isEmpty() && deaths.isEmpty() && ammoUnits.isEmpty();
	}

	long consumableTotal()
	{
		return events.stream()
			.filter(e -> e.getType() == CostEvent.Type.CONSUMABLE)
			.mapToLong(CostEvent::getGp)
			.sum();
	}

	long spellTotal()
	{
		return events.stream()
			.filter(e -> e.getType() == CostEvent.Type.SPELL)
			.mapToLong(CostEvent::getGp)
			.sum();
	}

	long teleportTotal()
	{
		return events.stream()
			.filter(e -> e.getType() == CostEvent.Type.TELEPORT)
			.mapToLong(CostEvent::getGp)
			.sum();
	}

	/** Never negative - picking your own ammo back up can briefly push the running tally down. */
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

	/** Value of deaths in this trip still waiting on resolution/confirmation. */
	long atRiskTotal()
	{
		return deaths.stream().mapToLong(DeathEntry::atRiskValue).sum();
	}

	long total()
	{
		return consumableTotal() + spellTotal() + teleportTotal() + ammoTotal() + confirmedDeathTotal();
	}
}
