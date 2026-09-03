/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 *
 * Spell rune requirements - the standard spellbook plus the Ancient Magicks combat spells.
 * Quantities are from the OSRS Wiki (Standard spellbook / God spells / Ancient Magicks). If
 * a spell is mispriced, fix the single line here - nothing else depends on the numbers.
 *
 * Detection is manual-cast only for every spell here - autocasting is not tracked (same
 * limitation for the standard combat spells). Not covered (documented in the README):
 * Ancient/Lunar/Arceuus teleports, the Lunar and Arceuus spellbooks, and the Enchant
 * Jewellery / Enchant Crossbow Bolt families (a submenu picks the real spell).
 */
package com.profitlosscalculator;

import static com.profitlosscalculator.Rune.AIR;
import static com.profitlosscalculator.Rune.BLOOD;
import static com.profitlosscalculator.Rune.BODY;
import static com.profitlosscalculator.Rune.CHAOS;
import static com.profitlosscalculator.Rune.COSMIC;
import static com.profitlosscalculator.Rune.DEATH;
import static com.profitlosscalculator.Rune.EARTH;
import static com.profitlosscalculator.Rune.FIRE;
import static com.profitlosscalculator.Rune.LAW;
import static com.profitlosscalculator.Rune.MIND;
import static com.profitlosscalculator.Rune.NATURE;
import static com.profitlosscalculator.Rune.SOUL;
import static com.profitlosscalculator.Rune.WATER;
import static com.profitlosscalculator.Rune.WRATH;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.gameval.InterfaceID;

