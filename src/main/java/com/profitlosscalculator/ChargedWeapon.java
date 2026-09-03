/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

/**
 * A weapon that spends one stored charge per attack, where the plugin can put a gp value on
 * that charge: the attack plays a known animation and/or spot-anim on the player, one charge
 * goes per attack, and the recharge material is a tradeable item so its GE price is the cost.
 *
 * <p>The game does <em>not</em> expose these charge counts in a live varbit - the count only
 * syncs on charge / un-charge / "check". So consumption is counted from the attack, the same
 * way the "Item Charges Improved" and "Weapon Charges" hub plugins do it. Because the two
 * reference plugins disagree on whether a given id is an animation or a spot-anim, both are
 * watched and a per-weapon tick gap keeps one attack from counting twice.
 *
 * <p>Ids and recharge ratios are from github.com/TicTac7x/runelite-plugins (@plugin-charges)
 * and github.com/geheur/weapon-charges, cross-checked against the OSRS Wiki.
 *
 * <p>Not modelled: the blowpipe, tridents (multi-rune recharge), Sanguinesti staff, crystal
 * weapons (untradeable shards) and the wilderness weapons.
 */
@Getter
enum ChargedWeapon
{
	/** 1 ancient essence per shot. Attack anim 9858 / spot-anim 2289. */
	VENATOR_BOW("Venator bow", recipe(ItemID.ANCIENT_ESSENCE, 1),
		new int[]{9858}, new int[]{2289},
		ItemID.VENATOR_BOW, ItemID.VENATOR_BOW_ORNAMENT),

	/** 1 demon tear per cast (or 2 death + 1 chaos if rune-charged - always costed as tears).
	 *  12397 = attack, 12394 = special attack (the two plugins disagree anim vs spot-anim). */
	EYE_OF_AYAK("Eye of Ayak", recipe(ItemID.DEMON_TEAR, 1),
		new int[]{12397, 12394}, new int[]{12397, 12394},
		ItemID.EYE_OF_AYAK),

	/** 2 soul + 5 chaos runes per cast. Spot-anim 2125 (both plugins agree). */
	TUMEKENS_SHADOW("Tumeken's shadow", recipe(ItemID.SOULRUNE, 2, ItemID.CHAOSRUNE, 5),
		new int[]{}, new int[]{2125},
		ItemID.TUMEKENS_SHADOW);

	/** Panel / log label. */
	private final String label;
	/** Recharge material item id -&gt; quantity consumed per charge. */
	private final Map<Integer, Integer> recipe;
	/** Player animation ids that mean "one charge was just spent". */
	private final int[] attackAnimations;
	/** Player spot-anim ids that mean "one charge was just spent". */
	private final int[] attackGraphics;
	/** Weapon-slot item ids that spend this charge pool. */
	private final int[] itemIds;

	ChargedWeapon(String label, Map<Integer, Integer> recipe, int[] attackAnimations,
		int[] attackGraphics, int... itemIds)
	{
		this.label = label;
		this.recipe = recipe;
		this.attackAnimations = attackAnimations;
		this.attackGraphics = attackGraphics;
		this.itemIds = itemIds;
	}

	boolean isWeaponItem(int itemId)
	{
		return contains(itemIds, itemId);
	}

	/** True if {@code id} is one of this weapon's attack animations or spot-anims. */
	boolean isAttackSignal(int id)
	{
		return contains(attackAnimations, id) || contains(attackGraphics, id);
	}

	/** gp cost of {@code charges} charges at the given live GE prices. */
	long cost(long charges, IntUnaryOperator gePrice)
	{
		long perCharge = 0;
		for (Map.Entry<Integer, Integer> e : recipe.entrySet())
		{
			perCharge += (long) Math.max(0, gePrice.applyAsInt(e.getKey())) * e.getValue();
		}
		return charges * perCharge;
	}

	/** Recharge materials that {@code charges} charges represent: item id -&gt; quantity. */
	Map<Integer, Integer> materials(long charges)
	{
		final Map<Integer, Integer> m = new LinkedHashMap<>();
		recipe.forEach((id, per) -> m.put(id, (int) Math.min(Integer.MAX_VALUE, charges * per)));
		return m;
	}

	static ChargedWeapon byAttackSignal(int id)
	{
		for (ChargedWeapon w : values())
		{
			if (w.isAttackSignal(id))
			{
				return w;
			}
		}
		return null;
	}

	private static boolean contains(int[] arr, int v)
	{
		for (int x : arr)
		{
			if (x == v)
			{
				return true;
			}
		}
		return false;
	}

	private static Map<Integer, Integer> recipe(int... kv)
	{
		final Map<Integer, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put(kv[i], kv[i + 1]);
		}
		return Collections.unmodifiableMap(m);
	}
}
