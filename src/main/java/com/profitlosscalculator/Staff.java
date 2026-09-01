/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

/**
 * Equipped items that provide unlimited runes of one or more types, removing that rune
 * from a spell's cost. Covers elemental staves, the combination battlestaves, the Kodai
 * wand and the elemental tomes.
 *
 * <p>Not modelled in v1: tome charge depletion (a tome with no charges still counts as
 * providing its rune here), and staves that only matter to non-standard spellbooks.
 */
@Getter
enum Staff
{
	AIR(EnumSet.of(Rune.AIR),
		ItemID.STAFF_OF_AIR, ItemID.AIR_BATTLESTAFF, ItemID.MYSTIC_AIR_STAFF,
		ItemID.DRAMEN_STAFF_AIR),
	WATER(EnumSet.of(Rune.WATER),
		ItemID.STAFF_OF_WATER, ItemID.WATER_BATTLESTAFF, ItemID.MYSTIC_WATER_STAFF,
		ItemID.DRAMEN_STAFF_WATER),
	EARTH(EnumSet.of(Rune.EARTH),
		ItemID.STAFF_OF_EARTH, ItemID.EARTH_BATTLESTAFF, ItemID.MYSTIC_EARTH_STAFF),
	FIRE(EnumSet.of(Rune.FIRE),
		ItemID.STAFF_OF_FIRE, ItemID.FIRE_BATTLESTAFF, ItemID.MYSTIC_FIRE_STAFF,
		ItemID.DRAMEN_STAFF_FIRE),

	MUD(EnumSet.of(Rune.WATER, Rune.EARTH),
		ItemID.MUD_BATTLESTAFF, ItemID.MYSTIC_MUD_STAFF),
	LAVA(EnumSet.of(Rune.EARTH, Rune.FIRE),
		ItemID.LAVA_BATTLESTAFF, ItemID.MYSTIC_LAVA_STAFF,
		ItemID.LAVA_BATTLESTAFF_PRETTY, ItemID.MYSTIC_LAVA_STAFF_PRETTY),
	STEAM(EnumSet.of(Rune.WATER, Rune.FIRE),
		ItemID.STEAM_BATTLESTAFF, ItemID.MYSTIC_STEAM_BATTLESTAFF,
		ItemID.STEAM_BATTLESTAFF_PRETTY, ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY),
	SMOKE(EnumSet.of(Rune.AIR, Rune.FIRE),
		ItemID.SMOKE_BATTLESTAFF, ItemID.MYSTIC_SMOKE_BATTLESTAFF),
	MIST(EnumSet.of(Rune.AIR, Rune.WATER),
		ItemID.MIST_BATTLESTAFF, ItemID.MYSTIC_MIST_BATTLESTAFF),
	DUST(EnumSet.of(Rune.AIR, Rune.EARTH),
		ItemID.DUST_BATTLESTAFF, ItemID.MYSTIC_DUST_BATTLESTAFF),

	KODAI(EnumSet.of(Rune.WATER), ItemID.KODAI_WAND),
	TOME_OF_FIRE(EnumSet.of(Rune.FIRE), ItemID.TOME_OF_FIRE),
	TOME_OF_WATER(EnumSet.of(Rune.WATER), ItemID.TOME_OF_WATER);

	private final Set<Rune> runes;
	private final int[] itemIds;

	Staff(Set<Rune> runes, int... itemIds)
	{
		this.runes = runes;
		this.itemIds = itemIds;
	}

	static Staff byItemId(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		for (Staff staff : values())
		{
			for (int id : staff.itemIds)
			{
				if (id == itemId)
				{
					return staff;
				}
			}
		}
		return null;
	}
}
