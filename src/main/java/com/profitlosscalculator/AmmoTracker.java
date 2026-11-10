/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns "combined owned ammo quantity per item id" snapshots into per-item consumption,
 * split into what left the slot ("fired") and what came back ("recovered"), so the panel
 * can show the working, not just the net.
 *
 * <p>The plugin feeds this the total quantity it can see for each ammo-ish item - the
 * equipped ammo/thrown slot, cannon loaded balls, Dizana's quiver, inventory copies. A
 * drop is fired ammo; a rise, up to the amount still outstanding, is ammo picked back up.
 * A rise beyond that (a shop buy, withdrawing more) just re-bases. Ava's recovery never
 * decrements the slot, so it never even counts as fired.
 *
 * <p>State is per tracking window - the plugin calls {@link #reset} at every trip boundary,
 * so {@link #stats} is always "this trip". Pure and unit-testable.
 */
class AmmoTracker
{
	private final Map<Integer, Long> baseline = new HashMap<>();
	private final Map<Integer, Long> fired = new LinkedHashMap<>();
	private final Map<Integer, Long> recovered = new HashMap<>();
	/** Fired ammo written off by {@link #forfeitOutstanding} - no longer out there to pick up. */
	private final Map<Integer, Long> forfeited = new HashMap<>();

	/** Ids currently carrying a baseline - the plugin also totals their inventory copies. */
	Set<Integer> tracked()
	{
		return Collections.unmodifiableSet(baseline.keySet());
	}

	/** Forget everything and treat {@code owned} as the new zero point (trip boundary). */
	void reset(Map<Integer, Long> owned)
	{
		baseline.clear();
		fired.clear();
		recovered.clear();
		forfeited.clear();
		owned.forEach((id, qty) ->
		{
			if (qty > 0)
			{
				baseline.put(id, qty);
			}
		});
	}

	/**
	 * @param accrue when false, only re-base (used while a death is being resolved or a
	 *               bank is open, where the counts legitimately jump around)
	 * @return true if the fired/recovered tallies moved
	 */
	boolean reconcile(Map<Integer, Long> owned, boolean accrue)
	{
		boolean changed = false;

		final Set<Integer> ids = new HashSet<>(baseline.keySet());
		ids.addAll(owned.keySet());

		for (int id : ids)
		{
			final long before = baseline.getOrDefault(id, 0L);
			final long now = Math.max(0L, owned.getOrDefault(id, 0L));

			if (accrue && now < before)
			{
				fired.merge(id, before - now, Long::sum);
				changed = true;
			}
			else if (accrue && now > before)
			{
				// a rise while this id is tracked = picking your own ammo back up, but only
				// up to what is still out there - anything more is a restock, not recovery
				final long outstanding = Math.max(0L, fired.getOrDefault(id, 0L)
					- recovered.getOrDefault(id, 0L) - forfeited.getOrDefault(id, 0L));
				final long pickedUp = Math.min(now - before, outstanding);
				if (pickedUp > 0)
				{
					recovered.merge(id, pickedUp, Long::sum);
					changed = true;
				}
			}

			if (now <= 0L)
			{
				baseline.remove(id);
			}
			else
			{
				baseline.put(id, now);
			}
		}

		return changed;
	}

	/**
	 * Everything fired so far is gone for good (the player died and respawned away from it),
	 * so a later rise - the grave / Death's Office handing the kit back - is a restock, not
	 * ammo picked back up. The net spend is unchanged.
	 */
	void forfeitOutstanding()
	{
		fired.forEach((id, f) -> forfeited.put(id,
			Math.max(0L, f - recovered.getOrDefault(id, 0L))));
	}

	/** Frozen copy of this tracker's state, for {@link #restore}. */
	Snapshot snapshot()
	{
		return new Snapshot(baseline, fired, recovered, forfeited);
	}

	/**
	 * Roll back to {@code s}, undoing every fire / recovery since it was taken. Used on death:
	 * the client strips the kit a tick or two before the death event arrives, so that drop has
	 * already been read as firing the whole stack by the time the plugin knows it was a death.
	 */
	void restore(Snapshot s)
	{
		copy(s.baseline, baseline);
		copy(s.fired, fired);
		copy(s.recovered, recovered);
		copy(s.forfeited, forfeited);
	}

	private static void copy(Map<Integer, Long> from, Map<Integer, Long> to)
	{
		to.clear();
		to.putAll(from);
	}

	static final class Snapshot
	{
		private final Map<Integer, Long> baseline;
		private final Map<Integer, Long> fired;
		private final Map<Integer, Long> recovered;
		private final Map<Integer, Long> forfeited;

		private Snapshot(Map<Integer, Long> baseline, Map<Integer, Long> fired,
			Map<Integer, Long> recovered, Map<Integer, Long> forfeited)
		{
			this.baseline = new HashMap<>(baseline);
			this.fired = new LinkedHashMap<>(fired);
			this.recovered = new HashMap<>(recovered);
			this.forfeited = new HashMap<>(forfeited);
		}
	}

	/** id -&gt; {fired, recovered, net} for this window; net = max(0, fired - recovered). */
	Map<Integer, long[]> stats()
	{
		final Map<Integer, long[]> out = new LinkedHashMap<>();
		fired.forEach((id, f) ->
		{
			final long r = recovered.getOrDefault(id, 0L);
			out.put(id, new long[]{f, r, Math.max(0L, f - r)});
		});
		return out;
	}
}
