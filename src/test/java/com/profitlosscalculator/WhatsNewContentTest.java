/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WhatsNewContentTest
{
	@Test
	public void showsToSomeoneWhoNeverConfirmed()
	{
		assertTrue(WhatsNewContent.isUnseen(""));
		assertTrue(WhatsNewContent.isUnseen(null));
	}

	@Test
	public void hiddenOnceThisReleaseIsConfirmed()
	{
		assertFalse(WhatsNewContent.isUnseen(WhatsNewContent.VERSION));
	}

	@Test
	public void anyOtherConfirmedReleaseShowsIt()
	{
		// an older or newer version, or a suffixed one - only an exact match hides it
		assertTrue(WhatsNewContent.isUnseen("0.1"));
		assertTrue(WhatsNewContent.isUnseen("9.9"));
		assertTrue(WhatsNewContent.isUnseen(WhatsNewContent.VERSION + "b"));
	}
}
