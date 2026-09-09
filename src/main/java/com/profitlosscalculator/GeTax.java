/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * The Grand Exchange sales tax a seller pays, per the OSRS Wiki:
 *
 * <ul>
 *     <li><b>2%</b> of the sale price (since 29 May 2025; was 1% from Dec 2021).</li>
 *     <li>Charged <b>per item</b>, rounded down - so an item selling below 50 gp is taxed
 *         nothing, and the seller nets 98 gp whether the price is set to 99 or 100.</li>
 *     <li>Capped at <b>5,000,000 gp</b> per sell offer.</li>
 *     <li>A handful of items are exempt. Only Old School bonds matter here - almost every
 *         other exempt item is worth under 50 gp, which the per-item floor already zeroes.</li>
 * </ul>
 *
 * <p>Pure - unit-tested without a client. If Jagex changes the rate again it is the single
 * {@code / RATE_DIVISOR} below.
 */
final class GeTax
{
	/** 2% == divide by 50 and floor. */
	private static final long RATE_DIVISOR = 50;

	/** Maximum tax on one sell offer. */
	static final long CAP = 5_000_000L;

	private static final Set<Integer> EXEMPT;

	static
	{
		final Set<Integer> ids = new HashSet<>();
		ids.add(ItemID.OSRS_BOND);
		EXEMPT = Collections.unmodifiableSet(ids);
	}

	private GeTax()
	{
	}

	/**
	 * Tax owed on selling {@code qty} of {@code itemId} at {@code unitPrice} each.
	 *
	 * @param itemId    the item sold
	 * @param unitPrice executed price per item (gp)
	 * @param qty       quantity sold
	 * @return tax in gp, never negative, never over {@link #CAP}
	 */
	static long tax(int itemId, long unitPrice, long qty)
	{
		if (unitPrice <= 0 || qty <= 0 || EXEMPT.contains(itemId))
		{
			return 0L;
		}
		final long perItem = unitPrice / RATE_DIVISOR;
		if (perItem <= 0)
		{
			return 0L;
		}
		return Math.min(perItem * qty, CAP);
	}

	static boolean isExempt(int itemId)
	{
		return EXEMPT.contains(itemId);
	}
}
