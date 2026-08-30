/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.function.IntSupplier;
import lombok.Getter;

/**
 * Owns trip open/close and the debounce that stops a fresh trip being cut every time the
 * player re-opens the bank in one visit or wanders around the Grand Exchange.
 *
 * <p>Pure logic - it takes coordinate and tick ints, never RuneLite objects - so the
 * debounce rules can be unit tested. A bank/GE trigger only cuts a new trip when BOTH:
 *
 * <ul>
 *     <li>it has been at least {@link #REARM_TICKS} game ticks since the last bank/GE
 *         trigger of any kind (kills rapid double-fires from the interface load + the
 *         menu click, and "still fiddling with the bank" re-opens), and</li>
 *     <li>the player has been more than {@code debounceTiles} away (Chebyshev distance,
 *         absolute world coords) from where the current trip opened - i.e. actually left
 *         and came back.</li>
 * </ul>
 */
class TripManager
{
	interface Listener
	{
		void onTripOpened(Trip trip, String reason);

		void onTripClosed(Trip trip, boolean collapsed);
	}

	/** Minimum game ticks between one bank/GE trigger and the next that may cut a trip. */
	static final int REARM_TICKS = 10;

	private final IntSupplier debounceTiles;
	private final Listener listener;

	@Getter
	private Session session;

	private int boundaryX;
	private int boundaryY;
	private double maxDistSinceBoundary;
	private int lastTriggerTick;

	TripManager(IntSupplier debounceTiles, Listener listener)
	{
		this.debounceTiles = debounceTiles;
		this.listener = listener;
	}

	boolean isActive()
	{
		return session != null;
	}

	Session start(Instant now, int tick, int x, int y)
	{
		session = new Session(now);
		lastTriggerTick = tick;
		openTrip(now, x, y, "start");
		return session;
	}

	/** Called every tick with the player's current position while a session is active. */
	void updatePosition(int x, int y)
	{
		if (session == null)
		{
			return;
		}
		double d = Math.max(Math.abs(x - boundaryX), Math.abs(y - boundaryY));
		if (d > maxDistSinceBoundary)
		{
			maxDistSinceBoundary = d;
		}
	}

	/**
	 * A bank/GE trigger fired at the given position/tick. Returns true if it actually cut
	 * a new trip, false if it was debounced. Every call updates the "last trigger" tick,
	 * so a burst of triggers keeps pushing the re-arm window forward.
	 */
	boolean boundary(String reason, Instant now, int tick, int x, int y)
	{
		if (session == null || session.getCurrentTrip() == null)
		{
			return false;
		}
		updatePosition(x, y);

		final boolean rearmed = tick - lastTriggerTick >= REARM_TICKS;
		final boolean left = maxDistSinceBoundary > debounceTiles.getAsInt();
		lastTriggerTick = tick;

		if (!rearmed || !left)
		{
			return false;
		}
		closeTrip(now);
		openTrip(now, x, y, reason);
		return true;
	}

	/** Close the open trip, mark the session ended, and hand it back for the summary. */
	Session stop(Instant now)
	{
		if (session == null)
		{
			return null;
		}
		closeTrip(now);
		session.setEndTime(now);
		Session finished = session;
		session = null;
		return finished;
	}

	private void openTrip(Instant now, int x, int y, String reason)
	{
		Trip trip = new Trip(session.nextTripId(), now);
		session.setCurrentTrip(trip);
		boundaryX = x;
		boundaryY = y;
		maxDistSinceBoundary = 0;
		listener.onTripOpened(trip, reason);
	}

	private void closeTrip(Instant now)
	{
		Trip trip = session.getCurrentTrip();
		if (trip == null)
		{
			return;
		}
		trip.close(now);
		boolean collapsed = trip.isEmpty();
		if (collapsed)
		{
			session.setCollapsedEmptyTrips(session.getCollapsedEmptyTrips() + 1);
		}
		else
		{
			session.getTrips().add(trip);
		}
		session.setCurrentTrip(null);
		listener.onTripClosed(trip, collapsed);
	}
}
