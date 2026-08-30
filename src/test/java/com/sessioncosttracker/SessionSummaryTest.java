/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionSummaryTest
{
	private static CostEvent event(CostEvent.Type type, long gp)
	{
		return new CostEvent(type, Instant.now(), 385, 1, gp, "x", null);
	}

	private static DeathEntry death(Session s, DeathEntry.State state, long full, long resolved, boolean confirmed)
	{
		DeathEntry d = new DeathEntry(s.nextDeathId(), Instant.now(), Collections.emptyMap(), 0);
		d.setLoss(Collections.singletonMap(4151, 1), full);
		d.setState(state);
		d.setResolvedCost(resolved);
		d.setUserConfirmed(confirmed);
		return d;
	}

	@Test
	public void rollsUpCategoriesConfirmedAndAtRisk()
	{
		Session s = new Session(Instant.now());
		s.add(event(CostEvent.Type.CONSUMABLE, 5_000));
		s.add(event(CostEvent.Type.SPELL, 1_200));
		s.add(event(CostEvent.Type.TELEPORT, 300));
		s.setAmmo(Collections.emptyMap(), 800);
		s.add(death(s, DeathEntry.State.LOST, 2_000_000, 2_000_000, true));
		s.add(death(s, DeathEntry.State.PENDING, 20_000, 0, false));
		s.addBossKill();
		s.addBossKill();

		SessionSummary summary = SessionSummary.of(s);

		assertEquals(5_000, summary.getConsumables());
		assertEquals(1_200, summary.getSpells());
		assertEquals(300, summary.getTeleports());
		assertEquals(800, summary.getAmmo());
		assertEquals(2_000_000, summary.getDeathConfirmed());
		assertEquals(20_000, summary.getAtRisk());
		assertEquals(2, summary.getBossKills());
		assertEquals(2_007_300, summary.total());

		assertEquals(2_007_300L, summary.toJsonFields().get("total"));
		assertTrue(summary.toPlainText().contains("Session total"));
		assertTrue(summary.toPlainText().contains("Boss kills"));
	}

	@Test
	public void confirmedFeeCountsGravestoneZeroDoesNot()
	{
		Session s = new Session(Instant.now());
		s.add(death(s, DeathEntry.State.RETURNED, 500_000, 1_000, true));   // confirmed 1k fee
		s.add(death(s, DeathEntry.State.RETURNED, 800_000, 0, true));       // gravestone, 0

		SessionSummary summary = SessionSummary.of(s);
		assertEquals(1_000, summary.total());
		assertEquals(0, summary.getAtRisk());
	}
}
