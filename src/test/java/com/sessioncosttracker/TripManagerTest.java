/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class TripManagerTest
{
	private final List<String> opened = new ArrayList<>();
	private final List<Boolean> closedCollapsed = new ArrayList<>();
	private TripManager tm;
	private int tick;

	@Before
	public void setUp()
	{
		opened.clear();
		closedCollapsed.clear();
		tick = 0;
		tm = new TripManager(() -> 12, new TripManager.Listener()
		{
			@Override
			public void onTripOpened(Trip trip, String reason)
			{
				opened.add(reason);
			}

			@Override
			public void onTripClosed(Trip trip, boolean collapsed)
			{
				closedCollapsed.add(collapsed);
			}
		});
	}

	private Session start(int x, int y)
	{
		return tm.start(Instant.now(), tick, x, y);
	}

	private boolean bank(int x, int y)
	{
		return tm.boundary("bank", Instant.now(), tick, x, y);
	}

	private void spend(Session s)
	{
		s.getCurrentTrip().add(new CostEvent(CostEvent.Type.CONSUMABLE, Instant.now(),
			s.getCurrentTrip().getId(), 1, 1, 100, "food", null));
	}

	@Test
	public void startsTripOne()
	{
		Session s = start(3200, 3200);
		assertNotNull(s.getCurrentTrip());
		assertEquals(1, s.getCurrentTrip().getId());
		assertEquals(1, opened.size());
	}

	@Test
	public void bankReopenInOneVisitDoesNotCutNewTrip()
	{
		Session s = start(3200, 3200);
		spend(s);
		// same tick, hasn't moved: interface-load + menu-click both fire -> one trip
		assertFalse(bank(3201, 3200));
		tick += 2;
		assertFalse(bank(3202, 3201));
		assertEquals(1, s.getCurrentTrip().getId());
	}

	@Test
	public void afkStandingAtBankDoesNotCutNewTrips()
	{
		Session s = start(3200, 3200);
		spend(s);
		for (int i = 0; i < 8; i++)
		{
			tick += 30; // long quiet gaps, but the player never moves
			assertFalse("cut on reopen " + i, bank(3200, 3200));
		}
		assertEquals(1, s.getCurrentTrip().getId());
		assertEquals(0, s.getTrips().size());
	}

	@Test
	public void leavingAndReturningCutsANewTrip()
	{
		Session s = start(3200, 3200);
		spend(s);
		tick += 20;
		tm.updatePosition(3260, 3200); // 60 tiles away - definitely left
		assertTrue(bank(3200, 3200));
		assertEquals(2, s.getCurrentTrip().getId());
		assertEquals(1, closedCollapsed.size());
		assertFalse(closedCollapsed.get(0)); // trip 1 had spending, kept
		assertEquals(1, s.getTrips().size());
	}

	@Test
	public void mustReArmEvenAfterTravelling()
	{
		Session s = start(3200, 3200);
		spend(s);
		tm.updatePosition(3300, 3200); // travelled far
		tick += 1;                     // ...but only 1 tick since the last trigger
		assertFalse(bank(3300, 3200));
		tick += TripManager.REARM_TICKS;
		assertTrue(bank(3300, 3200));
	}

	@Test
	public void teleportStyleJumpThenBankCutsATrip()
	{
		Session s = start(3200, 3200);
		spend(s);
		tick += 15;
		tm.updatePosition(2440, 3090); // a teleport away
		assertTrue(bank(2440, 3090));
		assertEquals(2, s.getCurrentTrip().getId());
	}

	@Test
	public void emptyTripIsCollapsed()
	{
		Session s = start(3200, 3200);
		tick += 20;
		tm.updatePosition(3300, 3200);
		bank(3200, 3200);
		assertEquals(1, closedCollapsed.size());
		assertTrue(closedCollapsed.get(0));
		assertEquals(0, s.getTrips().size());
		assertEquals(1, s.getCollapsedEmptyTrips());
	}

	@Test
	public void stopClosesOpenTrip()
	{
		Session s = start(3200, 3200);
		spend(s);
		Session finished = tm.stop(Instant.now());
		assertFalse(tm.isActive());
		assertEquals(1, finished.getTrips().size());
		assertNotNull(finished.getEndTime());
	}
}
