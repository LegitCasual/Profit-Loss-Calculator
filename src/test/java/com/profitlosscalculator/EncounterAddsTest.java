/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EncounterAddsTest
{
	/** The reported bug: supplies spent on Husks mid-Phosani belong to Phosani. */
	@Test
	public void phosaniAddsResolveToPhosani()
	{
		final java.util.List<String> scene = Arrays.asList("Phosani's Nightmare", "Husk", "Husk");
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("Husk", scene));
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("Parasite", scene));
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("Sleepwalker", scene));
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("Totem", scene));
	}

	@Test
	public void sharedAddsPickWhicheverNightmareIsPresent()
	{
		assertEquals("The Nightmare",
			EncounterAdds.resolve("Husk", Arrays.asList("The Nightmare", "Husk")));
	}

	@Test
	public void jadHealersFollowWhicheverJadBossIsPresent()
	{
		assertEquals("TzTok-Jad", EncounterAdds.resolve("Yt-HurKot", Arrays.asList("TzTok-Jad")));
		assertEquals("TzKal-Zuk", EncounterAdds.resolve("Yt-HurKot", Arrays.asList("TzKal-Zuk", "JalTok-Jad")));
	}

	@Test
	public void krakenTentaclesFoldWhileTheBossIsStillAWhirlpool()
	{
		assertEquals("Kraken",
			EncounterAdds.resolve("Enormous Tentacle", Arrays.asList("Whirlpool", "Enormous Tentacle")));
		assertEquals("Whirlpool", EncounterAdds.resolve("Whirlpool", Arrays.asList("Whirlpool")));
	}

	@Test
	public void titanElementalsFollowTheirOwnTitan()
	{
		final java.util.List<String> scene = Arrays.asList("Branda the Fire Queen", "Eldric the Ice King");
		assertEquals("Branda the Fire Queen", EncounterAdds.resolve("Fire elemental", scene));
		assertEquals("Eldric the Ice King", EncounterAdds.resolve("Ice elemental", scene));
	}

	@Test
	public void addWithoutItsBossStaysItself()
	{
		assertEquals("Husk", EncounterAdds.resolve("Husk", Collections.singletonList("Husk")));
		assertEquals("Spawn", EncounterAdds.resolve("Spawn", Collections.emptyList()));
	}

	@Test
	public void nonAddsPassThrough()
	{
		final java.util.List<String> scene = Collections.singletonList("Phosani's Nightmare");
		assertEquals("Phosani's Nightmare", EncounterAdds.resolve("Phosani's Nightmare", scene));
		assertEquals("Goblin", EncounterAdds.resolve("Goblin", scene));
		assertNull(EncounterAdds.resolve(null, scene));
		assertFalse(EncounterAdds.isAdd("Goblin"));
		assertTrue(EncounterAdds.isAdd("husk"));
	}
}
