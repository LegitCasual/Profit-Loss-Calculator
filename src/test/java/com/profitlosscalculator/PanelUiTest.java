/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class PanelUiTest
{
	private static JPanel netCells(ProfitLossCalculatorPanel.View view)
	{
		final JPanel grid = new JPanel(new GridLayout(0, 2));
		PanelUi.addNetCells(grid, view, new JLabel());
		return grid;
	}

	private static JLabel cell(JPanel grid, String label)
	{
		for (Component c : grid.getComponents())
		{
			if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(label))
			{
				return (JLabel) c;
			}
		}
		return null;
	}

	@Test
	public void potentialNetExplainsItselfBeforeAnythingIsCashedOut()
	{
		final JPanel grid = netCells(ProfitLossCalculatorPanel.View.builder().net(-531).build());
		final JLabel potential = cell(grid, "Potential net");
		assertNotNull(potential);
		assertEquals(PanelUi.POTENTIAL_NET_TIP, potential.getToolTipText());
		assertNull(cell(grid, "Actual net"));
	}

	@Test
	public void potentialNetExplainsItselfUnderActualNetAfterAnAlch()
	{
		// an alch-only end-of-grind run: potential reads as a loss, actual doesn't
		final JPanel grid = netCells(ProfitLossCalculatorPanel.View.builder()
			.net(-2_016).realisedSoFar(10_812).actualNet(8_418).build());
		assertNotNull(cell(grid, "Actual net"));
		assertEquals(PanelUi.POTENTIAL_NET_TIP, cell(grid, "Potential net").getToolTipText());
	}
}
