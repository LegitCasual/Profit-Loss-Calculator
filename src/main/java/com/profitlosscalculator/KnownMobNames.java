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
import java.util.TreeSet;

/**
 * A static dictionary of real NPC names, for the Target Farm search-as-you-type field
 * (see {@link TargetedContent}). Every entry is the exact name the game gives the NPC (so an
 * exact-match farm actually counts its kills), sourced from:
 *
 * <ul>
 *     <li>RuneLite's bundled {@code net.runelite.client.plugins.slayer.Task} enum - the
 *         individually-named task entries and every {@code targetNames} alias (superiors,
 *         task-reachable bosses, named characters). Family labels like "Aberrant spectres" or
 *         "Cows" are left out: they are task/species groups, not one NPC, and would never
 *         match a kill in an exact-match field. The handful of task names carrying a "The "
 *         prefix that the NPC itself does not ("The Kalphite Queen" and friends) are stored
 *         without it.</li>
 *     <li>The OSRS Wiki monster infoboxes, for bosses that have no Slayer task - checked name
 *         by name against {@code {{Infobox Monster|name=}}}. Raid encounters (CoX / ToB / ToA)
 *         are deliberately excluded: their loot comes from a chest, not the boss NPC, so a
 *         "farm" of one would silently count nothing.</li>
 * </ul>
 *
 * <p>{@link #GROUPS} adds a few collective labels for multi-NPC encounters (Moons of Peril,
 * Dagannoth Kings, ...). Picking one drops every member into the farm at once; the members
 * are individually searchable too. {@link #expand(String)} turns a picked / typed value into
 * the NPC name(s) to farm.
 */
final class KnownMobNames
{
	/** Collective label -&gt; the exact NPC names it expands to. Insertion order = farm order. */
	private static final Map<String, List<String>> GROUPS;

	static
	{
		// only encounters that are genuinely several distinct boss NPCs each with their own
		// death + loot - not "two names, one kill / one shared chest" (Grotesque Guardians,
		// Barrows), which would skew the per-kill numbers
		final Map<String, List<String>> g = new LinkedHashMap<>();
		g.put("Moons of Peril", Arrays.asList("Blood Moon", "Blue Moon", "Eclipse Moon"));
		g.put("Dagannoth Kings", Arrays.asList("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"));
		g.put("The Royal Titans", Arrays.asList("Branda the Fire Queen", "Eldric the Ice King"));
		GROUPS = Collections.unmodifiableMap(g);
	}

	private static final List<String> NAMES;

