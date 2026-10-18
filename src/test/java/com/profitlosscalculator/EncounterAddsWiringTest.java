/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the plugin's opponent tracking / kill handling with hand-rolled {@link Proxy} fakes
 * (the project has no mocking library) to check the {@link EncounterAdds} wiring:
 * {@code trackOpponent()} -> {@code costMob()} and {@code onActorDeath} -> {@code handleNpcKill}.
 */
public class EncounterAddsWiringTest
{
	private ProfitLossCalculatorPlugin plugin;
	private Session session;
	private final List<NPC> sceneNpcs = new ArrayList<>();
	private Actor interacting;
	private int tick = 100;
	private boolean worldViewNull;

	@Before
	public void setUp() throws Exception
	{
		plugin = new ProfitLossCalculatorPlugin();
		session = new Session(Instant.now());
		set("session", session);
		set("client", fakeClient());
		set("config", proxy(ProfitLossCalculatorConfig.class, (p, m, a) ->
			"trackLoot".equals(m.getName()) ? Boolean.TRUE : defaultFor(m)));
	}

	// ------------------------------------------------------------------ cost attribution

	@Test
	public void huskCostLandsOnPhosaniWhileSheIsPresent() throws Exception
	{
		final NPC husk = npc("Husk");
		sceneNpcs.addAll(Arrays.asList(npc("Phosani's Nightmare"), husk, npc(null)));
		interacting = husk;

		call("trackOpponent");
		session.addMobCost((String) call("costMob"), 1234);

		assertEquals(Long.valueOf(1234), session.getCostByMob().get("Phosani's Nightmare"));
		assertNull(session.getCostByMob().get("Husk"));
	}

	@Test
	public void huskCostStaysOnHuskWithoutPhosani() throws Exception
	{
		final NPC husk = npc("Husk");
		sceneNpcs.add(husk);
		interacting = husk;

		call("trackOpponent");
		session.addMobCost((String) call("costMob"), 500);

		assertEquals(Long.valueOf(500), session.getCostByMob().get("Husk"));
		assertNull(session.getCostByMob().get("Phosani's Nightmare"));
	}

	@Test
	public void farmTargetingTheAddItselfKeepsItsCost() throws Exception
	{
		session.setMode(Session.RunMode.TARGETED);
		session.addTargetMob("Giant rat");
		final NPC rat = npc("Giant rat");
		sceneNpcs.addAll(Arrays.asList(npc("Scurrius"), rat));
		interacting = rat;

		call("trackOpponent");
		assertEquals("Giant rat", call("costMob"));
	}

	@Test
	public void farmTargetingTheBossStillFoldsItsAdds() throws Exception
	{
		session.setMode(Session.RunMode.TARGETED);
		session.addTargetMob("Scurrius");
		final NPC rat = npc("Giant rat");
		sceneNpcs.addAll(Arrays.asList(npc("Scurrius"), rat));
		interacting = rat;

		call("trackOpponent");
		assertEquals("Scurrius", call("costMob"));
	}

	@Test
	public void taggedBossNameStillResolves() throws Exception
	{
		final NPC husk = npc("<col=ffff00>Husk</col>");
		sceneNpcs.addAll(Arrays.asList(npc("<col=ff0000>Phosani's Nightmare</col>"), husk));
		interacting = husk;

		call("trackOpponent");
		assertEquals("Phosani's Nightmare", call("costMob"));
	}

	@Test
	public void nullWorldViewFallsBackToAddName() throws Exception
	{
		worldViewNull = true;
		interacting = npc("Husk");

		call("trackOpponent");
		assertEquals("Husk", call("costMob"));
	}

