/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * A static dictionary of real NPC names, for the Boss Target Farm search-as-you-type field
 * (see {@link TargetedContent}). Sourced from RuneLite's own bundled
 * {@code net.runelite.client.plugins.slayer.Task} enum (verified against RuneLite 1.12.38
 * sources) - specifically the individually-named entries: a task's primary name when it names
 * one specific creature (doesn't end in "s" - a plural family label like "Aberrant spectres" or
 * "Dagannoth Kings" is a task/species group, not one exact NPC name, and is deliberately left
 * out here since typing it verbatim into an exact-match field would never match a kill), plus
 * every {@code targetNames} alias, which by RuneLite's own design already names one specific
 * NPC (superiors, bosses reachable from a task, named characters).
 *
 * <p>This intentionally does not attempt to cover bosses outside the Slayer task list (raids,
 * ToA, wilderness bosses without a task, etc.) - anything not sourced from real, verified game
 * data risks suggesting a name that doesn't exactly match the real NPC, which would silently
 * break the exact-match farm it's meant to help set up. The six Barrows Brothers are the one
 * deliberate manual addition below, called out separately - long-stable, unambiguous content.
 */
final class KnownMobNames
{
	private static final List<String> NAMES;

	static
	{
		final TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		set.addAll(Arrays.asList(
			// single-entity task names (primary name = the creature's own name)
			"Ankou", "Cockatrice", "Deranged Archaeologist", "Duke Sucellus", "Kurask",
			"The Abyssal Sire", "The Alchemical Hydra", "The Chaos Elemental", "The Chaos Fanatic",
			"The Giant Mole", "The Kalphite Queen", "The King Black Dragon", "The Leviathan",
			"The Maggot King", "The Phantom Muspah", "The Shellbane Gryphon",
			"The Thermonuclear Smoke Devil", "The Whisperer", "TzTok-Jad", "TzKal-Zuk",
			"Commander Zilyana", "General Graardor", "Vardorvis", "Zulrah", "Sarachnis", "Scorpia",
			"Araxxor",

			// targetNames aliases - specific NPCs reachable from a task, or superiors
			"Abyssal Sire", "Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree",
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

			// manual addition: long-stable, unambiguous content, not from the Task list
			"Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"
		));
		NAMES = Collections.unmodifiableList(new java.util.ArrayList<>(set));
	}

	private KnownMobNames()
	{
	}

	static List<String> all()
	{
		return NAMES;
	}
}
