/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

/**
 * Compact in-game pill showing the running session net (and net/hr once there's a rate),
 * tinted green or red by sign. Hovering it pops the full breakdown - actual / potential net,
 * income, cost, kills, at-risk - beside it, so the screen stays uncluttered.
 * Sits at the middle of the game view's right edge by default, and can be dragged anywhere.
 * Both the default spot and the breakdown steer clear of the minimap, side panel and chatbox
 * in the resizable layouts (fixed mode's viewport already excludes them).
 * Only rendered while a session is active and {@code showOverlay} is enabled.
 */
class ProfitLossCalculatorOverlay extends Overlay
{
	private static final int PAD_X = 5;
	private static final int PAD_Y = 3;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 3;
	/** Gap between the badge and the right edge of the game view in its default spot. */
	private static final int EDGE_MARGIN = 10;
	/** Gap between the badge and its hover breakdown. */
	private static final int DETAILS_GAP = 3;
	private static final int DETAILS_WIDTH = 170;
	/** Breakdown height guess for the first hovered frame, before the panel has been measured. */
	private static final int DETAILS_HEIGHT_GUESS = 110;

	/** Resizable-layout interface parts the badge and its breakdown shouldn't cover. Missing or
	 *  hidden ones (e.g. the other layout's) are skipped each frame. */
	private static final int[] AVOID_WIDGETS = {
		InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER,
		InterfaceID.ToplevelOsrsStretch.SIDE_CONTAINER,
		InterfaceID.ToplevelOsrsStretch.SIDE_TOP,
		InterfaceID.ToplevelOsrsStretch.SIDE_BOTTOM,
		InterfaceID.ToplevelOsrsStretch.SIDE_BACKGROUND,
		InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER,
		InterfaceID.ToplevelPreEoc.MAP_CONTAINER,
		InterfaceID.ToplevelPreEoc.SIDE_CONTAINER,
		InterfaceID.ToplevelPreEoc.SIDE_STATIC,
		InterfaceID.ToplevelPreEoc.SIDE_MOVABLE,
		InterfaceID.ToplevelPreEoc.SIDE_BACKGROUND,
		InterfaceID.ToplevelPreEoc.CHAT_CONTAINER,
	};

	private static final Color PROFIT_BG = new Color(28, 80, 28, 200);
	private static final Color PROFIT_BORDER = new Color(70, 150, 70, 220);
	private static final Color LOSS_BG = new Color(90, 25, 25, 200);
	private static final Color LOSS_BORDER = new Color(170, 60, 60, 220);
	private static final Color PAUSED_BG = new Color(55, 55, 55, 200);
	private static final Color PAUSED_BORDER = new Color(120, 120, 120, 220);
	private static final Color RATE_TEXT = new Color(210, 210, 210);
	/** Hover breakdown background: mid grey at 50% so the game shows through but text stays legible. */
	private static final Color DETAILS_BG = new Color(60, 60, 60, 128);

	private final ProfitLossCalculatorPlugin plugin;
	private final ProfitLossCalculatorConfig config;
	private final Client client;
	private final ItemManager itemManager;

	private final PanelComponent detailsPanel = new PanelComponent();

	@Inject
	ProfitLossCalculatorOverlay(ProfitLossCalculatorPlugin plugin, ProfitLossCalculatorConfig config,
		Client client, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.itemManager = itemManager;
		// No snap corner exists for middle-right, so the badge places itself there (see
		// render) until the user drags it; a drag saves a preferred location that then wins, and
		// Alt+right-click "Reset" clears it back to the default spot.
		setPosition(OverlayPosition.DYNAMIC);
		setMovable(true);
		// Above widgets so the hover breakdown isn't drawn underneath interface panels it has to
		// sit next to; placement keeps both it and the badge off them.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		detailsPanel.setPreferredSize(new Dimension(DETAILS_WIDTH, 0));
		detailsPanel.setBackgroundColor(DETAILS_BG);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.isSessionActive())
		{
			return null;
		}

		final ProfitLossCalculatorPanel.View v = plugin.currentView();
		final boolean profit = v.getNet() >= 0;

