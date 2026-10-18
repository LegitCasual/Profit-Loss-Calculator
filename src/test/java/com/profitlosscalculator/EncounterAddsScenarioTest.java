/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Fight-timeline simulations at the {@link EncounterAdds} level: each tick names what the
 * player is hitting and what is in the scene; costs go to the resolved name, and a death
 * counts as a kill only when the dying NPC resolves to itself (mirrors the plugin wiring).
 */
public class EncounterAddsScenarioTest
{
	/** Minimal model of trackOpponent / handleNpcKill. */
	private static final class Fight
	{
		final Map<String, Long> cost = new LinkedHashMap<>();
		final Map<String, Integer> kills = new LinkedHashMap<>();

		void hit(String target, List<String> scene, long gp)
		{
			cost.merge(EncounterAdds.resolve(target, scene), gp, Long::sum);
		}

		void dies(String npc, List<String> scene)
		{
			if (EncounterAdds.resolve(npc, scene).equals(npc))
			{
				kills.merge(npc, 1, Integer::sum);
			}
		}
	}

	private static List<String> scene(String... names)
	{
		return new ArrayList<>(Arrays.asList(names));
	}

	@Test
	public void fullPhosaniKill()
	{
		final String boss = "Phosani's Nightmare";
		final Fight f = new Fight();
		f.hit(boss, scene(boss), 100);
		// totem phase: boss in the scene, 4 totems
		final List<String> totems = scene(boss, "Totem", "Totem", "Totem", "Totem");
		for (int i = 0; i < 4; i++)
		{
			f.hit("Totem", totems, 10);
		}
		// husk phase
		final List<String> husks = scene(boss, "Husk", "Husk");
		f.hit("Husk", husks, 20);
		f.dies("Husk", husks);
		f.dies("Husk", husks);
		// parasites
		final List<String> para = scene(boss, "Parasite", "Parasite");
		f.hit("Parasite", para, 30);
		f.dies("Parasite", para);
		// sleepwalkers
		final List<String> sw = scene(boss, "Sleepwalker", "Sleepwalker", "Sleepwalker");
		f.hit("Sleepwalker", sw, 5);
		f.dies("Sleepwalker", sw);
		f.hit(boss, scene(boss), 100);
		f.dies(boss, scene(boss));

		assertEquals(Collections.singletonMap(boss, 295L), f.cost);
		assertEquals(Collections.singletonMap(boss, 1), f.kills);
	}

	@Test
	public void regularNightmareWithOtherPlayers()
	{
		final String boss = "The Nightmare";
		final Fight f = new Fight();
		// other players are Players, not NPCs, so the NPC scene is just the boss and adds
		final List<String> s = scene(boss, "Husk", "Husk", "Parasite", "Totem", "Totem", "Totem", "Totem");
		f.hit("Totem", s, 10);
		f.hit("Husk", s, 10);
		f.dies("Husk", s);
		f.hit("Parasite", s, 10);
		f.dies("Parasite", s);
		f.dies(boss, scene(boss));
		assertEquals(Collections.singletonMap(boss, 30L), f.cost);
		assertEquals(Collections.singletonMap(boss, 1), f.kills);
	}

	@Test
	public void grotesqueGuardians()
	{
		final Fight f = new Fight();
		final List<String> both = scene("Dawn", "Dusk");
		f.hit("Dusk", both, 10);
		f.hit("Dawn", both, 10);
		f.dies("Dawn", both); // Dawn dies first, Dusk still present
		f.dies("Dusk", scene("Dusk", "Dawn"));
		assertEquals(Collections.singletonMap("Dusk", 20L), f.cost);
		assertEquals(Collections.singletonMap("Dusk", 1), f.kills);
	}

	@Test
	public void royalTitans()
	{
		final String branda = "Branda the Fire Queen";
		final String eldric = "Eldric the Ice King";
		assertEquals(branda, EncounterAdds.resolve("Fire elemental", scene(branda, eldric, "Fire elemental")));
		assertEquals(eldric, EncounterAdds.resolve("Ice elemental", scene(branda, eldric)));
		// Branda dead and despawned; Eldric still up
		assertEquals(eldric, EncounterAdds.resolve("Fire elemental", scene(eldric, "Fire elemental")));
		// both titans gone
		assertEquals("Ice elemental", EncounterAdds.resolve("Ice elemental", scene("Ice elemental")));
		// each elemental follows its own titan while both are up, whatever the scene order
		assertEquals(eldric, EncounterAdds.resolve("Ice elemental", scene(branda, eldric)));
		assertEquals(branda, EncounterAdds.resolve("Fire elemental", scene(eldric, branda)));
	}

