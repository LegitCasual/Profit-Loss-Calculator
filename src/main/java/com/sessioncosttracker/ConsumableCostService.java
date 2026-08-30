/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Prices an "Eat"/"Drink" click. One click = one use.
 *
 * <p>For a dosed potion one click is a single sip, so (when {@code potionDoseAware}) the
 * charge is the value that sip actually destroys: the GE price of the potion at its
 * current dose minus the price of the same potion one dose lower - or minus an empty vial
 * for the final sip. This is the "(4) &rarr; (3)" cost, not a flat quarter of a 4-dose
 * price, so consecutive sips read as the same number and the last dose isn't undercharged.
 * If the lower-dose variant has no GE price we fall back to {@code price / doses}.
 */
class ConsumableCostService
{
	private static final Pattern DOSE = Pattern.compile("\\((\\d)\\)\\s*$");

	private final ItemManager itemManager;

	@Inject
	ConsumableCostService(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	@Value
	static class Consumed
	{
		int itemId;
		String name;
		long gp;
	}

	/**
	 * @param itemId item from the menu click
	 * @param doseAware whether to charge one sip instead of the whole potion
	 * @return the priced consumable, or null if the item id is unusable
	 */
	Consumed price(int itemId, boolean doseAware)
	{
		if (itemId <= 0)
		{
			return null;
		}

		final ItemComposition comp = itemManager.getItemComposition(itemId);
		final String name = comp != null ? comp.getName() : "Item " + itemId;
		final long fullPrice = Math.max(0, itemManager.getItemPrice(itemId));

		if (!doseAware)
		{
			return new Consumed(itemId, name, fullPrice);
		}

		final Matcher m = DOSE.matcher(name);
		if (!m.find())
		{
			return new Consumed(itemId, name, fullPrice);
		}

		final int doses = Integer.parseInt(m.group(1));
		final long lowerDosePrice = doses > 1 ? priceOfDose(itemId, doses - 1) : -1;
		final long vialPrice = Math.max(0, itemManager.getItemPrice(ItemID.VIAL_EMPTY));

		return new Consumed(itemId, name, sipCost(doses, fullPrice, lowerDosePrice, vialPrice));
	}

	/**
	 * Cost of a single sip. Pure so it can be unit-tested without an {@link ItemManager}.
	 *
	 * @param doses         dose count of the potion that was clicked
	 * @param fullPrice     GE price of that potion
	 * @param lowerDosePrice GE price of the same potion at {@code doses - 1}, or negative
	 *                       if unknown/unpriced (only consulted when {@code doses > 1})
	 * @param vialPrice     GE price of an empty vial (what's left after the last sip)
	 */
	static long sipCost(int doses, long fullPrice, long lowerDosePrice, long vialPrice)
	{
		if (fullPrice <= 0)
		{
			return 0;
		}
		if (doses <= 1)
		{
			return Math.max(0, fullPrice - Math.max(0, vialPrice));
		}
		if (lowerDosePrice < 0)
		{
			return Math.round(fullPrice / (double) doses);
		}
		return Math.max(0, fullPrice - lowerDosePrice);
	}

	/**
	 * GE price of the same potion at {@code targetDose} doses, found via the item's
	 * variation group, or -1 if no such variant exists or it has no price.
	 */
	private long priceOfDose(int itemId, int targetDose)
	{
		final int base = ItemVariationMapping.map(itemId);
		for (int variant : ItemVariationMapping.getVariations(base))
		{
			final ItemComposition vc = itemManager.getItemComposition(variant);
			if (vc == null)
			{
				continue;
			}
			final Matcher vm = DOSE.matcher(vc.getName());
			if (vm.find() && Integer.parseInt(vm.group(1)) == targetDose)
			{
				final long p = itemManager.getItemPrice(variant);
				return p > 0 ? p : -1;
			}
		}
		return -1;
	}
}
