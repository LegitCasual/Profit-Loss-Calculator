/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;

/**
 * One Start-to-Stop play session. Holds the kept (non-empty) trips plus the trip that is
 * currently open, and hands out monotonic ids for trips and deaths.
 */
@Getter
class Session
{
	private final Instant startTime;

	@Setter
	private Instant endTime;

	/** Closed, non-empty trips in order. */
	private final List<Trip> trips = new ArrayList<>();

	@Setter
	private Trip currentTrip;

	@Setter
	private int collapsedEmptyTrips;

	private int tripSeq;
	private int deathSeq;

	Session(Instant startTime)
	{
		this.startTime = startTime;
	}

	int nextTripId()
	{
		return ++tripSeq;
	}

	int nextDeathId()
	{
		return ++deathSeq;
	}

	/** Kept trips plus the open one (if any). */
	List<Trip> allTrips()
	{
		return Stream.concat(trips.stream(), currentTrip == null ? Stream.empty() : Stream.of(currentTrip))
			.collect(Collectors.toList());
	}

	List<DeathEntry> allDeaths()
	{
		return allTrips().stream()
			.flatMap(t -> t.getDeaths().stream())
			.collect(Collectors.toList());
	}

	Trip tripById(int id)
	{
		return allTrips().stream().filter(t -> t.getId() == id).findFirst().orElse(null);
	}

	long confirmedTotal()
	{
		return allTrips().stream().mapToLong(Trip::total).sum();
	}

	long atRiskTotal()
	{
		return allTrips().stream().mapToLong(Trip::atRiskTotal).sum();
	}
}