		final String netText = signed(v.getNet());
		final String rateText = v.getNetPerHour() != 0 ? "  " + signed(v.getNetPerHour()) + "/hr" : "";

		final FontMetrics fm = graphics.getFontMetrics();
		final int textW = fm.stringWidth(netText) + fm.stringWidth(rateText);
		final int width = PAD_X + ICON_SIZE + ICON_GAP + textW + PAD_X;
		final int height = Math.max(ICON_SIZE, fm.getHeight()) + PAD_Y * 2;

		final Color bg = v.isPaused() ? PAUSED_BG : profit ? PROFIT_BG : LOSS_BG;
		final Color border = v.isPaused() ? PAUSED_BORDER : profit ? PROFIT_BORDER : LOSS_BORDER;
		graphics.setColor(bg);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(border);
		graphics.drawRect(0, 0, width - 1, height - 1);

		final BufferedImage coins = itemManager.getImage(ItemID.COINS, 10000, false);
		if (coins != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(coins, PAD_X, (height - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE, null);
		}

		final int textX = PAD_X + ICON_SIZE + ICON_GAP;
		final int baseline = (height - fm.getHeight()) / 2 + fm.getAscent();
		drawShadowed(graphics, netText, textX, baseline, v.isPaused() ? Color.WHITE
			: profit ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		drawShadowed(graphics, rateText, textX + fm.stringWidth(netText), baseline, RATE_TEXT);

		// Where the badge is on the canvas this frame (the renderer set our bounds' location).
		final Rectangle badge = new Rectangle(getBounds().x, getBounds().y, width, height);
		final List<Rectangle> avoid = avoidAreas();

		final Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && !client.isMenuOpen() && badge.contains(mouse.getX(), mouse.getY()))
		{
			fillDetails(v);
			final Rectangle spot = detailsSpot(badge, avoid);
			// Graphics is translated to the badge, so place the panel relative to it.
			detailsPanel.setPreferredLocation(new java.awt.Point(spot.x - badge.x, spot.y - badge.y));
			detailsPanel.render(graphics);
		}

		if (getPreferredLocation() == null)
		{
			// Takes effect next frame: the renderer draws a DYNAMIC overlay at its bounds' location.
			getBounds().setLocation(defaultSpot(width, height, avoid));
		}

		return new Dimension(width, height);
	}

	/**
	 * Middle of the game view's right edge - pulled left of any interface part (side panel,
	 * minimap) that reaches into that row, so a short window doesn't put it on the inventory.
	 */
	private java.awt.Point defaultSpot(int width, int height, List<Rectangle> avoid)
	{
		final int y = client.getViewportYOffset() + (client.getViewportHeight() - height) / 2;
		int right = client.getViewportXOffset() + client.getViewportWidth() - EDGE_MARGIN;
		for (Rectangle r : avoid)
		{
			if (r.y < y + height && r.y + r.height > y)
			{
				right = Math.min(right, r.x - EDGE_MARGIN);
			}
		}
		return new java.awt.Point(Math.max(0, right - width), y);
	}

	/**
	 * Picks where the breakdown goes: below, above, then left / right of the badge
	 * (right-aligned first so it doesn't run off the right edge), taking the first spot that's
	 * on screen and clear of the interface. Falls back to below, clamped on screen.
	 */
	private Rectangle detailsSpot(Rectangle badge, List<Rectangle> avoid)
	{
		final Rectangle measured = detailsPanel.getBounds();
		final int w = measured.width > 0 ? measured.width : DETAILS_WIDTH;
		final int h = measured.height > 0 ? measured.height : DETAILS_HEIGHT_GUESS;

		final int rightAligned = badge.x + badge.width - w;
		final int below = badge.y + badge.height + DETAILS_GAP;
		final int above = badge.y - h - DETAILS_GAP;
		final int leftOf = badge.x - w - DETAILS_GAP;
		final int rightOf = badge.x + badge.width + DETAILS_GAP;
		final Rectangle[] candidates = {
			new Rectangle(rightAligned, below, w, h),
			new Rectangle(badge.x, below, w, h),
			new Rectangle(rightAligned, above, w, h),
			new Rectangle(badge.x, above, w, h),
			new Rectangle(leftOf, badge.y, w, h),
			new Rectangle(leftOf, badge.y + badge.height - h, w, h),
			new Rectangle(rightOf, badge.y, w, h),
			new Rectangle(rightOf, badge.y + badge.height - h, w, h),
		};

		final Rectangle canvas = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		for (Rectangle c : candidates)
		{
			if (canvas.contains(c) && avoid.stream().noneMatch(c::intersects))
			{
				return c;
			}
		}

		final Rectangle fallback = candidates[0];
		fallback.x = Math.max(0, Math.min(fallback.x, canvas.width - w));
		fallback.y = Math.max(0, Math.min(fallback.y, canvas.height - h));
		return fallback;
	}

