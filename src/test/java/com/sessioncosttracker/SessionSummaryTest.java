/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionSummaryTest
{
	private static Trip tripWithConsumable(Session s, long gp)
	{
		Trip t = new Trip(s.nextTripId(), Instant.now());
		t.add(new CostEvent(CostEvent.Type.CONSUMABLE, Instant.now(), t.getId(), 385, 1, gp, "Shark", null));
		return t;
	}

	private static DeathEntry death(Session s, int tripId, DeathEntry.State state, long full, long resolved, boolean confirmed)
	{
		DeathEntry d = new DeathEntry(s.nextDeathId(), tripId, Instant.now(), Collections.emptyMap(), 0);
		d.setLoss(Collections.singletonMap(4151, 1), full);
		d.setState(state);
		d.setResolvedCost(resolved);
		d.setUserConfirmed(confirmed);
		return d;
	}

	@Test
	public void summaryRollsUpTripsConfirmedAndAtRisk()
	{
		Session s = new Session(Instant.now());

		Trip t1 = tripWithConsumable(s, 5_000);
		s.getTrips().add(t1);

		Trip t2 = new Trip(s.nextTripId(), Instant.now());
		t2.add(death(s, t2.getId(), DeathEntry.State.LOST, 2_000_000, 2_000_000, true));
		s.getTrips().add(t2);

		Trip t3 = new Trip(s.nextTripId(), Instant.now());
		t3.add(death(s, t3.getId(), DeathEntry.State.PENDING, 20_000, 0, false));
		s.getTrips().add(t3);

		s.setCollapsedEmptyTrips(2);

		SessionSummary summary = SessionSummary.of(s);

		assertEquals(3, summary.getTrips().size());
		assertEquals(5_000, summary.getTrips().get(0).getConsumables());
		assertEquals(2_000_000, summary.getTrips().get(1).getDeathConfirmed());
		assertEquals(20_000, summary.getTrips().get(2).getDeathAtRisk());
		assertEquals(0, summary.getTrips().get(2).getDeathConfirmed());

		assertEquals(2_005_000, summary.getSessionTotal());
		assertEquals(20_000, summary.getAtRisk());
		assertEquals(2, summary.getCollapsedEmptyTrips());

		assertNotNull(summary.toJsonFields().get("trips"));
		assertTrue(summary.toPlainText().contains("Session total"));
	}

	@Test
	public void confirmedFeeCountsGravestoneZeroDoesNot()
	{
		Session s = new Session(Instant.now());
		Trip t = new Trip(s.nextTripId(), Instant.now());
		// returned + confirmed at a 1k fee -> counts 1000
		t.add(death(s, t.getId(), DeathEntry.State.RETURNED, 500_000, 1_000, true));
		// returned via gravestone -> confirmed at 0 -> counts 0, not at risk
		t.add(death(s, t.getId(), DeathEntry.State.RETURNED, 800_000, 0, true));
		s.getTrips().add(t);

		SessionSummary summary = SessionSummary.of(s);
		assertEquals(1_000, summary.getSessionTotal());
		assertEquals(0, summary.getAtRisk());
	}
}
