/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

/**
 * Turns a detected standard-spellbook cast into a gp cost: the sum of GE prices of the
 * runes the spell needs, minus any rune supplied by an equipped staff/tome.
 *
 * <p>Deliberate limitation (see README): the rune <em>pouch</em> is not consulted - runes
 * in the pouch are still consumed by the cast and still cost gp, so only equipped staves
 * remove a rune from the bill. Combination-rune substitution from the inventory is not
 * modelled (cost is nearly identical either way).
 */
class SpellCostService
{
	private final Client client;
	private final ItemManager itemManager;

	@Inject
	SpellCostService(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	@Value
	static class Priced
	{
		Spell spell;
		long gp;
		Map<String, Long> breakdown;
		/** rune -&gt; quantity actually consumed (staff-provided runes excluded). */
		Map<Rune, Integer> runesUsed;
	}

	Priced price(Spell spell)
	{
		return price(spell, equippedRuneSources(), id -> Math.max(0, itemManager.getItemPrice(id)));
	}

	/** Pure core: exposed for unit testing with a fake price function and staff set. */
	static Priced price(Spell spell, Set<Rune> providedByStaff, IntUnaryOperator gePrice)
	{
		long gp = 0;
		Map<String, Long> breakdown = new LinkedHashMap<>();
		Map<Rune, Integer> runesUsed = new LinkedHashMap<>();
		for (Map.Entry<Rune, Integer> e : spell.getRunes().entrySet())
		{
			final Rune rune = e.getKey();
			final int qty = e.getValue();
			if (providedByStaff.contains(rune))
			{
				breakdown.put(rune.getDisplayName() + " (staff)", 0L);
				continue;
			}
			final long line = (long) gePrice.applyAsInt(rune.getItemId()) * qty;
			gp += line;
			breakdown.put(rune.getDisplayName() + " x" + qty, line);
			runesUsed.put(rune, qty);
		}
		return new Priced(spell, gp, breakdown, runesUsed);
	}

	Set<Rune> equippedRuneSources()
	{
		final EnumSet<Rune> set = EnumSet.noneOf(Rune.class);
		final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return set;
		}
		addStaff(set, worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx()));
		addStaff(set, worn.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx()));
		return set;
	}

	private static void addStaff(Set<Rune> set, Item item)
	{
		if (item == null)
		{
			return;
		}
		final Staff staff = Staff.byItemId(item.getId());
		if (staff != null)
		{
			set.addAll(staff.getRunes());
		}
	}
}
