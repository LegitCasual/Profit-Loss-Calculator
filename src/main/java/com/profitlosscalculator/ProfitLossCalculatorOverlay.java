/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

/**
 * Optional in-game panel showing the running session net, income, cost and kill tally.
 * Only rendered while a session is active and {@code showOverlay} is enabled.
 */
class ProfitLossCalculatorOverlay extends OverlayPanel
{
	private final ProfitLossCalculatorPlugin plugin;
	private final ProfitLossCalculatorConfig config;

	@Inject
	ProfitLossCalculatorOverlay(ProfitLossCalculatorPlugin plugin, ProfitLossCalculatorConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.isSessionActive())
		{
			return null;
		}

		final ProfitLossCalculatorPanel.View v = plugin.currentView();
		final Color netColor = v.getNet() >= 0
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_ERROR_COLOR;

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(v.getTitle() + (v.isPaused() ? " (paused)" : ""))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Net")
			.right(gp(v.getNet()))
			.rightColor(netColor)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Picked up")
			.right(gp(v.getGains()))
			.build());
		if (v.getPotential() != v.getGains())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Dropped")
				.right(gp(v.getPotential()))
				.build());
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Spent / lost")
			.right(gp(v.getLosses()))
			.build());
		if (v.getKills() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Kills")
				.right(Integer.toString(v.getKills()))
				.build());
			if (v.isTargeted())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Net / kill")
					.right(gp(v.getGpPerKill()))
					.rightColor(netColor)
					.build());
			}
		}
		if (v.getAtRisk() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("At risk")
				.right(gp(v.getAtRisk()))
				.build());
		}

		return super.render(graphics);
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}
}
