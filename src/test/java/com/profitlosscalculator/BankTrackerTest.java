/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BankTrackerTest
{
	private static Map<Integer, Integer> inv(int... kv)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	@Test
	public void anItemLeavingTheInventoryIsADeposit()
	{
		List<BankTracker.Move> moves = BankTracker.diff(inv(536, 100, 995, 5000), inv(995, 5000));
		assertEquals(1, moves.size());
		assertEquals(536, moves.get(0).getItemId());
		assertEquals(100, moves.get(0).getQty());
		assertTrue(moves.get(0).isDeposit());
	}

	@Test
	public void anItemArrivingIsAWithdrawal()
	{
		List<BankTracker.Move> moves = BankTracker.diff(inv(995, 5000), inv(536, 40, 995, 5000));
		assertEquals(1, moves.size());
		assertEquals(536, moves.get(0).getItemId());
		assertEquals(40, moves.get(0).getQty());
		assertTrue(!moves.get(0).isDeposit());
	}

	@Test
	public void aPartialDepositReportsTheDelta()
	{
		List<BankTracker.Move> moves = BankTracker.diff(inv(536, 100), inv(536, 30));
		assertEquals(1, moves.size());
		assertEquals(70, moves.get(0).getQty());
		assertTrue(moves.get(0).isDeposit());
	}

	@Test
	public void depositAndWithdrawInOneChange()
	{
		List<BankTracker.Move> moves = BankTracker.diff(inv(536, 100, 561, 200), inv(561, 500));
		assertEquals(2, moves.size());
		// order: deposits then withdrawals
		assertEquals(536, moves.get(0).getItemId());
		assertTrue(moves.get(0).isDeposit());
		assertEquals(561, moves.get(1).getItemId());
		assertEquals(300, moves.get(1).getQty());
		assertTrue(!moves.get(1).isDeposit());
	}

	@Test
	public void noChangeNoMoves()
	{
		assertTrue(BankTracker.diff(inv(536, 100), inv(536, 100)).isEmpty());
		assertTrue(BankTracker.diff(inv(), inv()).isEmpty());
	}
}
