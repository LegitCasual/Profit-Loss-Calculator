/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.List;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KnownMobNamesTest
{
	private static boolean contains(String name)
	{
		return KnownMobNames.all().stream().anyMatch(n -> n.equalsIgnoreCase(name));
	}

	@Test
	public void containsDukeSucellusForTheDukePrefix()
	{
		final String needle = "duke";
		assertTrue(KnownMobNames.all().stream()
			.anyMatch(n -> n.toLowerCase(Locale.ROOT).startsWith(needle)
				&& n.equalsIgnoreCase("Duke Sucellus")));
	}

	@Test
	public void containsKnownIndividualBossesAndAliases()
	{
		assertTrue(contains("Vardorvis"));
		assertTrue(contains("Vorkath"));
		assertTrue(contains("Callisto"));
		assertTrue(contains("Nechryarch"));
		assertTrue(contains("Dharok the Wretched"));
	}

	@Test
	public void containsBossesThatHaveNoSlayerTask()
	{
		assertTrue(contains("Nex"));
		assertTrue(contains("Corporeal Beast"));
		assertTrue(contains("The Nightmare"));
		assertTrue(contains("Phosani's Nightmare"));
		assertTrue(contains("Blood Moon"));
		assertTrue(contains("Eclipse Moon"));
		assertTrue(contains("Dagannoth Rex"));
		assertTrue(contains("The Hueycoatl"));
		assertTrue(contains("Scurrius"));
		assertTrue(contains("Yama"));
		assertTrue(contains("Doom of Mokhaiotl"));
		assertTrue(contains("Crazy archaeologist"));
		assertTrue(contains("Sol Heredit"));
		assertTrue(contains("Crystalline Hunllef"));
	}

	@Test
	public void bossNamesDoNotCarryASlayerAssignmentThePrefix()
	{
		// the NPC is "Kalphite Queen" etc - the Slayer task calls it "The Kalphite Queen", and
		// storing that would silently never match a kill
		assertFalse(contains("The Kalphite Queen"));
		assertFalse(contains("The King Black Dragon"));
		assertFalse(contains("The Alchemical Hydra"));
		assertFalse(contains("The Giant Mole"));
		assertFalse(contains("The Chaos Fanatic"));
		assertTrue(contains("Kalphite Queen"));
		assertTrue(contains("Alchemical Hydra"));
		// ...but these NPCs really are named with "The"
		assertTrue(contains("The Whisperer"));
		assertTrue(contains("The Leviathan"));
	}

	@Test
	public void doesNotContainPluralTaskFamilyLabels()
	{
		// Slayer task/species group names, not exact single-NPC names
		assertFalse(contains("Aberrant spectres"));
		assertFalse(contains("Cows"));
		assertFalse(contains("Crabs"));
	}

	@Test
	public void collectiveLabelsExpandToTheirBosses()
	{
		assertEquals(
			java.util.Arrays.asList("Blood Moon", "Blue Moon", "Eclipse Moon"),
			KnownMobNames.expand("Moons of Peril"));
		assertEquals(
			java.util.Arrays.asList("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"),
			KnownMobNames.expand("dagannoth kings"));
		// a leading "The " on the label is optional
		assertEquals(2, KnownMobNames.expand("Royal Titans").size());
	}

	@Test
	public void expandPassesThroughAnythingThatIsNotALabel()
	{
		assertEquals(java.util.Collections.singletonList("Vorkath"), KnownMobNames.expand("  Vorkath  "));
		assertTrue(KnownMobNames.expand("").isEmpty());
		assertTrue(KnownMobNames.expand(null).isEmpty());
	}

	@Test
	public void everyCollectiveLabelIsInTheSuggestionListAndHasKnownMembers()
	{
		for (java.util.Map.Entry<String, List<String>> g : KnownMobNames.groups().entrySet())
		{
			assertTrue("label listed: " + g.getKey(), contains(g.getKey()));
			for (String member : g.getValue())
			{
				assertTrue(g.getKey() + " member listed: " + member, contains(member));
			}
		}
	}

	@Test
	public void hasNoDuplicatesCaseInsensitive()
	{
		final java.util.Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (String name : KnownMobNames.all())
		{
			assertTrue("duplicate: " + name, seen.add(name));
		}
	}
}