@Getter
enum Spell
{
	// --- Strikes ---
	WIND_STRIKE(InterfaceID.MagicSpellbook.WIND_STRIKE, "Wind Strike", r(AIR, 1, MIND, 1)),
	WATER_STRIKE(InterfaceID.MagicSpellbook.WATER_STRIKE, "Water Strike", r(WATER, 1, AIR, 1, MIND, 1)),
	EARTH_STRIKE(InterfaceID.MagicSpellbook.EARTH_STRIKE, "Earth Strike", r(EARTH, 2, AIR, 1, MIND, 1)),
	FIRE_STRIKE(InterfaceID.MagicSpellbook.FIRE_STRIKE, "Fire Strike", r(FIRE, 3, AIR, 2, MIND, 1)),
	// --- Bolts ---
	WIND_BOLT(InterfaceID.MagicSpellbook.WIND_BOLT, "Wind Bolt", r(AIR, 2, CHAOS, 1)),
	WATER_BOLT(InterfaceID.MagicSpellbook.WATER_BOLT, "Water Bolt", r(WATER, 2, AIR, 2, CHAOS, 1)),
	EARTH_BOLT(InterfaceID.MagicSpellbook.EARTH_BOLT, "Earth Bolt", r(EARTH, 3, AIR, 2, CHAOS, 1)),
	FIRE_BOLT(InterfaceID.MagicSpellbook.FIRE_BOLT, "Fire Bolt", r(FIRE, 4, AIR, 3, CHAOS, 1)),
	// --- Blasts ---
	WIND_BLAST(InterfaceID.MagicSpellbook.WIND_BLAST, "Wind Blast", r(AIR, 3, DEATH, 1)),
	WATER_BLAST(InterfaceID.MagicSpellbook.WATER_BLAST, "Water Blast", r(WATER, 3, AIR, 3, DEATH, 1)),
	EARTH_BLAST(InterfaceID.MagicSpellbook.EARTH_BLAST, "Earth Blast", r(EARTH, 4, AIR, 3, DEATH, 1)),
	FIRE_BLAST(InterfaceID.MagicSpellbook.FIRE_BLAST, "Fire Blast", r(FIRE, 5, AIR, 4, DEATH, 1)),
	// --- Waves ---
	WIND_WAVE(InterfaceID.MagicSpellbook.WIND_WAVE, "Wind Wave", r(AIR, 5, BLOOD, 1)),
	WATER_WAVE(InterfaceID.MagicSpellbook.WATER_WAVE, "Water Wave", r(WATER, 7, AIR, 5, BLOOD, 1)),
	EARTH_WAVE(InterfaceID.MagicSpellbook.EARTH_WAVE, "Earth Wave", r(EARTH, 7, AIR, 5, BLOOD, 1)),
	FIRE_WAVE(InterfaceID.MagicSpellbook.FIRE_WAVE, "Fire Wave", r(FIRE, 7, AIR, 5, BLOOD, 1)),
	// --- Surges ---
	WIND_SURGE(InterfaceID.MagicSpellbook.WIND_SURGE, "Wind Surge", r(AIR, 7, WRATH, 1)),
	WATER_SURGE(InterfaceID.MagicSpellbook.WATER_SURGE, "Water Surge", r(WATER, 10, AIR, 7, WRATH, 1)),
	EARTH_SURGE(InterfaceID.MagicSpellbook.EARTH_SURGE, "Earth Surge", r(EARTH, 10, AIR, 7, WRATH, 1)),
	FIRE_SURGE(InterfaceID.MagicSpellbook.FIRE_SURGE, "Fire Surge", r(FIRE, 10, AIR, 7, WRATH, 1)),
	// --- God spells / Charge ---
	SARADOMIN_STRIKE(InterfaceID.MagicSpellbook.SARADOMIN_STRIKE, "Saradomin Strike", r(AIR, 4, FIRE, 2, BLOOD, 2)),
	CLAWS_OF_GUTHIX(InterfaceID.MagicSpellbook.CLAWS_OF_GUTHIX, "Claws of Guthix", r(AIR, 4, FIRE, 1, BLOOD, 2)),
	FLAMES_OF_ZAMORAK(InterfaceID.MagicSpellbook.FLAMES_OF_ZAMORAK, "Flames of Zamorak", r(AIR, 1, FIRE, 4, BLOOD, 2)),
	CHARGE(InterfaceID.MagicSpellbook.CHARGE, "Charge", r(AIR, 3, FIRE, 3, BLOOD, 3)),
	// --- Other combat ---
	CRUMBLE_UNDEAD(InterfaceID.MagicSpellbook.CRUMBLE_UNDEAD, "Crumble Undead", r(AIR, 2, EARTH, 2, CHAOS, 1)),
	IBAN_BLAST(InterfaceID.MagicSpellbook.IBAN_BLAST, "Iban Blast", r(FIRE, 5, DEATH, 1)),
	MAGIC_DART(InterfaceID.MagicSpellbook.MAGIC_DART, "Magic Dart", r(DEATH, 1, MIND, 4)),
	// --- Curses / binds ---
	CONFUSE(InterfaceID.MagicSpellbook.CONFUSE, "Confuse", r(EARTH, 2, WATER, 3, BODY, 1)),
	WEAKEN(InterfaceID.MagicSpellbook.WEAKEN, "Weaken", r(EARTH, 2, WATER, 3, BODY, 1)),
	CURSE(InterfaceID.MagicSpellbook.CURSE, "Curse", r(EARTH, 3, WATER, 2, BODY, 1)),
	VULNERABILITY(InterfaceID.MagicSpellbook.VULNERABILITY, "Vulnerability", r(EARTH, 5, WATER, 5, SOUL, 1)),
	ENFEEBLE(InterfaceID.MagicSpellbook.ENFEEBLE, "Enfeeble", r(EARTH, 8, WATER, 8, SOUL, 1)),
	STUN(InterfaceID.MagicSpellbook.STUN, "Stun", r(EARTH, 12, WATER, 12, SOUL, 1)),
	BIND(InterfaceID.MagicSpellbook.BIND, "Bind", r(EARTH, 3, WATER, 3, NATURE, 2)),
	SNARE(InterfaceID.MagicSpellbook.SNARE, "Snare", r(EARTH, 4, WATER, 4, NATURE, 3)),
	ENTANGLE(InterfaceID.MagicSpellbook.ENTANGLE, "Entangle", r(EARTH, 5, WATER, 5, NATURE, 4)),
	TELEPORT_BLOCK(InterfaceID.MagicSpellbook.TELEPORT_BLOCK, "Tele Block", r(CHAOS, 1, DEATH, 1, LAW, 1)),
	// --- Alchemy / smithing utility ---
	BONES_TO_BANANAS(InterfaceID.MagicSpellbook.BONES_BANANAS, "Bones to Bananas", r(EARTH, 2, WATER, 2, NATURE, 1)),
	BONES_TO_PEACHES(InterfaceID.MagicSpellbook.BONES_PEACHES, "Bones to Peaches", r(EARTH, 2, WATER, 4, NATURE, 2)),
	LOW_ALCHEMY(InterfaceID.MagicSpellbook.LOW_ALCHEMY, "Low Level Alchemy", r(FIRE, 3, NATURE, 1)),
	HIGH_ALCHEMY(InterfaceID.MagicSpellbook.HIGH_ALCHEMY, "High Level Alchemy", r(FIRE, 5, NATURE, 1)),
	SUPERHEAT_ITEM(InterfaceID.MagicSpellbook.SUPERHEAT, "Superheat Item", r(FIRE, 4, NATURE, 1)),
	TELEKINETIC_GRAB(InterfaceID.MagicSpellbook.TELEGRAB, "Telekinetic Grab", r(AIR, 1, LAW, 1)),
	// --- Charge orb ---
	CHARGE_WATER_ORB(InterfaceID.MagicSpellbook.CHARGE_WATER_ORB, "Charge Water Orb", r(WATER, 30, COSMIC, 3)),
	CHARGE_EARTH_ORB(InterfaceID.MagicSpellbook.CHARGE_EARTH_ORB, "Charge Earth Orb", r(EARTH, 30, COSMIC, 3)),
	CHARGE_FIRE_ORB(InterfaceID.MagicSpellbook.CHARGE_FIRE_ORB, "Charge Fire Orb", r(FIRE, 30, COSMIC, 3)),
	CHARGE_AIR_ORB(InterfaceID.MagicSpellbook.CHARGE_AIR_ORB, "Charge Air Orb", r(AIR, 30, COSMIC, 3)),
	// --- Teleports ---
	VARROCK_TELEPORT(InterfaceID.MagicSpellbook.VARROCK_TELEPORT, "Varrock Teleport", r(AIR, 3, FIRE, 1, LAW, 1)),
	LUMBRIDGE_TELEPORT(InterfaceID.MagicSpellbook.LUMBRIDGE_TELEPORT, "Lumbridge Teleport", r(AIR, 3, EARTH, 1, LAW, 1)),
	FALADOR_TELEPORT(InterfaceID.MagicSpellbook.FALADOR_TELEPORT, "Falador Teleport", r(AIR, 3, WATER, 1, LAW, 1)),
	TELEPORT_TO_HOUSE(InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE, "Teleport to House", r(AIR, 1, EARTH, 1, LAW, 1)),
	CAMELOT_TELEPORT(InterfaceID.MagicSpellbook.CAMELOT_TELEPORT, "Camelot Teleport", r(AIR, 5, LAW, 1)),
	ARDOUGNE_TELEPORT(InterfaceID.MagicSpellbook.ARDOUGNE_TELEPORT, "Ardougne Teleport", r(WATER, 2, LAW, 2)),
	WATCHTOWER_TELEPORT(InterfaceID.MagicSpellbook.WATCHTOWER_TELEPORT, "Watchtower Teleport", r(EARTH, 2, LAW, 2)),
	TROLLHEIM_TELEPORT(InterfaceID.MagicSpellbook.TROLLHEIM_TELEPORT, "Trollheim Teleport", r(FIRE, 2, LAW, 2)),
	APE_ATOLL_TELEPORT(InterfaceID.MagicSpellbook.APE_TELEPORT, "Ape Atoll Teleport", r(FIRE, 2, WATER, 2, LAW, 2)),
	KOUREND_CASTLE_TELEPORT(InterfaceID.MagicSpellbook.KOUREND_TELEPORT, "Kourend Castle Teleport", r(FIRE, 1, WATER, 1, LAW, 2)),
	CIVITAS_ILLA_FORTIS_TELEPORT(InterfaceID.MagicSpellbook.FORTIS_TELEPORT, "Civitas illa Fortis Teleport", r(EARTH, 1, FIRE, 1, LAW, 2)),
	TELEOTHER_LUMBRIDGE(InterfaceID.MagicSpellbook.TELEOTHER_LUMBRIDGE, "Teleother Lumbridge", r(EARTH, 1, LAW, 1, SOUL, 1)),
	TELEOTHER_FALADOR(InterfaceID.MagicSpellbook.TELEOTHER_FALADOR, "Teleother Falador", r(WATER, 1, LAW, 1, SOUL, 1)),
	TELEOTHER_CAMELOT(InterfaceID.MagicSpellbook.TELEOTHER_CAMELOT, "Teleother Camelot", r(LAW, 1, SOUL, 2)),