	@Test
	public void nonAddOpponentNeverWalksTheScene() throws Exception
	{
		// a WorldView that blows up proves the scene is only walked for known add names
		set("client", proxy(Client.class, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getLocalPlayer":
					return fakePlayer();
				case "getTickCount":
					return tick;
				case "getTopLevelWorldView":
					throw new AssertionError("scene walked for a non-add");
				default:
					return defaultFor(m);
			}
		}));
		interacting = npc("Phosani's Nightmare");
		call("trackOpponent");
		assertEquals("Phosani's Nightmare", call("costMob"));
	}

	@Test
	public void costStaysOnBossForStickyWindowAfterSwitchingBackToBoss() throws Exception
	{
		final NPC boss = npc("Phosani's Nightmare");
		final NPC husk = npc("Husk");
		sceneNpcs.addAll(Arrays.asList(boss, husk));
		interacting = husk;
		call("trackOpponent");
		interacting = null; // stopped attacking - sticky window keeps the boss
		tick += 3;
		call("trackOpponent");
		assertEquals("Phosani's Nightmare", call("costMob"));
	}

	// ------------------------------------------------------------------ kill counting

	@Test
	public void huskDeathDoesNotBumpAKillWhilePhosaniIsPresent() throws Exception
	{
		final NPC husk = npc("Husk");
		sceneNpcs.addAll(Arrays.asList(npc("Phosani's Nightmare"), husk));

		plugin.onActorDeath(new ActorDeath(husk));

		assertEquals(0, session.getKillCount());
		assertTrue(session.getKillsByMob().isEmpty());
	}

	@Test
	public void huskDeathCountsWithoutPhosani() throws Exception
	{
		final NPC husk = npc("Husk");
		sceneNpcs.add(husk);

		deathTolerantOfViewRefresh(husk);

		assertEquals(1, session.getKillCount());
		assertEquals(Integer.valueOf(1), session.getKillsByMob().get("Husk"));
	}

	@Test
	public void bossDeathStillCounts() throws Exception
	{
		final NPC boss = npc("Phosani's Nightmare");
		sceneNpcs.addAll(Arrays.asList(boss, npc("Husk")));

		deathTolerantOfViewRefresh(boss);

		assertEquals(Integer.valueOf(1), session.getKillsByMob().get("Phosani's Nightmare"));
		assertNull(session.getKillsByMob().get("Husk"));
	}

	/** A counted kill calls refreshView(), whose buildView() needs injected services this test
	 *  doesn't wire - the kill is booked before that, so tolerate a failure from there only. */
	private void deathTolerantOfViewRefresh(NPC npc)
	{
		try
		{
			plugin.onActorDeath(new ActorDeath(npc));
		}
		catch (RuntimeException e)
		{
			boolean fromView = false;
			for (StackTraceElement el : e.getStackTrace())
			{
				if ("buildView".equals(el.getMethodName()) || "refreshView".equals(el.getMethodName()))
				{
					fromView = true;
					break;
				}
			}
			if (!fromView)
			{
				throw e;
			}
		}
	}

	// ------------------------------------------------------------------ fakes

	private Client fakeClient()
	{
		return proxy(Client.class, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getLocalPlayer":
					return fakePlayer();
				case "getTickCount":
					return tick;
				case "getTopLevelWorldView":
					return worldViewNull ? null : fakeWorldView();
				default:
					return defaultFor(m);
			}
		});
	}

	private Player fakePlayer()
	{
		return proxy(Player.class, (p, m, a) ->
			"getInteracting".equals(m.getName()) ? interacting : defaultFor(m));
	}

	private WorldView fakeWorldView()
	{
		return proxy(WorldView.class, (p, m, a) ->
		{
			if ("npcs".equals(m.getName()))
			{
				final List<NPC> snapshot = new ArrayList<>(sceneNpcs);
				return proxy(IndexedObjectSet.class, (p2, m2, a2) ->
				{
					switch (m2.getName())
					{
						case "iterator":
							return snapshot.iterator();
						case "stream":
							return snapshot.stream();
						case "spliterator":
							return snapshot.spliterator();
						case "byIndex":
							return snapshot.get((Integer) a2[0]);
						default:
							return defaultFor(m2);
					}
				});
			}
			return defaultFor(m);
		});
	}

	private static NPC npc(String name)
	{
		return proxy(NPC.class, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getName":
					return name;
				case "equals":
					return p == a[0];
				case "hashCode":
					return System.identityHashCode(p);
				case "toString":
					return "NPC(" + name + ")";
				default:
					return defaultFor(m);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler h)
	{
		return (T) Proxy.newProxyInstance(EncounterAddsWiringTest.class.getClassLoader(), new Class<?>[]{type}, h);
	}

	private static Object defaultFor(Method m)
	{
		final Class<?> r = m.getReturnType();
		if (!r.isPrimitive() || r == void.class)
		{
			if (r == List.class)
			{
				return Collections.emptyList();
			}
			return null;
		}
		if (r == boolean.class)
		{
			return false;
		}
		if (r == char.class)
		{
			return '\0';
		}
		if (r == long.class)
		{
			return 0L;
		}
		if (r == double.class)
		{
			return 0d;
		}
		if (r == float.class)
		{
			return 0f;
		}
		if (r == byte.class)
		{
			return (byte) 0;
		}
		if (r == short.class)
		{
			return (short) 0;
		}
		return 0;
	}

	private void set(String field, Object value) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(plugin, value);
	}

	private Object call(String method) throws Exception
	{
		final Method m = ProfitLossCalculatorPlugin.class.getDeclaredMethod(method);
		m.setAccessible(true);
		try
		{
			return m.invoke(plugin);
		}
		catch (InvocationTargetException e)
		{
			if (e.getCause() instanceof Exception)
			{
				throw (Exception) e.getCause();
			}
			throw (Error) e.getCause();
		}
	}
}
