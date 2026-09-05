/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Collections;
import java.util.List;
import net.runelite.api.NPC;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlayerTaskTrackerTest
{
	private static final class FakeService implements SlayerPluginService
	{
		private final String task;
		private final String location;
		private final int initial;
		private final int remaining;

		private FakeService(String task, String location, int initial, int remaining)
		{
			this.task = task;
			this.location = location;
			this.initial = initial;
			this.remaining = remaining;
		}

		@Override
		public List<NPC> getTargets()
		{
			return Collections.emptyList();
		}

		@Override
		public String getTask()
		{
			return task;
		}

		@Override
		public String getTaskLocation()
		{
			return location;
		}

		@Override
		public int getInitialAmount()
		{
			return initial;
		}

		@Override
		public int getRemainingAmount()
		{
			return remaining;
		}
	}

	@Test
	public void matchIsCaseInsensitiveAndTagStripped()
	{
		final SlayerTaskTracker t = new SlayerTaskTracker();
		t.accumulateName("<col=ff0000>Aberrant spectre</col>");

		assertTrue(t.isTaskMob("aberrant spectre"));
		assertTrue(t.isTaskMob("ABERRANT SPECTRE"));
		assertFalse(t.isTaskMob("Cow"));
	}

	@Test
	public void nullAndBlankNamesAreIgnored()
	{
		final SlayerTaskTracker t = new SlayerTaskTracker();
		t.accumulateName(null);
		t.accumulateName("   ");

		assertFalse(t.isTaskMob(null));
		assertFalse(t.isTaskMob(""));
	}

	@Test
	public void matchedNamesAccumulateAcrossTaskChanges()
	{
		// "keep tracking, fold in the new task" - a run spanning two tasks should still match
		// mobs from the first one after the second is picked up.
		final SlayerTaskTracker t = new SlayerTaskTracker();
		t.accumulateName("Aberrant spectre");
		t.accumulateName("Choke devil"); // second task's alias mob

		assertTrue(t.isTaskMob("Aberrant spectre"));
		assertTrue(t.isTaskMob("Choke devil"));
	}

	@Test
	public void resetClearsAccumulatedNames()
	{
		final SlayerTaskTracker t = new SlayerTaskTracker();
		t.accumulateName("Aberrant spectre");
		t.reset();

		assertFalse(t.isTaskMob("Aberrant spectre"));
	}

	@Test
	public void pollLiveInfoCachesTaskSnapshot()
	{
		final SlayerTaskTracker t = new SlayerTaskTracker();
		assertFalse(t.hasTask());

		assertTrue(t.pollLiveInfo(new FakeService("Aberrant spectres", "Catacombs of Kourend", 130, 42)));

		assertTrue(t.hasTask());
		assertEquals("Aberrant spectres", t.getTaskName());
		assertEquals("Catacombs of Kourend", t.getTaskLocation());
		assertEquals(130, t.getInitialAmount());
		assertEquals(42, t.getRemainingAmount());
	}

	@Test
	public void pollLiveInfoReportsWhetherAnythingChanged()
	{
		final SlayerTaskTracker t = new SlayerTaskTracker();
		final FakeService task = new FakeService("Aberrant spectres", "Catacombs of Kourend", 130, 42);

		assertTrue(t.pollLiveInfo(task));
		// same snapshot again - nothing changed
		assertFalse(t.pollLiveInfo(task));
		// remaining count ticked down - a real change
		assertTrue(t.pollLiveInfo(new FakeService("Aberrant spectres", "Catacombs of Kourend", 130, 41)));
		// new task entirely
		assertTrue(t.pollLiveInfo(new FakeService("Choke devils", "Catacombs of Kourend", 130, 130)));
	}

	@Test
	public void noTaskIsReportedAsEmpty()
	{
		final SlayerTaskTracker t = new SlayerTaskTracker();
		t.pollLiveInfo(new FakeService(null, null, 0, 0));

		assertFalse(t.hasTask());
	}
}
