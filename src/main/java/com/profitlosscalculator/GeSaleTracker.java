/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Turns the stream of {@code GrandExchangeOfferChanged} events into completed-sale deltas,
 * the same way RuneLite's own {@code GrandExchangePlugin} does: keep the last seen
 * {@code (quantitySold, spent)} per slot, and a positive change in both is a fill.
 *
 * <ul>
 *     <li>Only <b>sell</b> offers produce {@link Sale}s - buys are ignored (supplies are
 *         costed by consumption, not purchase).</li>
 *     <li>A slot with no comparable prior state is <b>seeded silently</b> - a sale is only
 *         ever emitted as a positive delta against a known baseline, so an offer already in
 *         progress the first time the plugin sees it never back-dates a phantom sale.</li>
 *     <li>{@link #snapshot()} / {@link #restore(Map)} persist the per-slot state so a sale
 *         that completes while logged out is attributed once (the pre-logout partial is the
 *         baseline) rather than double-counted.</li>
 *     <li>During the post-login burst (the client re-sends every slot, EMPTY first) an EMPTY
 *         is ignored so it doesn't wipe a restored baseline.</li>
 * </ul>
 *
 * <p>Pure and unit-testable.
 */
class GeSaleTracker
{
	/** Per-slot last-seen offer state. Plain POJO for trivial Gson round-tripping. */
	static final class SlotState
	{
		int itemId;
		String state;
		int qtySold;
		long spent;
		/** GE tax already attributed to fills of this offer, for the per-offer cap. */
		long taxedSoFar;

		SlotState()
		{
		}

		SlotState(int itemId, String state, int qtySold, long spent, long taxedSoFar)
		{
			this.itemId = itemId;
			this.state = state;
			this.qtySold = qtySold;
			this.spent = spent;
			this.taxedSoFar = taxedSoFar;
		}

		SlotState copy()
		{
			return new SlotState(itemId, state, qtySold, spent, taxedSoFar);
		}
	}

	/** One completed fill of a sell offer. */
	@Value
	static class Sale
	{
		int itemId;
		int qty;
		/** gross coins the buyer(s) paid for this fill. */
		long gross;
		/** GE tax on this fill (0 when tax deduction is off). */
		long tax;
	}

	/** {@code (itemId, executedUnitPrice, cumulativeQty) -> total tax for the offer so far}. */
	interface TaxFn
	{
		long tax(int itemId, long unitPrice, long cumulativeQty);
	}

	private final Map<Integer, SlotState> slots = new HashMap<>();

	List<Sale> onOffer(int slot, int itemId, GrandExchangeOfferState state, int qtySold,
		long spent, boolean loginBurst, TaxFn taxFn)
	{
		if (state == GrandExchangeOfferState.EMPTY)
		{
			// after the login burst an EMPTY means the slot was genuinely collected / cleared;
			// during the burst it is just the client re-initialising and must not wipe state
			if (!loginBurst)
			{
				slots.remove(slot);
			}
			return Collections.emptyList();
		}

		final boolean sell = state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_SELL;

		SlotState st = slots.get(slot);
		if (st == null || st.itemId != itemId)
		{
			// no comparable baseline - record where we are, emit nothing
			slots.put(slot, new SlotState(itemId, state.name(), qtySold, spent, 0L));
			return Collections.emptyList();
		}

		final int dqty = qtySold - st.qtySold;
		final long dspent = spent - st.spent;
		st.state = state.name();
		st.qtySold = qtySold;
		st.spent = spent;

		if (!sell || dqty <= 0 || dspent <= 0)
		{
			return Collections.emptyList();
		}

		final long unitPrice = dspent / dqty;
		final long offerTax = taxFn == null ? 0L : Math.max(0L, taxFn.tax(itemId, unitPrice, qtySold));
		final long taxThisFill = Math.max(0L, offerTax - st.taxedSoFar);
		st.taxedSoFar = Math.max(st.taxedSoFar, offerTax);

		return Collections.singletonList(new Sale(itemId, dqty, dspent, taxThisFill));
	}

	Map<Integer, SlotState> snapshot()
	{
		final Map<Integer, SlotState> out = new HashMap<>();
		slots.forEach((k, v) -> out.put(k, v.copy()));
		return out;
	}

	void restore(Map<Integer, SlotState> saved)
	{
		slots.clear();
		if (saved != null)
		{
			saved.forEach((k, v) ->
			{
				if (v != null)
				{
					slots.put(k, v.copy());
				}
			});
		}
	}
}
