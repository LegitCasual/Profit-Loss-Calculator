/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/**
 * The elemental / catalytic runes used by standard-spellbook spells, mapped to the
 * item id whose GE price represents one rune.
 */
@Getter
@RequiredArgsConstructor
enum Rune
{
	AIR("Air", ItemID.AIRRUNE),
	WATER("Water", ItemID.WATERRUNE),
	EARTH("Earth", ItemID.EARTHRUNE),
	FIRE("Fire", ItemID.FIRERUNE),
	MIND("Mind", ItemID.MINDRUNE),
	BODY("Body", ItemID.BODYRUNE),
	CHAOS("Chaos", ItemID.CHAOSRUNE),
	DEATH("Death", ItemID.DEATHRUNE),
	BLOOD("Blood", ItemID.BLOODRUNE),
	COSMIC("Cosmic", ItemID.COSMICRUNE),
	NATURE("Nature", ItemID.NATURERUNE),
	LAW("Law", ItemID.LAWRUNE),
	SOUL("Soul", ItemID.SOULRUNE),
	WRATH("Wrath", ItemID.WRATHRUNE);

	private final String displayName;
	private final int itemId;
}
