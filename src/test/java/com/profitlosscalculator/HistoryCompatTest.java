/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;

/**
 * Proves that pre-existing history (schema 3) and round-18 {@code realised.jsonl} (no
 * {@code kind} field) still load and aggregate cleanly after the potential / banked /
 * realised rework - i.e. an update does not lose or corrupt a user's recorded data.
 *
 * <p>Two parts: fixed sample lines taken verbatim from a real {@code history.jsonl} (runs on
 * every machine), and - when present - the actual file in {@code ~/.runelite/} (runs on the
 * dev's machine, skipped elsewhere).
 */
public class HistoryCompatTest
{
	private static final Gson GSON = new Gson();

	/** Verbatim lines from a real history.jsonl - multi-mob slayer with an unattributed "" key
	 *  and a negative cost, the "High alch" pseudo-mob, an empty perMob, and a skill row. */
	private static final String[] REAL_HISTORY_LINES = {
		"{\"schema\":3,\"kind\":\"farm\",\"start\":\"2026-08-31T15:34:52.789593100Z\",\"end\":\"2026-08-31T15:35:59.525085200Z\",\"durationSec\":66,\"valuation\":\"HIGHEST\",\"perMob\":{\"Brutus\":{\"kills\":2,\"gained\":215,\"dropped\":647,\"cost\":77,\"deaths\":0,\"items\":[[556,29,145],[884,14,70]]}}}",
		"{\"schema\":3,\"kind\":\"session\",\"start\":\"2026-08-31T20:44:27.285182600Z\",\"end\":\"2026-08-31T20:46:15.185333800Z\",\"durationSec\":107,\"valuation\":\"HIGHEST\",\"perMob\":{\"High alch\":{\"kills\":0,\"gained\":1023360,\"dropped\":1023360,\"cost\":0,\"deaths\":0,\"items\":[[995,1023360,1023360]]},\"\":{\"kills\":0,\"gained\":0,\"dropped\":0,\"cost\":5376,\"deaths\":0,\"items\":[]}}}",
		"{\"schema\":3,\"kind\":\"slayer\",\"start\":\"2026-09-05T21:45:41.419003800Z\",\"end\":\"2026-09-06T00:35:28.402429Z\",\"durationSec\":10186,\"valuation\":\"GE\",\"perMob\":{\"Iron dragon\":{\"kills\":26,\"gained\":76809,\"dropped\":181129,\"cost\":-44775,\"deaths\":0,\"items\":[[995,21640,21640],[9431,2,19118],[536,5,16650]]},\"\":{\"kills\":0,\"gained\":0,\"dropped\":0,\"cost\":142332,\"deaths\":0,\"items\":[]}}}",
		"{\"schema\":3,\"kind\":\"farm\",\"start\":\"2026-09-05T15:47:00.238946200Z\",\"end\":\"2026-09-05T15:47:21.799161600Z\",\"durationSec\":21,\"valuation\":\"GE\",\"perMob\":{}}",
		"{\"schema\":3,\"kind\":\"session\",\"start\":\"2026-09-06T17:26:11.118736400Z\",\"end\":\"2026-09-06T17:28:19.219199500Z\",\"durationSec\":128,\"valuation\":\"GE\",\"perMob\":{\"Fletching\":{\"kills\":0,\"gained\":250,\"dropped\":250,\"cost\":1000,\"deaths\":0,\"items\":[[56,25,250]]}}}",
	};

	/** Verbatim round-18 realised.jsonl lines - note: no "kind" field. */
	private static final String[] REAL_REALISED_LINES = {
		"{\"schema\":1,\"runStart\":\"2026-09-06T13:04:10.082808100Z\",\"mob\":\"Ice troll female\",\"itemId\":1161,\"qty\":2,\"gross\":3456,\"tax\":68,\"net\":3388,\"projected\":3598,\"soldAt\":\"2026-09-07T19:30:33.086064300Z\"}",
	};

