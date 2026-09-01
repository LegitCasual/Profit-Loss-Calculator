/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionSummaryTest
{
	/** flat 200 gp per item, whichever map the caller points it at */
	private static final ToLongFunction<IncomeEvent> DROPPED =
		e -> e.getItems().values().stream().mapToLong(q -> q * 200L).sum();
	private static final ToLongFunction<IncomeEvent> COLLECTED =
		e -> e.getCollected().values().stream().mapToLong(q -> q * 200L).sum();

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

	private static IncomeEvent npcLoot(Map<Integer, Integer> items)
	{
		return new IncomeEvent(IncomeEvent.Type.NPC_LOOT, Instant.now(), "Vorkath", items);
	}

	private static Map<Integer, Integer> map(int k, int v)
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(k, v);
		return m;
	}

	@Test
	public void rollsUpCategoriesConfirmedAtRiskCollectedPotentialAndNet()
	{
		Session s = new Session(Instant.now());
		s.add(event(CostEvent.Type.CONSUMABLE, 5_000));
		s.add(event(CostEvent.Type.SPELL, 1_200));
		s.add(event(CostEvent.Type.TELEPORT, 300));
		s.setAmmo(Collections.emptyMap(), 800);
		s.add(death(s, DeathEntry.State.LOST, 2_000_000, 2_000_000, true));
		s.add(death(s, DeathEntry.State.PENDING, 20_000, 0, false));

		IncomeEvent loot = npcLoot(map(561, 100));   // 100 dropped
		loot.collect(561, 60);                       // only 60 picked up
		s.add(loot);
		s.addBossKill("Vorkath");
		s.addBossKill("Vorkath");

		final long collected = s.incomeTotal(COLLECTED);   // 60 * 200 = 12,000
		final long potential = s.incomeTotal(DROPPED);     // 100 * 200 = 20,000
		SessionSummary summary = SessionSummary.of(s, collected, potential);

		assertEquals(5_000, summary.getConsumables());
		assertEquals(800, summary.getAmmo());
		assertEquals(2_000_000, summary.getDeathConfirmed());
		assertEquals(20_000, summary.getAtRisk());
		assertEquals(2, summary.getBossKills());

		assertEquals(2_007_300, summary.total());
		assertEquals(12_000, summary.getCollected());
		assertEquals(20_000, summary.getPotential());
		assertEquals(12_000 - 2_007_300, summary.net());

		assertEquals(2_007_300L, summary.toJsonFields().get("cost"));
		assertEquals(12_000L, summary.toJsonFields().get("collectedIncome"));
		assertEquals(20_000L, summary.toJsonFields().get("potentialIncome"));
		assertTrue(summary.toPlainText().contains("Collected:"));
		assertTrue(summary.toPlainText().contains("Potential:"));
	}

	@Test
	public void confirmedFeeCountsGravestoneZeroDoesNot()
	{
		Session s = new Session(Instant.now());
		s.add(death(s, DeathEntry.State.RETURNED, 500_000, 1_000, true));   // confirmed 1k fee
		s.add(death(s, DeathEntry.State.RETURNED, 800_000, 0, true));       // gravestone, 0

		SessionSummary summary = SessionSummary.of(s, 0, 0);
		assertEquals(1_000, summary.total());
		assertEquals(0, summary.getAtRisk());
		assertEquals(-1_000, summary.net());
	}

	@Test
	public void bossKillsBucketLootPerKill()
	{
		Session s = new Session(Instant.now());
		BossKill k1 = s.addBossKill("Vorkath");
		IncomeEvent d1 = npcLoot(map(536, 2));
		s.add(d1);
		k1.add(d1);

		BossKill k2 = s.addBossKill("Vorkath");
		IncomeEvent d2 = npcLoot(map(11235, 1));
		s.add(d2);
		k2.add(d2);

		assertEquals(2, s.getBossKills());
		assertEquals(2, s.getKills().size());
		assertEquals(1, s.getKills().get(0).getIndex());
		assertEquals(k2, s.lastKill());
		assertEquals(d1, k1.getDrops().get(0));
	}

	@Test
	public void rewardsAndPickupsCollectFullyOnCreation()
	{
		IncomeEvent casket = new IncomeEvent(IncomeEvent.Type.EVENT_LOOT, Instant.now(), "Barrows", map(995, 500_000));
		IncomeEvent grab = new IncomeEvent(IncomeEvent.Type.PICKUP, Instant.now(), "Ground", map(561, 40));

		assertTrue(casket.fullyCollected());
		assertTrue(grab.fullyCollected());

		Session s = new Session(Instant.now());
		s.add(casket);
		s.add(grab);
		// collected == potential for these
		assertEquals(s.incomeTotal(DROPPED), s.incomeTotal(COLLECTED));
	}
}