	// --- Ancient Magicks: combat (Ice / Blood / Smoke / Shadow, all four tiers) ---
	ICE_RUSH(InterfaceID.MagicSpellbook.ICE_RUSH, "Ice Rush", r(WATER, 2, CHAOS, 2, DEATH, 2)),
	ICE_BURST(InterfaceID.MagicSpellbook.ICE_BURST, "Ice Burst", r(WATER, 4, CHAOS, 4, DEATH, 2)),
	ICE_BLITZ(InterfaceID.MagicSpellbook.ICE_BLITZ, "Ice Blitz", r(WATER, 3, BLOOD, 2, DEATH, 2)),
	ICE_BARRAGE(InterfaceID.MagicSpellbook.ICE_BARRAGE, "Ice Barrage", r(WATER, 6, BLOOD, 2, DEATH, 4)),
	BLOOD_RUSH(InterfaceID.MagicSpellbook.BLOOD_RUSH, "Blood Rush", r(BLOOD, 1, CHAOS, 2, DEATH, 2)),
	BLOOD_BURST(InterfaceID.MagicSpellbook.BLOOD_BURST, "Blood Burst", r(BLOOD, 2, CHAOS, 4, DEATH, 2)),
	BLOOD_BLITZ(InterfaceID.MagicSpellbook.BLOOD_BLITZ, "Blood Blitz", r(BLOOD, 4, DEATH, 2)),
	BLOOD_BARRAGE(InterfaceID.MagicSpellbook.BLOOD_BARRAGE, "Blood Barrage", r(BLOOD, 4, DEATH, 4, SOUL, 1)),
	SMOKE_RUSH(InterfaceID.MagicSpellbook.SMOKE_RUSH, "Smoke Rush", r(AIR, 1, FIRE, 1, CHAOS, 2, DEATH, 2)),
	SMOKE_BURST(InterfaceID.MagicSpellbook.SMOKE_BURST, "Smoke Burst", r(AIR, 2, FIRE, 2, CHAOS, 4, DEATH, 2)),
	SMOKE_BLITZ(InterfaceID.MagicSpellbook.SMOKE_BLITZ, "Smoke Blitz", r(AIR, 2, FIRE, 2, BLOOD, 2, DEATH, 2)),
	SMOKE_BARRAGE(InterfaceID.MagicSpellbook.SMOKE_BARRAGE, "Smoke Barrage", r(AIR, 4, FIRE, 4, BLOOD, 2, DEATH, 4)),
	SHADOW_RUSH(InterfaceID.MagicSpellbook.SHADOW_RUSH, "Shadow Rush", r(AIR, 1, CHAOS, 2, DEATH, 2, SOUL, 1)),
	SHADOW_BURST(InterfaceID.MagicSpellbook.SHADOW_BURST, "Shadow Burst", r(AIR, 1, CHAOS, 4, DEATH, 2, SOUL, 2)),
	SHADOW_BLITZ(InterfaceID.MagicSpellbook.SHADOW_BLITZ, "Shadow Blitz", r(AIR, 2, BLOOD, 2, DEATH, 2, SOUL, 2)),
	SHADOW_BARRAGE(InterfaceID.MagicSpellbook.SHADOW_BARRAGE, "Shadow Barrage", r(AIR, 4, BLOOD, 2, DEATH, 4, SOUL, 3));

	private final int componentId;
	private final String displayName;
	private final Map<Rune, Integer> runes;

	Spell(int componentId, String displayName, Map<Rune, Integer> runes)
	{
		this.componentId = componentId;
		this.displayName = displayName;
		this.runes = runes;
	}

	private static final Map<Integer, Spell> BY_COMPONENT = new HashMap<>();

	static
	{
		for (Spell s : values())
		{
			BY_COMPONENT.put(s.componentId, s);
		}
	}

	static Spell byComponent(int componentId)
	{
		return BY_COMPONENT.get(componentId);
	}

	private static Map<Rune, Integer> r(Object... kv)
	{
		EnumMap<Rune, Integer> map = new EnumMap<>(Rune.class);
		for (int i = 0; i < kv.length; i += 2)
		{
			map.put((Rune) kv[i], (Integer) kv[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}
}
