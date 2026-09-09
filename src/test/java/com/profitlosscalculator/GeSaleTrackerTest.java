/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class GeSaleTrackerTest
{
	private static final int BONES = ItemID.DRAGON_BONES;

	private static List<GeSaleTracker.Sale> feed(GeSaleTracker t, int slot, GrandExchangeOfferState st,
		int qtySold, long spent, boolean burst)
	{
		return t.onOffer(slot, BONES, st, qtySold, spent, burst, null);
	}

	@Test
	public void firstSightOfAnOfferSeedsWithoutEmitting()
	{
		final GeSaleTracker t = new GeSaleTracker();
		assertTrue(feed(t, 0, GrandExchangeOfferState.SELLING, 0, 0, false).isEmpty());
		// an offer already 50 in when first seen also seeds silently - those 50 predate tracking
		assertTrue(feed(t, 1, GrandExchangeOfferState.SELLING, 50, 50_000, false).isEmpty());
	}

	@Test
	public void partialFillsThenCompletion()
	{
		final GeSaleTracker t = new GeSaleTracker();
		feed(t, 0, GrandExchangeOfferState.SELLING, 0, 0, false);

		List<GeSaleTracker.Sale> s1 = feed(t, 0, GrandExchangeOfferState.SELLING, 50, 50_000, false);
		assertEquals(1, s1.size());
		assertEquals(50, s1.get(0).getQty());
		assertEquals(50_000L, s1.get(0).getGross());

		List<GeSaleTracker.Sale> s2 = feed(t, 0, GrandExchangeOfferState.SOLD, 100, 100_000, false);
		assertEquals(1, s2.size());
		assertEquals(50, s2.get(0).getQty());
		assertEquals(50_000L, s2.get(0).getGross());

		// a repeat of the final state is not a new sale
		assertTrue(feed(t, 0, GrandExchangeOfferState.SOLD, 100, 100_000, false).isEmpty());
	}

	@Test
	public void buyOffersAreIgnored()
	{
		final GeSaleTracker t = new GeSaleTracker();
		feed(t, 0, GrandExchangeOfferState.BUYING, 0, 0, false);
		assertTrue(feed(t, 0, GrandExchangeOfferState.BUYING, 100, 500_000, false).isEmpty());
		assertTrue(feed(t, 0, GrandExchangeOfferState.BOUGHT, 200, 1_000_000, false).isEmpty());
	}

	@Test
	public void emptyDuringLoginBurstDoesNotWipeState()
	{
		final GeSaleTracker t = new GeSaleTracker();
		feed(t, 0, GrandExchangeOfferState.SELLING, 0, 0, false);
		feed(t, 0, GrandExchangeOfferState.SELLING, 50, 50_000, false);
		// login burst re-sends the slot as EMPTY first
		assertTrue(feed(t, 0, GrandExchangeOfferState.EMPTY, 0, 0, true).isEmpty());
		// ...then as SOLD with the full qty - only the remaining 50 is new
		List<GeSaleTracker.Sale> s = feed(t, 0, GrandExchangeOfferState.SOLD, 100, 100_000, true);
		assertEquals(1, s.size());
		assertEquals(50, s.get(0).getQty());
	}

	@Test
	public void emptyAfterTheBurstClearsTheSlot()
	{
		final GeSaleTracker t = new GeSaleTracker();
		feed(t, 0, GrandExchangeOfferState.SELLING, 0, 0, false);
		feed(t, 0, GrandExchangeOfferState.SELLING, 50, 50_000, false);
		feed(t, 0, GrandExchangeOfferState.EMPTY, 0, 0, false);   // collected
		// a brand new offer in the same slot seeds fresh, no phantom sale of the old 50
		assertTrue(feed(t, 0, GrandExchangeOfferState.SELLING, 30, 30_000, false).isEmpty());
	}

	@Test
	public void snapshotRestoreRoundTrip()
	{
		final GeSaleTracker a = new GeSaleTracker();
		feed(a, 0, GrandExchangeOfferState.SELLING, 0, 0, false);
		feed(a, 0, GrandExchangeOfferState.SELLING, 40, 40_000, false);
		final Map<Integer, GeSaleTracker.SlotState> snap = a.snapshot();

		final GeSaleTracker b = new GeSaleTracker();
		b.restore(snap);
		// b picks up exactly where a left off - only the new 60 counts
		List<GeSaleTracker.Sale> s = feed(b, 0, GrandExchangeOfferState.SOLD, 100, 100_000, true);
		assertEquals(1, s.size());
		assertEquals(60, s.get(0).getQty());
		assertEquals(60_000L, s.get(0).getGross());
	}

	@Test
	public void taxAccumulatesAcrossFillsAndRespectsTheCap()
	{
		final GeSaleTracker t = new GeSaleTracker();
		t.onOffer(0, BONES, GrandExchangeOfferState.SELLING, 0, 0, false, GeTax::tax);
		List<GeSaleTracker.Sale> f1 = t.onOffer(0, BONES, GrandExchangeOfferState.SELLING,
			100, 100_000, false, GeTax::tax);
		assertEquals(2_000L, f1.get(0).getTax());   // 2% of 1000, x100
		List<GeSaleTracker.Sale> f2 = t.onOffer(0, BONES, GrandExchangeOfferState.SOLD,
			200, 200_000, false, GeTax::tax);
		assertEquals(2_000L, f2.get(0).getTax());   // cumulative 4000 - 2000 already taxed
	}
}
