/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * A batch of items that became available to the player - a monster drop, a PvP kill, a
 * reward chest, a pickpocket, or something taken off the ground.
 *
 * <p>Two figures per event:
 * <ul>
 *     <li>{@link #items} - what dropped / was received ("potential")</li>
 *     <li>{@link #collected} - the subset that actually made it into the player's
 *         possession ("actual", the part that counts toward profit)</li>
 * </ul>
 *
 * For rewards, pickpockets and ground pickups the two are equal from the start - those go
 * straight into the bag. For monster and PvP loot {@code collected} starts empty and is
 * filled in as the plugin sees the items enter the inventory ({@link LootCollector}).
 *
 * <p>The gp value is <em>not</em> frozen: the maps are priced on demand through
 * {@link IncomeValuation}, so switching the valuation config re-prices the whole ledger.
 */
@Getter
class IncomeEvent
{
	enum Type
	{
		/** Monster / boss kill (RuneLite {@code LootRecordType.NPC}). */
		NPC_LOOT,
		/** PvP kill (RuneLite {@code LootRecordType.PLAYER}). */
		PLAYER_LOOT,
		/** Reward chest, casket, minigame reward, etc. (RuneLite {@code LootRecordType.EVENT}). */
		EVENT_LOOT,
		/** Pickpocketing (RuneLite {@code LootRecordType.PICKPOCKET}). */
		PICKPOCKET,
		/** Taken off the ground, not attributable to one of your own kills. */
		PICKUP,
		/** Coins from a High Alchemy cast. */
		ALCH
	}

	private final Type type;
	private final Instant time;

	/** Human label: the NPC / player / event name, or "Ground" for a pickup. */
	private final String source;

	/** itemId -&gt; quantity that dropped / was received. Immutable. */
	private final Map<Integer, Integer> items;

	/** itemId -&gt; quantity confirmed in the player's possession. A subset of {@link #items}. */
	private final Map<Integer, Integer> collected = new LinkedHashMap<>();

	IncomeEvent(Type type, Instant time, String source, Map<Integer, Integer> items)
	{
		this.type = type;
		this.time = time;
		this.source = source;
		this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
		if (type != Type.NPC_LOOT && type != Type.PLAYER_LOOT)
		{
			// rewards / pickpockets / deliberate pickups are already in hand
			this.collected.putAll(this.items);
		}
	}

	/** Quantity of {@code id} from this event not yet accounted as collected. */
	int outstanding(int id)
	{
		return Math.max(0, items.getOrDefault(id, 0) - collected.getOrDefault(id, 0));
	}

	boolean fullyCollected()
	{
		for (int id : items.keySet())
		{
			if (outstanding(id) > 0)
			{
				return false;
			}
		}
		return true;
	}

	/** Mark up to {@code qty} of {@code id} collected; returns how many were actually applied. */
	int collect(int id, int qty)
	{
		final int room = Math.min(qty, outstanding(id));
		if (room > 0)
		{
			collected.merge(id, room, Integer::sum);
		}
		return room;
	}
}
