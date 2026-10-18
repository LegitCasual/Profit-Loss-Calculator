/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the plugin's real (private) trackOpponent / costMob / handleNpcKill against
 * hand-rolled java.lang.reflect.Proxy fakes of Client / WorldView / NPC / Player - no Mockito
 * on this project's test classpath.
 */
public class EncounterAddsPluginScenarioTest
{
	private ProfitLossCalculatorPlugin plugin;
	private Session session;
	private int tick;
	private final List<NPC> scene = new ArrayList<>();
	private NPC interacting;
	private boolean worldViewNull;
	private boolean trackLoot = true;

	// ------------------------------------------------------------------ fakes

	private static Object defaultFor(Class<?> t)
	{
		if (t == boolean.class)
		{
			return false;
		}
		if (t == int.class)
		{
			return 0;
		}
		if (t == long.class)
		{
			return 0L;
		}
		if (t == double.class)
		{
			return 0d;
		}
		if (t == float.class)
		{
			return 0f;
		}
		if (t == short.class)
		{
			return (short) 0;
		}
		if (t == byte.class)
		{
			return (byte) 0;
		}
		if (t == char.class)
		{
			return (char) 0;
		}
		if (t == String.class)
		{
			return "";
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> impl)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "hashCode":
					return System.identityHashCode(p);
				case "equals":
					return p == a[0];
				case "toString":
					return type.getSimpleName() + "@fake";
			}
			final Object r = impl.apply(m.getName(), a);
			return r != null ? r : defaultFor(m.getReturnType());
		});
	}

	private static NPC npc(String name)
	{
		return fake(NPC.class, (m, a) -> "getName".equals(m) ? name : null);
	}

	private NPC spawn(String name)
	{
		final NPC n = npc(name);
		scene.add(n);
		return n;
	}

	private void despawnAll(String name)
	{
		scene.removeIf(n -> name.equals(n.getName()));
	}

	@Before
	public void setUp() throws Exception
	{
		plugin = new ProfitLossCalculatorPlugin();
		final IndexedObjectSet<NPC> npcs = fake(IndexedObjectSet.class, (m, a) ->
		{
			if ("iterator".equals(m))
			{
				return new ArrayList<>(scene).iterator();
			}
			if ("stream".equals(m))
			{
				return new ArrayList<>(scene).stream();
			}
			return null;
		});
		final WorldView wv = fake(WorldView.class, (m, a) -> "npcs".equals(m) ? npcs : null);
		final Player me = fake(Player.class, (m, a) -> "getInteracting".equals(m) ? interacting : null);
		final Client client = fake(Client.class, (m, a) ->
		{
			switch (m)
			{
				case "getTickCount":
					return tick;
				case "getTopLevelWorldView":
					return worldViewNull ? null : wv;
				case "getLocalPlayer":
					return me;
				default:
					return null;
			}
		});
		final ProfitLossCalculatorConfig config = fake(ProfitLossCalculatorConfig.class,
			(m, a) -> "trackLoot".equals(m) ? trackLoot : null);

		set("client", client);
		set("config", config);
		set("logger", new SessionLogger(new Gson()));
		session = new Session(Instant.now());
		set("session", session);
		// refreshView() -> buildView() short-circuits to frozenView when sessionFinished; none of
		// the methods under test look at sessionFinished, so this keeps the heavy view out of it
		set("sessionFinished", true);
		set("frozenView", ProfitLossCalculatorPanel.View.builder().build());
		tick = 1000;
	}

	private void set(String field, Object value) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(plugin, value);
	}

	private Object call(String method, Class<?>[] types, Object... args) throws Exception
	{
		final Method m = ProfitLossCalculatorPlugin.class.getDeclaredMethod(method, types);
		m.setAccessible(true);
		try
		{
			return m.invoke(plugin, args);
		}
		catch (InvocationTargetException e)
		{
			throw (Exception) e.getCause();
		}
	}

	// ------------------------------------------------------------------ simulation steps

	/** One game tick: player is interacting with {@code foe} (or nobody), then spends {@code gp}. */
	private void tick(NPC foe, long gp) throws Exception
	{
		tick++;
		interacting = foe;
		call("trackOpponent", new Class<?>[0]);
		session.addMobCost((String) call("costMob", new Class<?>[0]), gp);
	}

	private void dies(NPC n) throws Exception
	{
		call("handleNpcKill", new Class<?>[]{NPC.class}, n);
	}

	private Map<String, Long> cost()
	{
		return session.getCostByMob();
	}

	private Map<String, Integer> kills()
	{
		return session.getKillsByMob();
	}

	// ------------------------------------------------------------------ scenarios

	@Test
	public void fullPhosaniKillPlainSession() throws Exception
	{
		final NPC boss = spawn("Phosani's Nightmare");
		tick(boss, 100);
		final List<NPC> totems = Arrays.asList(spawn("Totem"), spawn("Totem"), spawn("Totem"), spawn("Totem"));
		for (NPC t : totems)
		{
			tick(t, 10);
		}
		despawnAll("Totem");
		final NPC h1 = spawn("Husk");
		final NPC h2 = spawn("Husk");
		tick(h1, 20);
		dies(h1);
		tick += 3;
		tick(h2, 20);
		dies(h2);
		despawnAll("Husk");
		final NPC p = spawn("Parasite");
		tick(p, 30);
		dies(p);
		despawnAll("Parasite");
		final NPC sw = spawn("Sleepwalker");
		tick(sw, 5);
		dies(sw);
		despawnAll("Sleepwalker");
		tick(boss, 100);
		dies(boss);

		assertEquals(1, cost().size());
		assertEquals(Long.valueOf(315), cost().get("Phosani's Nightmare"));
		assertEquals(1, kills().size());
		assertEquals(Integer.valueOf(1), kills().get("Phosani's Nightmare"));
		assertEquals(1, session.getKillCount());
	}

	@Test
	public void phosaniTargetedFarmOpensOneBucket() throws Exception
	{
		session.setMode(Session.RunMode.TARGETED);
		session.addTargetMob("Phosani's Nightmare");
		final NPC boss = spawn("Phosani's Nightmare");
		final NPC husk = spawn("Husk");
		tick(husk, 50);
		dies(husk);
		dies(boss);
		assertEquals(1, session.getKills().size());
		assertEquals(Long.valueOf(50), cost().get("Phosani's Nightmare"));
	}

	@Test
	public void regularNightmare() throws Exception
	{
		final NPC boss = spawn("The Nightmare");
		final NPC husk = spawn("Husk");
		final NPC totem = spawn("Totem");
		tick(totem, 10);
		tick(husk, 10);
		dies(husk);
		dies(boss);
		assertEquals(Long.valueOf(20), cost().get("The Nightmare"));
		assertEquals(Integer.valueOf(1), kills().get("The Nightmare"));
		assertEquals(1, kills().size());
	}

	@Test
	public void grotesqueGuardians() throws Exception
	{
		final NPC dusk = spawn("Dusk");
		final NPC dawn = spawn("Dawn");
		tick(dawn, 10);
		dies(dawn);
		tick(dusk, 10);
		dies(dusk);
		assertEquals(Long.valueOf(20), cost().get("Dusk"));
		assertEquals(1, kills().size());
		assertEquals(Integer.valueOf(1), kills().get("Dusk"));
	}

	@Test
	public void colourTagsAndWhitespaceOnLiveNames() throws Exception
	{
		spawn("<col=ff0000>Phosani's Nightmare</col>  ");
		final NPC husk = spawn("<col=ffff00>Husk</col> ");
		tick(husk, 10);
		dies(husk);
		assertEquals(Long.valueOf(10), cost().get("Phosani's Nightmare"));
		assertTrue(kills().isEmpty());
	}

	@Test
	public void upperCaseAddName() throws Exception
	{
		spawn("Phosani's Nightmare");
		final NPC husk = spawn("HUSK");
		tick(husk, 10);
		dies(husk);
		assertEquals(Long.valueOf(10), cost().get("Phosani's Nightmare"));
		assertTrue(kills().isEmpty());
	}

	@Test
	public void stickyWindowAfterBossDies() throws Exception
	{
		final NPC boss = spawn("Phosani's Nightmare");
		final NPC husk = spawn("Husk");
		tick(husk, 10);
		// boss dies + despawns; player stops interacting. Costs within the 8-tick sticky window
		// still go to Phosani (the resolved name was cached at the last hit)
		dies(boss);
		despawnAll("Phosani's Nightmare");
		for (int i = 0; i < 7; i++)
		{
			tick(null, 1);
		}
		assertEquals(Long.valueOf(17), cost().get("Phosani's Nightmare"));
		tick(null, 1); // 8th tick still inside (<= 8)
		tick(null, 1); // 9th - falls out of the window
		assertEquals(Long.valueOf(18), cost().get("Phosani's Nightmare"));
		assertEquals(Long.valueOf(1), cost().get(""));
	}

	@Test
	public void bossGoneButStillHittingHusk() throws Exception
	{
		spawn("Phosani's Nightmare");
		final NPC husk = spawn("Husk");
		tick(husk, 10);
		despawnAll("Phosani's Nightmare");
		tick(husk, 10);
		dies(husk);
		assertEquals(Long.valueOf(10), cost().get("Phosani's Nightmare"));
		assertEquals(Long.valueOf(10), cost().get("Husk"));
		assertEquals(Integer.valueOf(1), kills().get("Husk"));
	}

	@Test
	public void royalTitansElementals() throws Exception
	{
		final NPC branda = spawn("Branda the Fire Queen");
		spawn("Eldric the Ice King");
		final NPC ice = spawn("Ice elemental");
		tick(ice, 10);
		dies(ice);
		assertEquals(Long.valueOf(10), cost().get("Eldric the Ice King"));
		scene.clear();
		final NPC ice2 = spawn("Ice elemental");
		tick(ice2, 10);
		dies(ice2);
		assertEquals(Long.valueOf(10), cost().get("Ice elemental"));
		assertEquals(Integer.valueOf(1), kills().get("Ice elemental"));
		assertNull(kills().get(branda.getName()));
	}

	@Test
	public void infernoAndCaves() throws Exception
	{
		spawn("TzKal-Zuk");
		final NPC jad = spawn("JalTok-Jad");
		final NPC healer = spawn("Yt-HurKot");
		tick(healer, 5);
		dies(healer);
		tick(jad, 5);
		dies(jad);
		assertEquals(Long.valueOf(10), cost().get("TzKal-Zuk"));
		assertTrue(kills().isEmpty());

		scene.clear();
		final NPC w68 = spawn("JalTok-Jad");
		tick += 5;
		tick(w68, 5);
		dies(w68);
		assertEquals(Integer.valueOf(1), kills().get("JalTok-Jad"));

		scene.clear();
		spawn("TzTok-Jad");
		final NPC caves = spawn("Yt-HurKot");
		tick(caves, 5);
		assertEquals(Long.valueOf(5), cost().get("TzTok-Jad"));
	}

	@Test
	public void collisionsElsewhereUnchanged() throws Exception
	{
		for (String n : new String[]{"Giant rat", "Spawn", "Golem", "Fire elemental"})
		{
			scene.clear();
			final NPC mob = spawn(n);
			spawn("Lizardman shaman");
			tick += 5;
			tick(mob, 1);
			dies(mob);
			assertEquals(n, Long.valueOf(1), cost().get(n));
			assertEquals(n, Integer.valueOf(1), kills().get(n));
		}
	}

	@Test
	public void nullWorldViewFallsBackToRawName() throws Exception
	{
		spawn("Phosani's Nightmare");
		final NPC husk = spawn("Husk");
		worldViewNull = true;
		tick(husk, 10);
		dies(husk);
		assertEquals(Long.valueOf(10), cost().get("Husk"));
		assertEquals(Integer.valueOf(1), kills().get("Husk"));
	}

	/** FINDING: a Targeted farm of an add name loses its kills whenever the add's boss is in the scene. */
	@Test
	public void targetedGiantRatFarmWithScurriusLoaded() throws Exception
	{
		session.setMode(Session.RunMode.TARGETED);
		session.addTargetMob("Giant rat");
		spawn("Scurrius");
		final NPC rat = spawn("Giant rat");
		tick(rat, 10);
		dies(rat);
		// the farm targets the add, not the boss - so the add keeps its own kill and cost
		assertEquals(1, session.getKills().size());
		assertEquals(Long.valueOf(10), cost().get("Giant rat"));
		assertNull(cost().get("Scurrius"));

		// same farm with Scurrius not loaded counts normally
		despawnAll("Scurrius");
		final NPC rat2 = spawn("Giant rat");
		tick += 5;
		tick(rat2, 10);
		dies(rat2);
		assertEquals(2, session.getKills().size());
	}

	/** Same thing via a farm explicitly targeting an add of a co-located boss: Dawn. */
	@Test
	public void targetedAddFarmDawn() throws Exception
	{
		session.setMode(Session.RunMode.TARGETED);
		session.addTargetMob("Dawn");
		spawn("Dusk");
		final NPC dawn = spawn("Dawn");
		dies(dawn);
		assertEquals(1, session.getKills().size());
	}
}
