/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.util.Text;

/**
 * One Start-to-Stop run. Holds every cost event, every income event, every death, the ammo
 * tally and the boss kill count. Can be paused - while paused the plugin stops accruing to
 * it.
 *
 * <p>A run is one of three modes ({@link RunMode}). A plain <b>session</b> is a flat "record
 * my profit / loss for this stretch of time" ledger. A <b>targeted farm</b> attributes kills
 * and loot for one or more named mobs ({@link #targetMobs} - you can add more to the group
 * mid-run without stopping). A <b>Slayer</b> run attributes kills and loot for every mob
 * that has ever counted toward the current task while the run was live (matched via
 * {@link SlayerTaskTracker} - the set only grows, so getting reassigned to a new task mid-run
 * folds the new task's mobs in rather than resetting). Either grouped mode charges every cost
 * incurred while it runs to the farm/task and the panel shows a per-kill net.
 */
@Getter
class Session
{
	enum RunMode
	{
		SESSION, TARGETED, SLAYER
	}

	private final Instant startTime;

	@Setter
	private Instant endTime;

	@Setter
	private boolean paused;

	@Setter
	private RunMode mode = RunMode.SESSION;

	/** The farmed mobs' names, for {@link RunMode#TARGETED} only, in the order added. */
	private final List<String> targetMobs = new ArrayList<>();

	private final List<CostEvent> events = new ArrayList<>();
	private final List<IncomeEvent> income = new ArrayList<>();
	private final List<DeathEntry> deaths = new ArrayList<>();
	private final List<BossKill> kills = new ArrayList<>();

	/** Ammo used this session: item id -&gt; {fired, recovered, net}. */
	private Map<Integer, long[]> ammoStats = new LinkedHashMap<>();
	private long ammoGp;

	/** Charged-weapon charges spent this session: weapon -&gt; charges. */
	private Map<ChargedWeapon, Long> chargedWeaponsSpent = new EnumMap<>(ChargedWeapon.class);
	private long chargedWeaponGp;

	/** Runes consumed by spell casts this session: rune item id -&gt; quantity. */
	private final Map<Integer, Integer> runesUsed = new LinkedHashMap<>();

	private int deathSeq;

	/** Plain-session kill tally (a targeted farm counts {@link #kills} buckets instead). */
	private int killCount;

	/** NPC name -&gt; kills of it this run. Empty key is never used. */
	private final Map<String, Integer> killsByMob = new LinkedHashMap<>();

	/** NPC name -&gt; gp spent while fighting it (consumables, spells, teleports, ammo). The
	 *  empty-string key holds cost incurred while not in combat. Deaths are added here on
	 *  resolution. */
	private final Map<String, Long> costByMob = new LinkedHashMap<>();

	Session(Instant startTime)
	{
		this.startTime = startTime;
	}

	/** Bump the kill tally, and the per-mob count for {@code mob} (if given). */
	void bumpKill(String mob)
	{
		killCount++;
		if (mob != null && !mob.isEmpty())
		{
			killsByMob.merge(mob, 1, Integer::sum);
		}
	}

	/** Charge {@code gp} against {@code mob} ({@code null}/empty =&gt; the "not in combat" bucket). */
	void addMobCost(String mob, long gp)
	{
		if (gp == 0)
		{
			return;
		}
		costByMob.merge(mob == null ? "" : mob, gp, Long::sum);
	}

	/**
	 * Add a mob to a Targeted run's target group - case-insensitive de-duplicated, order
	 * preserved. No-op if blank or already present. Safe to call at any point during the run,
	 * not just at start - this is how a farm grows from one mob to several.
	 */
	void addTargetMob(String mob)
	{
		if (mob == null)
		{
			return;
		}
		final String trimmed = mob.trim();
		if (trimmed.isEmpty())
		{
			return;
		}
		for (String existing : targetMobs)
		{
			if (existing.equalsIgnoreCase(trimmed))
			{
				return;
			}
		}
		targetMobs.add(trimmed);
	}

	/** True when this run is a Targeted farm specifically (one or more named mobs). */
	boolean isTargeted()
	{
		return mode == RunMode.TARGETED;
	}

	/** True when this run is a Slayer task run. */
	boolean isSlayer()
	{
		return mode == RunMode.SLAYER;
	}

	/** True for either grouped mode (Targeted or Slayer) - a run with a per-kill bucket and an
	 *  "Other income" split, as opposed to a plain session where everything just counts. */
	boolean isGrouped()
	{
		return mode != RunMode.SESSION;
	}