	static
	{
		final TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		set.addAll(Arrays.asList(
			// --- Slayer Task singles + aliases (RuneLite Task enum, "The " prefix stripped
			//     where the NPC name itself has none) ---
			"Ankou", "Cockatrice", "Deranged archaeologist", "Duke Sucellus", "Kurask",
			"Abyssal Sire", "Alchemical Hydra", "Chaos Elemental", "Chaos Fanatic",
			"Giant Mole", "Kalphite Queen", "King Black Dragon", "The Leviathan",
			"Maggot King", "Phantom Muspah", "Shellbane gryphon",
			"Thermonuclear smoke devil", "The Whisperer", "TzTok-Jad", "TzKal-Zuk",
			"Commander Zilyana", "General Graardor", "Vardorvis", "Zulrah", "Sarachnis", "Scorpia",
			"Araxxor",

			"Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree",
			"Bandit", "Black Heather", "Donny the Lad", "Speedy Keith", "Death wing", "Callisto",
			"Artio", "Chicken", "Rooster", "Terrorbird", "Seagull", "Vulture", "Duck", "Penguin",
			"Baby Roc", "Demonic gorilla", "Balfrug Kreeyath", "Skotizo", "Porazdir",
			"Black Knight", "Vorkath", "Chasm crawler", "Cave abomination", "Kraken", "Buffalo",
			"Brutus", "Ammonite Crab", "Frost Crab", "King Sand Crab", "Rock Crab",
			"Giant Rock Crab", "Sand Crab", "Swamp Crab", "Crushing hand", "Ancient Custodian",
			"Night beast", "Dark warrior", "Jackal", "Temple Guardian", "Choke devil", "Dwarf",
			"Black Guard", "Elf", "Iorwerth Warrior", "Iorwerth Archer", "Branda the Fire Queen",
			"Flesh crawler", "Ancient wyvern", "Long-tailed wyvern", "Spitting wyvern",
			"Taloned wyvern", "Dusk", "Dawn", "Tortured soul", "Forgotten Soul", "Revenant",
			"Sergeant Strongstack", "Sergeant Grimspike", "Sergeant Steelwill", "K'ril Tsutsaroth",
			"Tstanon Karlak", "Tormented Demon", "Elvarg", "Cyclops", "Reanimated giant", "Obor",
			"Cerberus", "Eldric the Ice King", "Icelord", "Malevolent mage", "Jelly", "Lava dragon",
			"Zakl'n Gritch", "Sulphur Nagua", "Frost Nagua", "Amoxliatl", "Lizardman",
			"Bronze dragon", "Iron Dragon", "Steel dragon", "Mithril dragon", "Adamant dragon",
			"Rune dragon", "Tortured gorilla", "Padulah", "Bryophyta", "Zygomite", "Fungi",
			"Nechryarch", "Enclave guard", "Mogre", "Ogress", "Skogre", "Zogre", "Pirate",
			"Flaming pyrelord", "Scarab swarm", "Locust rider", "Scarab mage", "Small Scarab",
			"Lobstrosity", "Loar", "Phrin", "Riyl", "Asyn", "Fiyr", "Urium", "Vet'ion",
			"Calvar'ion", "Skeletal Mystic", "Kalrag", "Venenatis", "Spindel", "Araxyte",
			"Spiritual ranger", "Spiritual mage", "Spiritual warrior", "Dad", "Arrg", "Stick",
			"Kraka", "Pee Hat", "Rock", "Twig", "Berry", "Vyrewatch", "Warped terrorbird",
			"Warped tortoise", "Mutated terrorbird", "Mutated tortoise", "Werewolf", "Wolf",
			"Wyrmling", "Strykewyrm", "Undead",

			// --- Barrows Brothers (long-stable, unambiguous) ---
			"Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled",

			// --- bosses with no Slayer task (OSRS Wiki monster infobox names) ---
			"Nex", "Fumus", "Umbra", "Cruor", "Glacies",
			"Growler", "Bree", "Starlight",
			"Corporeal Beast", "The Nightmare", "Phosani's Nightmare",
			"Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",
			"Blood Moon", "Blue Moon", "Eclipse Moon",
			"The Hueycoatl", "Scurrius", "Yama", "Doom of Mokhaiotl",
			"Crazy archaeologist", "Gemstone Crab", "Revenant maledictus", "Hespori",
			"Sol Heredit", "Crystalline Hunllef", "Corrupted Hunllef", "The Mimic",
			"Zalcano", "Tempoross"
		));
		GROUPS.values().forEach(set::addAll);

		final List<String> out = new ArrayList<>(set);
		out.addAll(GROUPS.keySet());
		out.sort(String.CASE_INSENSITIVE_ORDER);
		NAMES = Collections.unmodifiableList(out);
	}

	private KnownMobNames()
	{
	}

	/** Every suggestion for the search field: individual NPC names plus the collective labels. */
	static List<String> all()
	{
		return NAMES;
	}

	/**
	 * Turn a picked suggestion or typed value into the NPC name(s) to farm. A collective label
	 * ("Moons of Peril", "Dagannoth Kings", ... - a leading "The " is optional) expands to its
	 * members in order; anything else comes back as a single trimmed name. Blank in, empty out.
	 */
	static List<String> expand(String entry)
	{
		if (entry == null)
		{
			return Collections.emptyList();
		}
		final String trimmed = entry.trim();
		if (trimmed.isEmpty())
		{
			return Collections.emptyList();
		}
		for (Map.Entry<String, List<String>> group : GROUPS.entrySet())
		{
			if (labelMatches(group.getKey(), trimmed))
			{
				return new ArrayList<>(group.getValue());
			}
		}
		return Collections.singletonList(trimmed);
	}

	private static boolean labelMatches(String label, String typed)
	{
		return stripThe(label).equalsIgnoreCase(stripThe(typed));
	}

	private static String stripThe(String s)
	{
		return s.length() > 4 && s.regionMatches(true, 0, "the ", 0, 4) ? s.substring(4).trim() : s;
	}

	/** Package-visible for tests: the collective labels and what they expand to. */
	static Map<String, List<String>> groups()
	{
		return GROUPS;
	}
}