	/** Canvas bounds of the visible interface parts listed in {@link #AVOID_WIDGETS}. */
	private List<Rectangle> avoidAreas()
	{
		final List<Rectangle> areas = new ArrayList<>();
		final long canvasArea = (long) client.getCanvasWidth() * client.getCanvasHeight();
		for (int id : AVOID_WIDGETS)
		{
			final Widget w = client.getWidget(id);
			if (w == null || w.isHidden())
			{
				continue;
			}
			final Rectangle r = w.getBounds();
			// Skip empty ones, and any container that turns out to span most of the screen -
			// avoiding that would leave nowhere to go.
			if (r == null || r.isEmpty() || (long) r.width * r.height > canvasArea / 2)
			{
				continue;
			}
			areas.add(r);
		}
		return areas;
	}

	/**
	 * Full breakdown shown on hover - what the old always-on panel used to display. The panel is
	 * reused and refilled each frame because {@link PanelComponent} sizes its background from the
	 * previous frame's layout; a fresh one every frame would only ever paint a zero-size box.
	 */
	private void fillDetails(ProfitLossCalculatorPanel.View v)
	{
		final PanelComponent panel = detailsPanel;
		panel.getChildren().clear();
		final Color netColor = v.getNet() >= 0
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_ERROR_COLOR;

		panel.getChildren().add(TitleComponent.builder()
			.text(v.getTitle() + (v.isPaused() ? " (paused)" : ""))
			.build());
		if (v.getRealisedSoFar() > 0 || v.getBankedSoFar() > 0)
		{
			panel.getChildren().add(line("Actual net", gp(v.getActualNet()), v.getActualNet() >= 0
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR));
		}
		panel.getChildren().add(line("Potential net", gp(v.getNet()), netColor));
		if (v.getNetPerHour() != 0)
		{
			panel.getChildren().add(line("Net / hr", gp(v.getNetPerHour()), netColor));
		}
		panel.getChildren().add(line("Potential", gp(v.getGains()), Color.WHITE));
		if (v.getPotential() != v.getGains())
		{
			panel.getChildren().add(line("Dropped", gp(v.getPotential()), Color.WHITE));
		}
		panel.getChildren().add(line("Spent / lost", gp(v.getLosses()), Color.WHITE));
		if (v.getKills() > 0)
		{
			panel.getChildren().add(line("Kills", Integer.toString(v.getKills()), Color.WHITE));
			if (v.isGrouped())
			{
				panel.getChildren().add(line("Net / kill", gp(v.getGpPerKill()), netColor));
			}
		}
		if (v.getAtRisk() > 0)
		{
			panel.getChildren().add(line("At risk", gp(v.getAtRisk()), Color.WHITE));
		}
	}

	private static LineComponent line(String left, String right, Color rightColor)
	{
		return LineComponent.builder().left(left).right(right).rightColor(rightColor).build();
	}

	private static void drawShadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		if (text.isEmpty())
		{
			return;
		}
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/** Short stack-size form ("225K", "-1.2M") with an explicit + on gains. */
	private static String signed(long v)
	{
		final String s = QuantityFormatter.quantityToStackSize(Math.abs(v));
		return (v < 0 ? "-" : "+") + s;
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}
}