	/**
	 * True when {@code npcName} counts toward this run's target group: one of the farmed mobs
	 * for a Targeted run, or a mob that has matched the Slayer task at some point during this
	 * run for a Slayer run. Always false for a plain session (nothing is "targeted"), and false
	 * for Slayer if {@code slayerTracker} is {@code null}.
	 */
	boolean matchesTarget(String npcName, SlayerTaskTracker slayerTracker)
	{
		switch (mode)
		{
			case TARGETED:
				for (String target : targetMobs)
				{
					if (nameMatches(npcName, target))
					{
						return true;
					}
				}
				return false;
			case SLAYER:
				return slayerTracker != null && slayerTracker.isTaskMob(npcName);
			default:
				return false;
		}
	}

	/** Exact, case-insensitive, tag-stripped NPC-name match. */
	static boolean nameMatches(String candidate, String target)
	{
		if (candidate == null || target == null)
		{
			return false;
		}
		return Text.removeTags(candidate).trim().equalsIgnoreCase(target.trim());
	}

	int nextDeathId()
	{
		return ++deathSeq;
	}

	void add(CostEvent event)
	{
		events.add(event);
	}

	void add(IncomeEvent event)
	{
		income.add(event);
	}

	void add(DeathEntry death)
	{
		deaths.add(death);
	}

	/** Open a new kill bucket for the farmed mob; later matching loot is attributed to it. */
	BossKill addBossKill(String name)
	{
		final BossKill kill = new BossKill(kills.size() + 1, Instant.now(), name);
		kills.add(kill);
		return kill;
	}

	/** Record runes spent on a cast (rune item id -&gt; quantity), for the loss icon grid. */
	void addRunes(Map<Integer, Integer> runes)
	{
		runes.forEach((id, qty) -> runesUsed.merge(id, qty, Integer::sum));
	}

	BossKill lastKill()
	{
		return kills.isEmpty() ? null : kills.get(kills.size() - 1);
	}

	/** Kill count for the panel: farm buckets when grouped (Targeted/Slayer), else the plain
	 *  tally (never less than the number of buckets, so direct {@link #addBossKill} in tests
	 *  still counts). */
	int getBossKills()
	{
		return isGrouped() ? kills.size() : Math.max(killCount, kills.size());
	}

	/** Replace the ammo tally (the tracker hands back a fresh running total each time). */
	void setAmmo(Map<Integer, long[]> stats, long gp)
	{
		this.ammoStats = stats;
		this.ammoGp = gp;
	}

	/** Replace the charged-weapon tally (running total, priced live off the GE). */
	void setChargedWeapons(Map<ChargedWeapon, Long> spent, long gp)
	{
		this.chargedWeaponsSpent = new EnumMap<>(spent);
		this.chargedWeaponGp = gp;
	}

	/** Never negative. */
	long chargedWeaponTotal()
	{
		return Math.max(0, chargedWeaponGp);
	}

	long consumableTotal()
	{
		return typeTotal(CostEvent.Type.CONSUMABLE);
	}

	long spellTotal()
	{
		return typeTotal(CostEvent.Type.SPELL);
	}

	long teleportTotal()
	{
		return typeTotal(CostEvent.Type.TELEPORT);
	}

	private long typeTotal(CostEvent.Type type)
	{
		return events.stream()
			.filter(e -> e.getType() == type)
			.mapToLong(CostEvent::getGp)
			.sum();
	}

	/** Never negative - picking your own ammo back up can briefly push the tally down. */
	long ammoTotal()
	{
		return Math.max(0, ammoGp);
	}

	/** Death cost that has resolved and been confirmed (or locked in as a full loss). */
	long confirmedDeathTotal()
	{
		return deaths.stream()
			.filter(DeathEntry::isCounted)
			.mapToLong(DeathEntry::getResolvedCost)
			.sum();
	}

	/** Value of deaths still waiting on resolution/confirmation. */
	long atRiskTotal()
	{
		return deaths.stream().mapToLong(DeathEntry::atRiskValue).sum();
	}

	/** Total gp spent this session (supplies, spells, teleports, ammo, weapon charges, deaths). */
	long total()
	{
		return consumableTotal() + spellTotal() + teleportTotal() + ammoTotal()
			+ chargedWeaponTotal() + confirmedDeathTotal();
	}

	/**
	 * Value of every income event, priced by the caller's function (which chooses whether
	 * to look at what dropped or only what was collected).
	 */
	long incomeTotal(ToLongFunction<IncomeEvent> pricer)
	{
		return income.stream().mapToLong(pricer).sum();
	}
}
