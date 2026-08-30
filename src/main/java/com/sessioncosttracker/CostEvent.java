/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * A single confirmed gp outlay inside a trip (a consumable used, a spell cast, or a
 * teleport charge spent). Ammo is aggregated on the {@link Trip} rather than logged as
 * individual events. Death loss is tracked separately as a {@link DeathEntry} until it
 * resolves.
 */
@Value
class CostEvent
{
	enum Type
	{
		CONSUMABLE,
		SPELL,
		TELEPORT
	}

	Type type;
	Instant time;
	int tripId;

	/** Item consumed (CONSUMABLE/TELEPORT), or -1 for SPELL. */
	int itemId;
	int quantity;

	/** gp charged for this event (already net of staff-provided runes for spells). */
	long gp;

	/** Human label: item name, spell name or teleport description. */
	String label;

	/** Optional per-rune breakdown for a spell cast; null otherwise. */
	@Nullable
	Map<String, Long> detail;
}