	@Test
	public void schema3LinesStillParseToRunRecords()
	{
		for (String line : REAL_HISTORY_LINES)
		{
			final RunRecord rr = GSON.fromJson(line, RunRecord.class);
			assertNotNull(line, rr);
			assertEquals(3, rr.getSchema());
			assertNotNull(rr.getStart());
			assertNotNull(rr.getPerMob());
		}
	}

	@Test
	public void round18RealisedLinesParseAndDefaultToGe()
	{
		for (String line : REAL_REALISED_LINES)
		{
			final SessionHistory.RealisedEntry re = GSON.fromJson(line, SessionHistory.RealisedEntry.class);
			assertNotNull(re);
			assertNotNull(re.getRunStart());
			assertEquals("ge", re.kindOrGe());   // absent "kind" -> "ge", never null-crashes
			assertEquals(3388, re.getNet());
		}
	}

	@Test
	public void realHistoryAggregatesWithRealisedFolded()
	{
		final List<RunRecord> runs = new ArrayList<>();
		for (String line : REAL_HISTORY_LINES)
		{
			runs.add(GSON.fromJson(line, RunRecord.class));
		}
		final List<SessionHistory.RealisedEntry> realised = new ArrayList<>();
		for (String line : REAL_REALISED_LINES)
		{
			realised.add(GSON.fromJson(line, SessionHistory.RealisedEntry.class));
		}

		final SessionHistory.Snapshot s = SessionHistory.aggregate(runs, realised, Collections.emptyList());

		assertEquals(REAL_HISTORY_LINES.length, s.getRuns());
		assertFalse("mob rows built", s.getMobs().isEmpty());
		// the High alch pseudo-mob's 1,023,360 gp survives
		assertTrue(s.getMobs().stream().anyMatch(m -> "High alch".equals(m.getName()) && m.getGained() == 1_023_360));
		// the skill row survives
		assertTrue(s.getMobs().stream().anyMatch(m -> "Fletching".equals(m.getName())));
		// realised.jsonl folds onto the (deleted-from-these-samples) Ice troll mob -> lifetime
		// realised is 0 here because that run isn't in the sample, but the sales list is built
		assertEquals(1, s.getSales().size());
		assertEquals("ge", s.getSales().get(0).getKind());
	}

	@Test
	public void theActualUserFilesLoadIfPresent() throws IOException
	{
		final Path dir = Paths.get(System.getProperty("user.home"), ".runelite", "profit-loss-calculator");
		final Path hist = dir.resolve("history.jsonl");
		Assume.assumeTrue("no local history.jsonl - skipping", Files.exists(hist));

		final List<RunRecord> runs = new ArrayList<>();
		int lines = 0;
		int parsed = 0;
		for (String line : Files.readAllLines(hist, StandardCharsets.UTF_8))
		{
			if (line.trim().isEmpty())
			{
				continue;
			}
			lines++;
			final RunRecord rr = GSON.fromJson(line, RunRecord.class);
			if (rr != null && rr.getSchema() >= 3 && rr.getPerMob() != null)
			{
				parsed++;
				runs.add(rr);
			}
		}
		assertEquals("every schema-3 line parsed", lines, parsed);

		final List<SessionHistory.RealisedEntry> realised = new ArrayList<>();
		final Path rf = dir.resolve("realised.jsonl");
		if (Files.exists(rf))
		{
			for (String line : Files.readAllLines(rf, StandardCharsets.UTF_8))
			{
				if (line.trim().isEmpty())
				{
					continue;
				}
				final SessionHistory.RealisedEntry re = GSON.fromJson(line, SessionHistory.RealisedEntry.class);
				assertNotNull(re);
				assertNotNull(re.kindOrGe());
				realised.add(re);
			}
		}

		final SessionHistory.Snapshot s = SessionHistory.aggregate(runs, realised, Collections.emptyList());
		assertEquals(runs.size(), s.getRuns());
		assertFalse(s.getMobs().isEmpty());
		System.out.println("[HistoryCompatTest] loaded " + runs.size() + " runs, " + realised.size()
			+ " realised entries, " + s.getMobs().size() + " lifetime mob rows; net " + s.getNet()
			+ ", realised " + s.getRealisedNet());
	}
}
