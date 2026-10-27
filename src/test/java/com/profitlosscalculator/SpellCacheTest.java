/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Holds {@link Spell} to the game's own data: {@code spell-runes.tsv} is every spell obj in the
 * OSRS cache with its button and rune params (see the file header). Catches a mistyped rune
 * count, a spell wired to the wrong button, and a rune-costing spell nothing charges for.
 */
public class SpellCacheTest
{
	/** Display names deliberately not the cache's - the cache name is shared with another spell. */
	private static final Set<Spell> RENAMED = Collections.singleton(Spell.APE_ATOLL_DUNGEON_TELEPORT);

	private static final class Row
	{
		int button;
		int book;
		String name;
		/** requirement item id -&gt; qty, runes and any required non-rune item alike */
		Map<Integer, Integer> req = new HashMap<>();
	}

	private static final Map<Integer, Row> BY_BUTTON = new HashMap<>();
	private static final Map<Integer, Rune> RUNE_BY_ITEM = new HashMap<>();

	@BeforeClass
	public static void load() throws Exception
	{
		for (Rune r : Rune.values())
		{
			RUNE_BY_ITEM.put(r.getItemId(), r);
		}
		try (InputStream in = SpellCacheTest.class.getResourceAsStream("spell-runes.tsv"))
		{
			assertNotNull("spell-runes.tsv missing from test resources", in);
			final BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = br.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				final String[] col = line.split("\t", -1);
				final Row row = new Row();
				row.button = Integer.parseInt(col[0]);
				row.book = Integer.parseInt(col[1]);
				row.name = col[2];
				if (!col[3].isEmpty())
				{
					for (String pair : col[3].split(","))
					{
						final String[] kv = pair.split(":");
						row.req.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
					}
				}
				BY_BUTTON.put(row.button, row);
			}
		}
		assertTrue("fixture looks truncated", BY_BUTTON.size() > 150);
	}

	/** The cache row's runes only - required staves / orbs / bananas aren't runes and aren't charged. */
	private static Map<Rune, Integer> runes(Row row)
	{
		final Map<Rune, Integer> out = new EnumMap<>(Rune.class);
		row.req.forEach((id, qty) ->
		{
			final Rune r = RUNE_BY_ITEM.get(id);
			if (r != null)
			{
				out.put(r, qty);
			}
		});
		return out;
	}

	@Test
	public void everySpellMatchesTheCacheRuneCost()
	{
		final List<String> wrong = new ArrayList<>();
		for (Spell s : Spell.values())
		{
			final Row row = BY_BUTTON.get(s.getComponentId());
			if (row == null)
			{
				wrong.add(s + ": no spell obj in the cache for button " + s.getComponentId());
			}
			else if (!runes(row).equals(s.getRunes()))
			{
				wrong.add(s + ": plugin " + s.getRunes() + " vs cache " + runes(row));
			}
		}
		assertTrue(String.join("\n", wrong), wrong.isEmpty());
	}

	@Test
	public void everySpellIsNamedAsInGame()
	{
		// byName resolves autocasts from the cache's SPELL_NAME, so the two have to agree
		for (Spell s : Spell.values())
		{
			if (!RENAMED.contains(s))
			{
				assertEquals(s.toString(), BY_BUTTON.get(s.getComponentId()).name, s.getDisplayName());
			}
		}
	}

	@Test
	public void everyRuneCostingSpellInTheGameIsPriced()
	{
		final List<String> missing = new ArrayList<>();
		for (Row row : BY_BUTTON.values())
		{
			if (!runes(row).isEmpty() && Spell.byComponent(row.button) == null)
			{
				missing.add("book " + row.book + " " + row.name + " (button " + row.button + ")");
			}
		}
		assertTrue("not priced:\n" + String.join("\n", missing), missing.isEmpty());
	}

	@Test
	public void cacheRunesAreAllKnownRunes()
	{
		// a cache requirement that is a rune we don't model (e.g. a new rune type) would be
		// silently dropped by runes() - fail instead. Non-rune requirements are known items.
		final Set<Integer> nonRuneRequirements = new HashSet<>(Arrays.asList(
			ItemID.SARADOMIN_STAFF, ItemID.ZAMORAK_STAFF,
			ItemID.PEST_INTERFACE_STAFFS, // Claws of Guthix: a Guthix staff / void knight mace
			ItemID.IBANSTAFF, ItemID.SLAYER_STAFF,
			ItemID.STAFFORB, // the unpowered orb a Charge Orb spell turns into a charged one
			ItemID.BANANA)); // Ape Atoll Teleport
		for (Row row : BY_BUTTON.values())
		{
			for (int id : row.req.keySet())
			{
				assertTrue(row.name + " needs item " + id + " - a rune Rune doesn't model?",
					RUNE_BY_ITEM.containsKey(id) || nonRuneRequirements.contains(id));
			}
		}
	}

	@Test
	public void thrallsAreArceuusAndPriced()
	{
		// issue #2: every thrall resurrect, as the game defines it, has a priced Spell
		int thralls = 0;
		for (Row row : BY_BUTTON.values())
		{
			if (row.name.startsWith("Resurrect ") && !row.name.equals("Resurrect Crops"))
			{
				thralls++;
				assertEquals(row.name, 3, row.book);
				assertEquals(row.name, runes(row), Spell.byComponent(row.button).getRunes());
			}
		}
		assertEquals(9, thralls);
	}
}
