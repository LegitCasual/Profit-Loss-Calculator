/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Locale;
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
	public void doesNotContainPluralTaskFamilyLabels()
	{
		// these are Slayer task/species group names, not exact single-NPC names - typing them
		// verbatim into an exact-match farm field would never match a real kill
		assertFalse(contains("Aberrant spectres"));
		assertFalse(contains("Dagannoth Kings"));
		assertFalse(contains("Cows"));
		assertFalse(contains("Crabs"));
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
