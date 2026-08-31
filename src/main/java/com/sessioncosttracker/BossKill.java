/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * One boss kill and the loot events attributed to it. The {@link IncomeEvent}s here are the
 * same objects held in {@link Session#getIncome()} - this is just a per-kill grouping for
 * the panel and the session log, not a second copy of the data.
 */
@Getter
class BossKill
{
	/** 1-based kill number within the session. */
	private final int index;
	private final Instant time;
	/** NPC name that triggered the kill (or the configured boss name for a manual +1). */
	private final String name;
	private final List<IncomeEvent> drops = new ArrayList<>();

	/** Session running ammo-gp total at the moment of the kill - lets a fight's ammo spend
	 *  be derived as the delta from the previous kill. */
	@Setter
	private long ammoGpAtKill;

	BossKill(int index, Instant time, String name)
	{
		this.index = index;
		this.time = time;
		this.name = name;
	}

	void add(IncomeEvent drop)
	{
		drops.add(drop);
	}
}
