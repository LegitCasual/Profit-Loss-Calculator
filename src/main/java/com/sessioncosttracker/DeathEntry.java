/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * A death, held as a pending ledger entry against the trip that was active at the moment
 * of death. It carries no confirmed cost until it resolves:
 *
 * <ul>
 *     <li>items came back for free (gravestone) -&gt; resolved cost 0</li>
 *     <li>items reclaimed at Death's Office -&gt; resolved cost = the fee paid
 *         (estimated by formula, adjustable in the panel)</li>
 *     <li>never reclaimed by session end -&gt; resolved cost = full GE value lost</li>
 * </ul>
 *
 * <p>The entry is created the instant the player dies (so the trip cannot be collapsed
 * before the loss is known) and {@link #setLoss} fills in the diff a few ticks later.
 */
@Getter
class DeathEntry
{
	enum State
	{
		/** Snapshot taken, waiting to see if items return. */
		PENDING,
		/** Items came back; fee is the formula estimate unless the user overrides it. */
		RETURNED,
		/** Session ended (or user marked) with items still gone - full loss. */
		LOST
	}

	private final int id;
	private final int tripId;
	private final Instant deathTime;

	/** itemId -&gt; quantity that was on the player pre-death and gone post-death. */
	private Map<Integer, Integer> lostItems;

	/** GE value of everything in {@link #lostItems}. */
	private long fullValue;

	@Setter
	private State state = State.PENDING;

	/** gp this death actually costs once resolved (0 until then). */
	@Setter
	private long resolvedCost;

	/** Formula estimate of the Death's Office reclaim fee, shown as the panel default. */
	@Setter
	private long estimatedFee;

	/** True once the user has accepted/edited the fee in the panel (or session stopped). */
	@Setter
	private boolean userConfirmed;

	DeathEntry(int id, int tripId, Instant deathTime, Map<Integer, Integer> lostItems, long fullValue)
	{
		this.id = id;
		this.tripId = tripId;
		this.deathTime = deathTime;
		this.lostItems = Collections.unmodifiableMap(new LinkedHashMap<>(lostItems));
		this.fullValue = fullValue;
	}

	void setLoss(Map<Integer, Integer> lostItems, long fullValue)
	{
		this.lostItems = Collections.unmodifiableMap(new LinkedHashMap<>(lostItems));
		this.fullValue = fullValue;
	}

	/** Whether {@link #resolvedCost} should be counted in the confirmed session total. */
	boolean isCounted()
	{
		return state == State.LOST || (state == State.RETURNED && userConfirmed);
	}

	/** gp still unconfirmed and therefore reported only on the "at risk" line. */
	long atRiskValue()
	{
		if (lostItems.isEmpty())
		{
			return 0L;
		}
		return isCounted() ? 0L : fullValue;
	}
}
