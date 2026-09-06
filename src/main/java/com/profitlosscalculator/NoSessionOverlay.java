/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * A small red "you haven't started tracking" nudge pinned to the top-right. Shown only while
 * the {@code showNoSessionWarning} config is on, no run is active, and the player is out in
 * the world - it stays hidden at a bank / deposit box and inside a player-owned house, both
 * ordinary spots to be standing idle before a trip.
 *
 * <p>The plugin adds and removes this overlay from the
 * {@link net.runelite.client.ui.overlay.OverlayManager} as the config toggles (see
 * {@code ProfitLossCalculatorPlugin.onConfigChanged}), so switching it off takes it off the
 * screen straight away rather than just skipping the draw.
 */
class NoSessionOverlay extends OverlayPanel
{
	private static final Color TEXT = new Color(255, 90, 90);
	private static final Color BACKGROUND = new Color(70, 20, 20, 170);

	private final ProfitLossCalculatorPlugin plugin;
	private final ProfitLossCalculatorConfig config;

	@Inject
	NoSessionOverlay(ProfitLossCalculatorPlugin plugin, ProfitLossCalculatorConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_RIGHT);
		panelComponent.setBackgroundColor(BACKGROUND);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showNoSessionWarning() || !plugin.shouldWarnNoSession())
		{
			return null;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("No profit loss session started")
			.leftColor(TEXT)
			.build());
		return super.render(graphics);
	}
}
