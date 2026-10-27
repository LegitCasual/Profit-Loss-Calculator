/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 *
 * Spell rune requirements for all four spellbooks - Standard, Ancient Magicks, Lunar and
 * Arceuus. Quantities are from the OSRS Wiki (Standard spellbook / God spells / Ancient
 * Magicks / Lunar spellbook / Arceuus spellbook) and checked against the game cache - the
 * spell objs' rune params, snapshotted in src/test/resources/.../spell-runes.tsv and enforced
 * by SpellCacheTest. If a spell is mispriced, fix the single line here - nothing else depends
 * on the numbers.
 *
 * Not covered (documented in the README): Enchant Crossbow Bolt (its runes depend on the bolt).
 */
package com.profitlosscalculator;

import static com.profitlosscalculator.Rune.AIR;
import static com.profitlosscalculator.Rune.ASTRAL;
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
	BONES_TO_PEACHES(InterfaceID.MagicSpellbook.BONES_PEACHES, "Bones to Peaches", r(EARTH, 4, WATER, 4, NATURE, 2)),
	LOW_ALCHEMY(InterfaceID.MagicSpellbook.LOW_ALCHEMY, "Low Level Alchemy", r(FIRE, 3, NATURE, 1)),
	HIGH_ALCHEMY(InterfaceID.MagicSpellbook.HIGH_ALCHEMY, "High Level Alchemy", r(FIRE, 5, NATURE, 1)),
	SUPERHEAT_ITEM(InterfaceID.MagicSpellbook.SUPERHEAT, "Superheat Item", r(FIRE, 4, NATURE, 1)),
	TELEKINETIC_GRAB(InterfaceID.MagicSpellbook.TELEGRAB, "Telekinetic Grab", r(AIR, 1, LAW, 1)),
	MONSTER_INSPECT(InterfaceID.MagicSpellbook.MONSTER_INSPECT, "Monster Inspect", r(BODY, 2, MIND, 2)),
	// --- Jewellery enchants (each level is its own button in the Enchant Jewellery submenu) ---
	ENCHANT_LVL_1(InterfaceID.MagicSpellbook.ENCHANT_1, "Lvl-1 Enchant", r(COSMIC, 1, WATER, 1)),
	ENCHANT_LVL_2(InterfaceID.MagicSpellbook.ENCHANT_2, "Lvl-2 Enchant", r(COSMIC, 1, AIR, 3)),
	ENCHANT_LVL_3(InterfaceID.MagicSpellbook.ENCHANT_3, "Lvl-3 Enchant", r(COSMIC, 1, FIRE, 5)),
	ENCHANT_LVL_4(InterfaceID.MagicSpellbook.ENCHANT_4, "Lvl-4 Enchant", r(COSMIC, 1, EARTH, 10)),
	ENCHANT_LVL_5(InterfaceID.MagicSpellbook.ENCHANT_5, "Lvl-5 Enchant", r(COSMIC, 1, WATER, 15, EARTH, 15)),
	ENCHANT_LVL_6(InterfaceID.MagicSpellbook.ENCHANT_6, "Lvl-6 Enchant", r(COSMIC, 1, EARTH, 20, FIRE, 20)),
	ENCHANT_LVL_7(InterfaceID.MagicSpellbook.ENCHANT_7, "Lvl-7 Enchant", r(COSMIC, 1, SOUL, 20, BLOOD, 20)),
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
	// --- Sailing ---
	SUMMON_BOAT(InterfaceID.MagicSpellbook.TELEPORT_BOAT_TO_ME, "Summon Boat", r(LAW, 2, EARTH, 1, WATER, 1)),
	TELEPORT_TO_BOAT(InterfaceID.MagicSpellbook.TELEPORT_ME_TO_BOAT, "Teleport to Boat", r(LAW, 2, EARTH, 2, WATER, 2)),

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
	SHADOW_BARRAGE(InterfaceID.MagicSpellbook.SHADOW_BARRAGE, "Shadow Barrage", r(AIR, 4, BLOOD, 2, DEATH, 4, SOUL, 3)),

	// --- Arceuus: thralls (the Book of the Dead and prayer points aren't runes - not costed) ---
	RESURRECT_LESSER_GHOST(InterfaceID.MagicSpellbook.RESURRECT_LESSER_GHOST, "Resurrect Lesser Ghost", r(AIR, 10, COSMIC, 1, MIND, 5)),
	RESURRECT_LESSER_SKELETON(InterfaceID.MagicSpellbook.RESURRECT_LESSER_SKELETON, "Resurrect Lesser Skeleton", r(AIR, 10, COSMIC, 1, MIND, 5)),
	RESURRECT_LESSER_ZOMBIE(InterfaceID.MagicSpellbook.RESURRECT_LESSER_ZOMBIE, "Resurrect Lesser Zombie", r(AIR, 10, COSMIC, 1, MIND, 5)),
	RESURRECT_SUPERIOR_GHOST(InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_GHOST, "Resurrect Superior Ghost", r(EARTH, 10, COSMIC, 1, DEATH, 5)),
	RESURRECT_SUPERIOR_SKELETON(InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_SKELETON, "Resurrect Superior Skeleton", r(EARTH, 10, COSMIC, 1, DEATH, 5)),
	RESURRECT_SUPERIOR_ZOMBIE(InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_ZOMBIE, "Resurrect Superior Zombie", r(EARTH, 10, COSMIC, 1, DEATH, 5)),
	RESURRECT_GREATER_GHOST(InterfaceID.MagicSpellbook.RESURRECT_GREATER_GHOST, "Resurrect Greater Ghost", r(FIRE, 10, BLOOD, 5, COSMIC, 1)),
	RESURRECT_GREATER_SKELETON(InterfaceID.MagicSpellbook.RESURRECT_GREATER_SKELETON, "Resurrect Greater Skeleton", r(FIRE, 10, BLOOD, 5, COSMIC, 1)),
	RESURRECT_GREATER_ZOMBIE(InterfaceID.MagicSpellbook.RESURRECT_GREATER_ZOMBIE, "Resurrect Greater Zombie", r(FIRE, 10, BLOOD, 5, COSMIC, 1)),
	// --- Arceuus: combat ---
	GHOSTLY_GRASP(InterfaceID.MagicSpellbook.GHOSTLY_GRASP, "Ghostly Grasp", r(AIR, 4, CHAOS, 1)),
	SKELETAL_GRASP(InterfaceID.MagicSpellbook.SKELETAL_GRASP, "Skeletal Grasp", r(EARTH, 8, DEATH, 1)),
	UNDEAD_GRASP(InterfaceID.MagicSpellbook.UNDEAD_GRASP, "Undead Grasp", r(FIRE, 12, BLOOD, 1)),
	INFERIOR_DEMONBANE(InterfaceID.MagicSpellbook.INFERIOR_DEMONBANE, "Inferior Demonbane", r(FIRE, 3, CHAOS, 1)),
	SUPERIOR_DEMONBANE(InterfaceID.MagicSpellbook.SUPERIOR_DEMONBANE, "Superior Demonbane", r(FIRE, 5, SOUL, 1)),
	DARK_DEMONBANE(InterfaceID.MagicSpellbook.DARK_DEMONBANE, "Dark Demonbane", r(FIRE, 7, SOUL, 2)),
	LESSER_CORRUPTION(InterfaceID.MagicSpellbook.LESSER_CORRUPTION, "Lesser Corruption", r(DEATH, 1, SOUL, 2)),
	GREATER_CORRUPTION(InterfaceID.MagicSpellbook.GREATER_CORRUPTION, "Greater Corruption", r(BLOOD, 1, SOUL, 3)),
	MARK_OF_DARKNESS(InterfaceID.MagicSpellbook.MARK_OF_DARKNESS, "Mark of Darkness", r(COSMIC, 1, SOUL, 1)),
	WARD_OF_ARCEUUS(InterfaceID.MagicSpellbook.WARD_OF_ARCEUUS, "Ward of Arceuus", r(COSMIC, 1, NATURE, 2, SOUL, 4)),
	DEATH_CHARGE(InterfaceID.MagicSpellbook.DEATH_CHARGE, "Death Charge", r(BLOOD, 1, DEATH, 1, SOUL, 1)),
	// --- Arceuus: utility (heads / ashes / bones / herbs they use are skilling materials) ---
	BASIC_REANIMATION(InterfaceID.MagicSpellbook.REANIMATION_BASIC, "Basic Reanimation", r(BODY, 4, NATURE, 2)),
	ADEPT_REANIMATION(InterfaceID.MagicSpellbook.REANIMATION_ADEPT, "Adept Reanimation", r(BODY, 4, NATURE, 3, SOUL, 1)),
	EXPERT_REANIMATION(InterfaceID.MagicSpellbook.REANIMATION_EXPERT, "Expert Reanimation", r(BLOOD, 1, NATURE, 3, SOUL, 2)),
	MASTER_REANIMATION(InterfaceID.MagicSpellbook.REANIMATION_MASTER, "Master Reanimation", r(BLOOD, 2, NATURE, 4, SOUL, 4)),
	DEMONIC_OFFERING(InterfaceID.MagicSpellbook.DEMONIC_OFFERING, "Demonic Offering", r(SOUL, 1, WRATH, 1)),
	SINISTER_OFFERING(InterfaceID.MagicSpellbook.SINISTER_OFFERING, "Sinister Offering", r(BLOOD, 1, WRATH, 1)),
	SHADOW_VEIL(InterfaceID.MagicSpellbook.SHADOW_VEIL, "Shadow Veil", r(EARTH, 5, FIRE, 5, COSMIC, 5)),
	VILE_VIGOUR(InterfaceID.MagicSpellbook.VILE_VIGOUR, "Vile Vigour", r(AIR, 3, SOUL, 1)),
	DEGRIME(InterfaceID.MagicSpellbook.DEGRIME, "Degrime", r(EARTH, 4, NATURE, 2)),
	RESURRECT_CROPS(InterfaceID.MagicSpellbook.RESURRECT_CROPS, "Resurrect Crops", r(EARTH, 25, BLOOD, 8, NATURE, 12, SOUL, 8)),
	DARK_LURE(InterfaceID.MagicSpellbook.DARK_LURE, "Dark Lure", r(DEATH, 1, NATURE, 1)),
	// --- Arceuus: teleports (home and minigame teleports are free) ---
	ARCEUUS_LIBRARY_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_ARCEUUS_LIBRARY, "Arceuus Library Teleport", r(EARTH, 2, LAW, 1)),
	DRAYNOR_MANOR_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_DRAYNOR_MANOR, "Draynor Manor Teleport", r(EARTH, 1, WATER, 1, LAW, 1)),
	BATTLEFRONT_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_BATTLEFRONT, "Battlefront Teleport", r(EARTH, 1, FIRE, 1, LAW, 1)),
	MIND_ALTAR_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_MIND_ALTAR, "Mind Altar Teleport", r(LAW, 1, MIND, 2)),
	RESPAWN_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_RESPAWN, "Respawn Teleport", r(LAW, 1, SOUL, 1)),
	SALVE_GRAVEYARD_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_SALVE_GRAVEYARD, "Salve Graveyard Teleport", r(LAW, 1, SOUL, 2)),
	FENKENSTRAINS_CASTLE_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_FENKENSTRAIN_CASTLE, "Fenkenstrain's Castle Teleport", r(EARTH, 1, LAW, 1, SOUL, 1)),
	WEST_ARDOUGNE_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_WEST_ARDOUGNE, "West Ardougne Teleport", r(LAW, 2, SOUL, 2)),
	HARMONY_ISLAND_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_HARMONY_ISLAND, "Harmony Island Teleport", r(LAW, 1, NATURE, 1, SOUL, 1)),
	CEMETERY_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_CEMETERY, "Cemetery Teleport", r(BLOOD, 1, LAW, 1, SOUL, 1)),
	BARROWS_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_BARROWS, "Barrows Teleport", r(BLOOD, 1, LAW, 2, SOUL, 2)),
	APE_ATOLL_DUNGEON_TELEPORT(InterfaceID.MagicSpellbook.TELEPORT_APE_ATOLL_DUNGEON, "Ape Atoll Teleport (Arceuus)", r(BLOOD, 2, LAW, 2, SOUL, 2)),

	// --- Ancient Magicks: teleports (home and minigame teleports are free) ---
	PADDEWWA_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT1, "Paddewwa Teleport", r(AIR, 1, FIRE, 1, LAW, 2)),
	SENNTISTEN_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT2, "Senntisten Teleport", r(LAW, 2, SOUL, 1)),
	KHARYRLL_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT3, "Kharyrll Teleport", r(BLOOD, 1, LAW, 2)),
	LASSAR_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT4, "Lassar Teleport", r(WATER, 4, LAW, 2)),
	DAREEYAK_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT5, "Dareeyak Teleport", r(AIR, 2, FIRE, 3, LAW, 2)),
	CARRALLANGER_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT6, "Carrallanger Teleport", r(LAW, 2, SOUL, 2)),
	ANNAKARL_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT7, "Annakarl Teleport", r(BLOOD, 2, LAW, 2)),
	GHORROCK_TELEPORT(InterfaceID.MagicSpellbook.ZAROSTELEPORT8, "Ghorrock Teleport", r(WATER, 8, LAW, 2)),
	// --- Teleport to Target (one button, shown on every spellbook) ---
	TELEPORT_TO_TARGET(InterfaceID.MagicSpellbook.BOUNTY_TARGET, "Teleport to Target", r(CHAOS, 1, DEATH, 1, LAW, 1)),

	// --- Lunar: combat / support ---
	MONSTER_EXAMINE(InterfaceID.MagicSpellbook.MONSTER_EXAMINE, "Monster Examine", r(ASTRAL, 1, COSMIC, 1, MIND, 1)),
	NPC_CONTACT(InterfaceID.MagicSpellbook.NPC_CONTACT, "Astral Contact", r(AIR, 2, ASTRAL, 1, COSMIC, 1)),
	CURE_OTHER(InterfaceID.MagicSpellbook.CURE_OTHER, "Cure Other", r(EARTH, 10, ASTRAL, 1, LAW, 1)),
	CURE_ME(InterfaceID.MagicSpellbook.CURE_ME, "Cure Me", r(ASTRAL, 2, COSMIC, 2, LAW, 1)),
	CURE_GROUP(InterfaceID.MagicSpellbook.CURE_GROUP, "Cure Group", r(ASTRAL, 2, COSMIC, 2, LAW, 2)),
	STAT_SPY(InterfaceID.MagicSpellbook.STAT_SPY, "Stat Spy", r(ASTRAL, 2, BODY, 5, COSMIC, 2)),
	DREAM(InterfaceID.MagicSpellbook.DREAM, "Dream", r(ASTRAL, 2, BODY, 5, COSMIC, 1)),
	STAT_RESTORE_POT_SHARE(InterfaceID.MagicSpellbook.REST_POT_SHARE, "Stat Restore Pot Share", r(EARTH, 10, WATER, 10, ASTRAL, 2)),
	BOOST_POTION_SHARE(InterfaceID.MagicSpellbook.STREN_POT_SHARE, "Boost Potion Share", r(EARTH, 12, WATER, 10, ASTRAL, 3)),
	ENERGY_TRANSFER(InterfaceID.MagicSpellbook.ENERGY_TRANS, "Energy Transfer", r(ASTRAL, 3, LAW, 2, NATURE, 1)),
	HEAL_OTHER(InterfaceID.MagicSpellbook.HEAL_OTHER, "Heal Other", r(ASTRAL, 3, BLOOD, 1, LAW, 3)),
	VENGEANCE_OTHER(InterfaceID.MagicSpellbook.VENGEANCE_OTHER, "Vengeance Other", r(EARTH, 10, ASTRAL, 3, DEATH, 2)),
	VENGEANCE(InterfaceID.MagicSpellbook.VENGEANCE, "Vengeance", r(EARTH, 10, ASTRAL, 4, DEATH, 2)),
	HEAL_GROUP(InterfaceID.MagicSpellbook.HEAL_GROUP, "Heal Group", r(ASTRAL, 4, BLOOD, 3, LAW, 6)),
	SPELLBOOK_SWAP(InterfaceID.MagicSpellbook.SPELLBOOK_SWAP, "Spellbook Swap", r(ASTRAL, 3, COSMIC, 2, LAW, 1)),
	// --- Lunar: skilling utility (the logs / sand / hides / flax they use are skilling materials) ---
	BAKE_PIE(InterfaceID.MagicSpellbook.BAKE_PIE, "Bake Pie", r(FIRE, 5, WATER, 4, ASTRAL, 1)),
	GEOMANCY(InterfaceID.MagicSpellbook.GEOMANCY, "Geomancy", r(EARTH, 8, ASTRAL, 3, NATURE, 3)),
	CURE_PLANT(InterfaceID.MagicSpellbook.CURE_PLANT, "Cure Plant", r(EARTH, 8, ASTRAL, 1)),
	HUMIDIFY(InterfaceID.MagicSpellbook.HUMIDIFY, "Humidify", r(FIRE, 1, WATER, 3, ASTRAL, 1)),
	HUNTER_KIT(InterfaceID.MagicSpellbook.HUNTER_KIT, "Hunter Kit", r(EARTH, 2, ASTRAL, 2)),
	SPIN_FLAX(InterfaceID.MagicSpellbook.SPIN_FLAX, "Spin Flax", r(AIR, 5, ASTRAL, 1, NATURE, 2)),
	SUPERGLASS_MAKE(InterfaceID.MagicSpellbook.SUPERGLASS, "Superglass Make", r(AIR, 10, FIRE, 6, ASTRAL, 2)),
	TAN_LEATHER(InterfaceID.MagicSpellbook.TAN_LEATHER, "Tan Leather", r(FIRE, 5, ASTRAL, 2, NATURE, 1)),
	STRING_JEWELLERY(InterfaceID.MagicSpellbook.STRING_JEWEL, "String Jewellery", r(EARTH, 10, WATER, 5, ASTRAL, 2)),
	MAGIC_IMBUE(InterfaceID.MagicSpellbook.MAGIC_IMBUE, "Magic Imbue", r(FIRE, 7, WATER, 7, ASTRAL, 2)),
	FERTILE_SOIL(InterfaceID.MagicSpellbook.FERTILE_SOIL, "Fertile Soil", r(EARTH, 15, ASTRAL, 3, NATURE, 2)),
	PLANK_MAKE(InterfaceID.MagicSpellbook.PLANK_MAKE, "Plank Make", r(EARTH, 15, ASTRAL, 2, NATURE, 1)),
	RECHARGE_DRAGONSTONE(InterfaceID.MagicSpellbook.RECHARGE_DRAGONSTONE, "Recharge Dragonstone", r(WATER, 4, ASTRAL, 1, SOUL, 1)),
	// --- Lunar: teleports (home and minigame teleports are free) ---
	MOONCLAN_TELEPORT(InterfaceID.MagicSpellbook.TELE_MOONCLAN, "Moonclan Teleport", r(EARTH, 2, ASTRAL, 2, LAW, 1)),
	TELE_GROUP_MOONCLAN(InterfaceID.MagicSpellbook.TELE_GROUP_MOONCLAN, "Tele Group Moonclan", r(EARTH, 4, ASTRAL, 2, LAW, 1)),
	OURANIA_TELEPORT(InterfaceID.MagicSpellbook.OURANIA_TELEPORT, "Ourania Teleport", r(EARTH, 6, ASTRAL, 2, LAW, 1)),
	WATERBIRTH_TELEPORT(InterfaceID.MagicSpellbook.TELE_WATERBIRTH, "Waterbirth Teleport", r(WATER, 1, ASTRAL, 2, LAW, 1)),
	TELE_GROUP_WATERBIRTH(InterfaceID.MagicSpellbook.TELE_GROUP_WATERBIRTH, "Tele Group Waterbirth", r(WATER, 5, ASTRAL, 2, LAW, 1)),
	BARBARIAN_TELEPORT(InterfaceID.MagicSpellbook.TELE_BARB_OUT, "Barbarian Teleport", r(FIRE, 3, ASTRAL, 2, LAW, 2)),
	TELE_GROUP_BARBARIAN(InterfaceID.MagicSpellbook.TELE_GROUP_BARBARIAN, "Tele Group Barbarian", r(FIRE, 6, ASTRAL, 2, LAW, 2)),
	KHAZARD_TELEPORT(InterfaceID.MagicSpellbook.TELE_KHAZARD, "Khazard Teleport", r(WATER, 4, ASTRAL, 2, LAW, 2)),
	TELE_GROUP_KHAZARD(InterfaceID.MagicSpellbook.TELE_GROUP_KHAZARD, "Tele Group Khazard", r(WATER, 8, ASTRAL, 2, LAW, 2)),
	FISHING_GUILD_TELEPORT(InterfaceID.MagicSpellbook.TELE_FISH, "Fishing Guild Teleport", r(WATER, 10, ASTRAL, 3, LAW, 3)),
	TELE_GROUP_FISHING_GUILD(InterfaceID.MagicSpellbook.TELE_GROUP_FISHING_GUILD, "Tele Group Fishing Guild", r(WATER, 14, ASTRAL, 3, LAW, 3)),
	CATHERBY_TELEPORT(InterfaceID.MagicSpellbook.TELE_CATHER, "Catherby Teleport", r(WATER, 10, ASTRAL, 3, LAW, 3)),
	TELE_GROUP_CATHERBY(InterfaceID.MagicSpellbook.TELE_GROUP_CATHERBY, "Tele Group Catherby", r(WATER, 15, ASTRAL, 3, LAW, 3)),
	ICE_PLATEAU_TELEPORT(InterfaceID.MagicSpellbook.TELE_GHORROCK, "Ice Plateau Teleport", r(WATER, 8, ASTRAL, 3, LAW, 3)),
	TELE_GROUP_ICE_PLATEAU(InterfaceID.MagicSpellbook.TELE_GROUP_GHORROCK, "Tele Group Ice Plateau", r(WATER, 16, ASTRAL, 3, LAW, 3));

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

	/** Lookup by in-game spell name (case-insensitive), or null. */
	static Spell byName(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (Spell s : values())
		{
			if (s.displayName.equalsIgnoreCase(name))
			{
				return s;
			}
		}
		return null;
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
