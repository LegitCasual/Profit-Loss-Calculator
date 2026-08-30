/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

/**
 * Optional in-game panel showing the running trip cost and session total. Only rendered
 * while a session is active and {@code showOverlay} is enabled.
 */
class SessionCostTrackerOverlay extends OverlayPanel
{
	private final SessionCostTrackerPlugin plugin;
	private final SessionCostTrackerConfig config;

	@Inject
	SessionCostTrackerOverlay(SessionCostTrackerPlugin plugin, SessionCostTrackerConfig config)
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

		final SessionCostTrackerPanel.View v = plugin.currentView();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Session cost")
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Trip #" + v.getCurrentTripId())
			.right(gp(v.getCurrentTripCost()))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Session")
			.right(gp(v.getSessionTotal()))
			.build());
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
