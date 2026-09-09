/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Turns a pair of inventory snapshots taken while a bank or deposit-box interface is open
 * into deposit / withdraw {@link Move}s. Pure - the plugin decides when a banking interface
 * is open and supplies the before / after {@code itemId -> quantity} maps.
 *
 * <p>An item that shrank between {@code before} and {@code after} went into the bank
 * (deposit); one that grew came back out (withdraw). Runes in the rune pouch are outside
 * the inventory container, so pouch moves don't register here - which is what we want, a
 * pouch is not "banked".
 */
final class BankTracker
{
	private BankTracker()
	{
	}

	/** One item moving between the inventory and the bank. */
	@Value
	static class Move
	{
		int itemId;
		int qty;
		/** true = into the bank, false = out of it. */
		boolean deposit;
	}

	static List<Move> diff(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		final List<Move> moves = new ArrayList<>();
		if (before == null || after == null || before.isEmpty() && after.isEmpty())
		{
			return moves;
		}
		ContainerSnapshot.lost(before, after).forEach((id, qty) ->
		{
			if (id > 0 && qty > 0)
			{
				moves.add(new Move(id, qty, true));
			}
		});
		ContainerSnapshot.lost(after, before).forEach((id, qty) ->
		{
			if (id > 0 && qty > 0)
			{
				moves.add(new Move(id, qty, false));
			}
		});
		return moves;
	}
}
