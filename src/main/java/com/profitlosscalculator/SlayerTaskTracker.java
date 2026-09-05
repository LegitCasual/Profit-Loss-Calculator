/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.NPC;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.util.Text;

/**
 * Wraps RuneLite's bundled Slayer plugin service. Rather than re-implementing task -&gt;
 * monster-family matching (aliases, superiors, boss tasks, area filters, ...), this leans on
 * {@link SlayerPluginService#getTargets()} - the exact live NPC list RuneLite's own Slayer
 * plugin already resolves for its highlighter - and accumulates every name it has ever
 * reported into a growing set.
 *
 * <p>A Slayer run is allowed to span several task assignments back to back (the run is not
 * stopped when the task changes mid-run), so the matched-name set only ever grows between
 * {@link #reset()} calls - it is not re-scoped to "the current task" on every poll.
 */
class SlayerTaskTracker
{
	private final Set<String> matchedNames = new HashSet<>();

	private String taskName;
	private String taskLocation;
	private int initialAmount;
	private int remainingAmount;

	/** Forget every matched name accumulated so far. Called once, when a Slayer run starts. */
	void reset()
	{
		matchedNames.clear();
	}

	/**
	 * Refresh the cached task name / location / progress. Cheap field reads on the service -
	 * RuneLite's Slayer plugin already did the work reactively - so this is safe to call every
	 * tick regardless of whether a Slayer run is active (it drives the idle "here's your
	 * current task" preview too).
	 *
	 * @return true if anything changed, so the caller can push a fresh view without waiting on
	 *         an unrelated event to trigger one
	 */
	boolean pollLiveInfo(SlayerPluginService service)
	{
		final String name = service.getTask();
		final String location = service.getTaskLocation();
		final int initial = service.getInitialAmount();
		final int remaining = service.getRemainingAmount();

		final boolean changed = !java.util.Objects.equals(name, taskName)
			|| !java.util.Objects.equals(location, taskLocation)
			|| initial != initialAmount
			|| remaining != remainingAmount;

		taskName = name;
		taskLocation = location;
		initialAmount = initial;
		remainingAmount = remaining;
		return changed;
	}

	/**
	 * Merge the current task's live target names into the matched set. Only meant to be called
	 * while a Slayer run is actively accruing - the scene scan itself is RuneLite's Slayer
	 * plugin's job, this just reads its already-computed list.
	 */
	void accumulateTargets(SlayerPluginService service)
	{
		for (NPC npc : service.getTargets())
		{
			if (npc != null)
			{
				accumulateName(npc.getName());
			}
		}
	}

	/** The name-cleaning/merging logic behind {@link #accumulateTargets}, decoupled from
	 *  {@link NPC} so it can be unit tested without mocking the game API. Package-private for
	 *  tests. */
	void accumulateName(String rawName)
	{
		if (rawName == null)
		{
			return;
		}
		final String cleaned = Text.removeTags(rawName).trim();
		if (!cleaned.isEmpty())
		{
			matchedNames.add(cleaned.toLowerCase(Locale.ROOT));
		}
	}

	/** True if {@code npcName} has ever been seen among this run's task targets. */
	boolean isTaskMob(String npcName)
	{
		if (npcName == null)
		{
			return false;
		}
		return matchedNames.contains(Text.removeTags(npcName).trim().toLowerCase(Locale.ROOT));
	}

	boolean hasTask()
	{
		return taskName != null && !taskName.isEmpty();
	}

	String getTaskName()
	{
		return taskName;
	}

	String getTaskLocation()
	{
		return taskLocation;
	}

	int getInitialAmount()
	{
		return initialAmount;
	}

	int getRemainingAmount()
	{
		return remainingAmount;
	}
}
