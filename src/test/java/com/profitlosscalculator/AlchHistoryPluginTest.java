/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

/**
 * High Alching looted items, from the plugin's real (private) Stop rollup through to the lifetime
 * ledger. The live panel showed the alch coins as realised, but History and the dashboard showed
 * only the nature-rune cost: the rollup stripped alched items out of the run before it was
 * written, so the alch replayed at Stop had no looted units left to realise against.
 */
public class AlchHistoryPluginTest
{
	private static final int GE = 38_000;
	private static final int HA = 38_400;
	private static final String MOB = "Plundered salvage";

	private ProfitLossCalculatorPlugin plugin;
	private Session session;

	@Before
	public void setUp() throws Exception
	{
		plugin = new ProfitLossCalculatorPlugin();
		set("config", Proxy.newProxyInstance(ProfitLossCalculatorConfig.class.getClassLoader(),
			new Class<?>[]{ProfitLossCalculatorConfig.class}, (p, m, a) ->
			{
				switch (m.getName())
				{
					case "incomeValuation":
						return IncomeValuation.Mode.GE;
					case "countUncollectedDrops":
						return false;
					case "ignoreIncomeBelow":
						return 0;
					default:
						return null;
				}
			}));
		set("priceLookup", new IncomeValuation.PriceLookup()
		{
			@Override
			public int ge(int itemId)
			{
				return itemId == ItemID.RUNE_PLATEBODY ? GE : 0;
			}

			@Override
			public int ha(int itemId)
			{
				return itemId == ItemID.RUNE_PLATEBODY ? HA : 0;
			}
		});
		session = new Session(Instant.now());
		set("session", session);
	}

	private void set(String field, Object value) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(plugin, value);
	}

	@SuppressWarnings("unchecked")
	private Map<String, RunRecord.MobRun> perMobRollup() throws Exception
	{
		final Method m = ProfitLossCalculatorPlugin.class.getDeclaredMethod("perMobRollup");
		m.setAccessible(true);
		return (Map<String, RunRecord.MobRun>) m.invoke(plugin);
	}

	@Test
	public void alchedLootIsRealisedInHistory() throws Exception
	{
		// three rune platebodies out of salvage, two of them alched while not in combat
		session.add(new IncomeEvent(IncomeEvent.Type.EVENT_LOOT, Instant.now(), MOB,
			Collections.singletonMap(ItemID.RUNE_PLATEBODY, 3)));
		session.addAlchedItem(ItemID.RUNE_PLATEBODY, 2);
		session.addMobCost(SessionHistory.UNATTRIBUTED, 2 * 300);

		final Map<String, RunRecord.MobRun> perMob = perMobRollup();
		final RunRecord.MobRun salvage = perMob.get(MOB);
		assertNotNull(salvage);
		assertEquals("alched loot stays in the run's gained", 3L * GE, salvage.getGained());
		assertEquals("alched loot isn't left on the floor", salvage.getGained(), salvage.getDropped());

		// what Stop does: write the run, then replay each alch against the ledger
		final List<RunRecord> entries = Collections.singletonList(new RunRecord(SessionHistory.SCHEMA,
			"session", "2026-10-31T00:00:00Z", "end", 600, "GE", perMob));
		final List<SessionHistory.RealisedEntry> realised = new ArrayList<>();
		for (int i = 0; i < 2; i++)
		{
			final SessionHistory.RealisedEntry re = SessionHistory.matchRealised(entries, realised,
				"alch", ItemID.RUNE_PLATEBODY, 1, HA, 0, "t");
			assertNotNull("alch " + (i + 1) + " matched no looted units", re);
			realised.add(re);
		}

		final SessionHistory.Snapshot s = SessionHistory.aggregate(entries, realised);
		assertEquals(2L * HA, s.getRealisedNet());
		// two platebodies at their alch coins, the third still at its loot snapshot, less the runes
		assertEquals(2L * HA + GE - 600, s.getActualNet());
	}

	@SuppressWarnings("unchecked")
	private void alch(int itemId, long coins) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField("pendingAlch");
		f.setAccessible(true);
		((List<long[]>) f.get(plugin)).add(new long[]{itemId, coins});
	}

	@SuppressWarnings("unchecked")
	private void priorLoot(int itemId, long qty) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField("priorLootAvail");
		f.setAccessible(true);
		((Map<Integer, Long>) f.get(plugin)).put(itemId, qty);
	}

	private long realisedSoFar() throws Exception
	{
		final Method m = ProfitLossCalculatorPlugin.class.getDeclaredMethod("realisedSoFar");
		m.setAccessible(true);
		return (long) m.invoke(plugin);
	}

	@Test
	public void liveRealisedCountsOnlyAlchsOfLoot() throws Exception
	{
		// one platebody looted this run; two more alched from the bank with none in history
		session.add(new IncomeEvent(IncomeEvent.Type.EVENT_LOOT, Instant.now(), MOB,
			Collections.singletonMap(ItemID.RUNE_PLATEBODY, 1)));
		alch(ItemID.RUNE_PLATEBODY, HA);
		alch(ItemID.RUNE_PLATEBODY, HA);
		alch(ItemID.RUNE_FULL_HELM, 21_120);
		assertEquals(HA, realisedSoFar());

		// history reports one more platebody looted in an earlier run, not yet sold or alched
		priorLoot(ItemID.RUNE_PLATEBODY, 1);
		assertEquals(2L * HA, realisedSoFar());
	}

	@Test
	public void unrealisedOutsideSkipsTheLiveRunAndRealisedUnits()
	{
		final RunRecord.MobRun scurrius = new RunRecord.MobRun(1, GE, GE, 0, 0,
			Collections.singletonList(new long[]{ItemID.RUNE_PLATEBODY, 3, 3L * GE}));
		final RunRecord earlier = new RunRecord(SessionHistory.SCHEMA, "session", "earlier", "end", 60, "GE",
			Collections.singletonMap("Scurrius", scurrius));
		// the live ambient run, already checkpointed with platebodies of its own
		final RunRecord live = new RunRecord(SessionHistory.SCHEMA, "session", "live", "end", 60, "GE",
			Collections.singletonMap("Scurrius", scurrius));
		// one platebody since sold on the GE
		final List<SessionHistory.RealisedEntry> realised = new ArrayList<>();
		realised.add(SessionHistory.matchRealised(java.util.Arrays.asList(earlier, live), realised,
			"ge", ItemID.RUNE_PLATEBODY, 1, GE, 0, "t"));

		assertEquals(2, SessionHistory.unrealisedOutside(java.util.Arrays.asList(earlier, live), realised,
			ItemID.RUNE_PLATEBODY, "live"));
	}
}