	@Test
	public void infernoAndFightCaves()
	{
		assertEquals("TzTok-Jad", EncounterAdds.resolve("Yt-HurKot", scene("TzTok-Jad", "Yt-HurKot")));
		assertEquals("TzKal-Zuk", EncounterAdds.resolve("Yt-HurKot", scene("TzKal-Zuk", "JalTok-Jad", "Yt-HurKot")));
		assertEquals("TzKal-Zuk", EncounterAdds.resolve("JalTok-Jad", scene("TzKal-Zuk", "JalTok-Jad")));
		assertEquals("TzKal-Zuk", EncounterAdds.resolve("Jal-MejJak", scene("TzKal-Zuk")));
		// wave 68: three Jads, no Zuk
		final List<String> w68 = scene("JalTok-Jad", "JalTok-Jad", "JalTok-Jad", "Yt-HurKot", "Yt-HurKot");
		assertEquals("JalTok-Jad", EncounterAdds.resolve("JalTok-Jad", w68));
		assertEquals("Yt-HurKot", EncounterAdds.resolve("Yt-HurKot", w68));
		// earlier Inferno waves: Jal-Xil / Jal-Zek without Zuk stay themselves
		assertEquals("Jal-Zek", EncounterAdds.resolve("Jal-Zek", scene("Jal-Zek", "Jal-Xil")));
		// Zuk wave: JalTok-Jad dying is not a kill
		final Fight f = new Fight();
		f.dies("JalTok-Jad", scene("TzKal-Zuk", "JalTok-Jad"));
		f.dies("TzKal-Zuk", scene("TzKal-Zuk"));
		assertEquals(Collections.singletonMap("TzKal-Zuk", 1), f.kills);
	}

	@Test
	public void genericNameCollisionsOutsideTheirBoss()
	{
		assertEquals("Giant rat", EncounterAdds.resolve("Giant rat", scene("Giant rat", "Rat", "Guard")));
		assertEquals("Spawn", EncounterAdds.resolve("Spawn", scene("Lizardman shaman", "Spawn")));
		assertEquals("Golem", EncounterAdds.resolve("Golem", scene("Golem")));
		assertEquals("Fire elemental", EncounterAdds.resolve("Fire elemental", scene("Fire elemental", "Earth elemental")));
		assertEquals("Flower", EncounterAdds.resolve("Flower", scene()));
		assertEquals("Lost Soul", EncounterAdds.resolve("Lost Soul", scene("Ghost")));
	}

	@Test
	public void nameFormatting()
	{
		final List<String> s = scene("Phosani's Nightmare");
		assertTrue(EncounterAdds.isAdd("HUSK "));
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("HUSK ", s));
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("  husk", s));
		// scene names are matched case-insensitively
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("Husk", scene("PHOSANI'S NIGHTMARE")));
		// EncounterAdds itself does NOT strip colour tags - the plugin must (it does, via Text.removeTags)
		assertFalse(EncounterAdds.isAdd("<col=ffff00>Husk</col>"));
		assertEquals("<col=ffff00>Husk</col>", EncounterAdds.resolve("<col=ffff00>Husk</col>", s));
		// ... nor tags / whitespace on the scene side
		assertEquals("Husk", EncounterAdds.resolve("Husk", scene("<col=ffff00>Phosani's Nightmare</col>")));
		assertEquals("Husk", EncounterAdds.resolve("Husk", scene("Phosani's Nightmare ")));
	}

	@Test
	public void unresolvedAddKeepsCallerSpelling()
	{
		// resolve returns the input instance unchanged when not resolved; the plugin's
		// "is it an add?" test is !encounterName(name).equals(name), which relies on this
		final String n = "HUSK ";
		assertTrue(n == EncounterAdds.resolve(n, scene()));
	}

	@Test
	public void phosaniDespawnMidFight()
	{
		final Fight f = new Fight();
		f.hit("Husk", scene("Phosani's Nightmare", "Husk"), 10);
		// boss despawned, player still hitting a lingering Husk
		f.hit("Husk", scene("Husk"), 10);
		f.dies("Husk", scene("Husk"));
		final Map<String, Long> expectCost = new LinkedHashMap<>();
		expectCost.put("Phosani's Nightmare", 10L);
		expectCost.put("Husk", 10L);
		assertEquals(expectCost, f.cost);
		assertEquals(Collections.singletonMap("Husk", 1), f.kills);
	}

	@Test
	public void addFarmedOnPurposeWithBossLoadedIsSwallowed()
	{
		// a Giant rat killed while Scurrius happens to be in the scene resolves to Scurrius
		final Fight f = new Fight();
		f.dies("Giant rat", scene("Scurrius", "Giant rat"));
		assertTrue(f.kills.isEmpty());
	}
}
