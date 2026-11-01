/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DeathReviewsTest
{
	private static final String START =
		"{\"ts\":\"2026-10-20T18:00:00Z\",\"event\":\"session_start\",\"startedAt\":\"2026-10-20T18:00:00Z\"}";
	private static final String DEATH =
		"{\"ts\":\"2026-10-20T18:30:00Z\",\"event\":\"death\",\"deathId\":1}";
	private static final String PENDING =
		"{\"ts\":\"2026-10-20T18:30:06Z\",\"event\":\"death_pending\",\"deathId\":1,\"fullValue\":520000000,"
			+ "\"estimatedFee\":500000,\"items\":{\"Twisted bow (20997)\":1,\"Ancestral hat (21018)\":1}}";
	private static final String LOST =
		"{\"ts\":\"2026-10-20T19:00:00Z\",\"event\":\"death_resolved\",\"deathId\":1,\"outcome\":\"lost\",\"gp\":520000000}";

	@Test
	public void fullLossDeathNeedsReview()
	{
		final List<DeathReviews.Review> out = DeathReviews.scan("session-a.jsonl", Arrays.asList(START, DEATH, PENDING, LOST));
		assertEquals(1, out.size());
		final DeathReviews.Review r = out.get(0);
		assertEquals("session-a.jsonl", r.getLog());
		assertEquals("2026-10-20T18:00:00Z", r.getRunStart());
		assertEquals(1, r.getDeathId());
		assertEquals("2026-10-20T18:30:00Z", r.getAt());
		assertEquals(520_000_000L, r.getCharged());
		assertEquals(500_000L, r.getFee());
		assertEquals(Arrays.asList(new DeathReviews.Item(20997, "Twisted bow", 1),
			new DeathReviews.Item(21018, "Ancestral hat", 1)), r.getItems());
		assertNull(r.getMob());
	}

	@Test
	public void reclaimedOrReviewedDeathsDoNot()
	{
		final String reclaimed =
			"{\"event\":\"death_resolved\",\"deathId\":1,\"outcome\":\"reclaimed\",\"gp\":500000}";
		final String reviewed = "{\"event\":\"death_reviewed\",\"deathId\":1,\"verdict\":\"denied\",\"gp\":500000}";
		assertTrue(DeathReviews.scan("a", Arrays.asList(START, DEATH, PENDING, reclaimed)).isEmpty());
		assertTrue(DeathReviews.scan("a", Arrays.asList(START, DEATH, PENDING, LOST, reviewed)).isEmpty());
	}

	@Test
	public void logWithoutStartIsSkipped()
	{
		assertTrue(DeathReviews.scan("a", Arrays.asList(DEATH, PENDING, LOST)).isEmpty());
	}

	@Test
	public void chargeIsAttributedToTheMobThatRecordedTheDeath()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("Vorkath", new RunRecord.MobRun(5, 1_000_000, 1_000_000, 521_000_000, 1, Collections.emptyList()));
		perMob.put("Zulrah", new RunRecord.MobRun(3, 900_000, 900_000, 200_000, 0, Collections.emptyList()));
		perMob.put("", new RunRecord.MobRun(0, 0, 0, 30_000, 0, Collections.emptyList()));
		final RunRecord run = new RunRecord(SessionHistory.SCHEMA, "session", "s", "e", 60, "GE", perMob);

		assertEquals("Vorkath", DeathReviews.attribute(run, 520_000_000L));
		assertNull(DeathReviews.attribute(run, 600_000_000L));
		assertNull(DeathReviews.attribute(null, 1));

		DeathReviews.refund(run, "Vorkath", 520_000_000L - 500_000L);
		assertEquals(1_500_000L, run.getPerMob().get("Vorkath").getCost());
		// still died - the death count stays
		assertEquals(1, run.getPerMob().get("Vorkath").getDeaths());
	}

	@Test
	public void notInCombatBucketIsTheFallback()
	{
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put("", new RunRecord.MobRun(0, 0, 0, 10_000_000, 0, Collections.emptyList()));
		final RunRecord run = new RunRecord(SessionHistory.SCHEMA, "session", "s", "e", 60, "GE", perMob);
		assertEquals("", DeathReviews.attribute(run, 9_000_000L));
	}
}
