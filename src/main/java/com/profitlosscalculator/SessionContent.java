/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The "Session" mode. A flat "record my profit / loss for this stretch of time" run - Net,
 * gp/hr and two item grids (green picked up, red consumed), plus optional income / cost lists
 * and any unresolved deaths.
 */
class SessionContent extends JPanel
{
	private final ProfitLossCalculatorPanel.Controls controls;
	private final ItemManager itemManager;

	private final JButton primaryBtn = new JButton("Start session");
	private final JButton stopBtn = new JButton("Stop");
	private final JButton restartBtn = new JButton("Restart");
	private final JPanel secondaryRow = new JPanel(new GridLayout(1, 2, 4, 0));

	private final JLabel titleLabel = new JLabel();
	private final JLabel killsLabel = new JLabel();
	private final JLabel noSessionLabel = new JLabel("No session");
	private final JPanel statGrid = new JPanel(new GridLayout(0, 2, 8, 1));
	private final JPanel gainGrid = new JPanel(new GridLayout(0, PanelUi.GRID_COLS, 2, 2));
	private final JPanel lossGrid = new JPanel(new GridLayout(0, PanelUi.GRID_COLS, 2, 2));

	private final JPanel incomeSection = new JPanel();
	private final JPanel incomePanel = new JPanel();
	private final JPanel costSection = new JPanel();
	private final JPanel costPanel = new JPanel();
	private final JPanel deathsSection = new JPanel();
	private final JPanel deathsPanel = new JPanel();

	SessionContent(ProfitLossCalculatorPanel.Controls controls, ItemManager itemManager)
	{
		this.controls = controls;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

		primaryBtn.setFocusPainted(false);
		primaryBtn.addActionListener(e -> controls.onStartPauseResume());
		stopBtn.setFocusPainted(false);
		restartBtn.setFocusPainted(false);
		stopBtn.addActionListener(e -> controls.onStop());
		restartBtn.addActionListener(e -> controls.onRestart());
		secondaryRow.add(stopBtn);
		secondaryRow.add(restartBtn);

		final JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.add(PanelUi.stretch(primaryBtn));
		north.add(PanelUi.vgap(4));
		north.add(PanelUi.stretch(secondaryRow));

		final JPanel summary = new JPanel();
		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summary.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 0, 8, 0)));

		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		killsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		killsLabel.setFont(FontManager.getRunescapeSmallFont());
		final JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(titleLabel, BorderLayout.WEST);
		titleRow.add(killsLabel, BorderLayout.EAST);

		noSessionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noSessionLabel.setFont(FontManager.getRunescapeSmallFont());

		statGrid.setOpaque(false);
		statGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

		summary.add(PanelUi.stretch(titleRow));
		summary.add(PanelUi.vgap(4));
		summary.add(statGrid);
		summary.add(PanelUi.stretch(noSessionLabel));
		summary.add(PanelUi.vgap(6));
		summary.add(gainGrid);
		summary.add(PanelUi.vgap(3));
		summary.add(lossGrid);

		final JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.add(summary);
		center.add(PanelUi.vgap(8));

		incomePanel.setLayout(new BoxLayout(incomePanel, BoxLayout.Y_AXIS));
		section(incomeSection, "Income", incomePanel);
		costPanel.setLayout(new BoxLayout(costPanel, BoxLayout.Y_AXIS));
		section(costSection, "Costs", costPanel);
		deathsPanel.setLayout(new BoxLayout(deathsPanel, BoxLayout.Y_AXIS));
		section(deathsSection, "Deaths", deathsPanel);

		center.add(incomeSection);
		center.add(costSection);
		center.add(deathsSection);

		final JPanel currentStack = new JPanel();
		currentStack.setLayout(new BoxLayout(currentStack, BoxLayout.Y_AXIS));
		currentStack.add(north);
		currentStack.add(PanelUi.vgap(6));
		currentStack.add(center);

		add(currentStack, BorderLayout.NORTH);
		render(ProfitLossCalculatorPanel.View.builder().build());
	}

	private static void section(JPanel holder, String title, JPanel body)
	{
		holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.add(PanelUi.sectionLabel(title));
		holder.add(body);
		holder.add(PanelUi.vgap(10));
	}

	void render(ProfitLossCalculatorPanel.View view)
	{
		final boolean otherModeActive = view.isGrouped() && view.isActive();
		final boolean sessionRunning = !view.isGrouped() && view.isActive();
		final boolean sessionShowing = !view.isGrouped() && (view.isActive() || view.isFinished());

		primaryBtn.setEnabled(!otherModeActive);
		primaryBtn.setText(otherModeActive ? "Another run is active"
			: !sessionRunning ? "Start session"
			: view.isPaused() ? "Resume" : "Pause");
		primaryBtn.setBackground(otherModeActive ? ColorScheme.MEDIUM_GRAY_COLOR
			: !sessionRunning || view.isPaused() ? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_INPROGRESS_COLOR);
		secondaryRow.setVisible(sessionRunning);

		titleLabel.setText("Session" + (sessionShowing && !view.getState().isEmpty() ? "  ·  " + view.getState() : ""));
		killsLabel.setText(sessionShowing && view.getKills() > 0
			? view.getKills() + " kill" + (view.getKills() == 1 ? "" : "s")
			: "");

		statGrid.removeAll();
		statGrid.setVisible(sessionShowing);
		noSessionLabel.setVisible(!sessionShowing);
		noSessionLabel.setText(otherModeActive
			? (view.isTargeted() ? "A Boss Target Farm is running - see the Boss Target Farm tab"
				: "A Slayer task is running - see the Slayer tab")
			: "No session");

		if (sessionShowing)
		{
			statGrid.add(PanelUi.statCell("Net", PanelUi.sign(view.getNet()),
				view.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR, true));
			statGrid.add(PanelUi.statCell("", view.getNetPerHour() != 0
				? PanelUi.gpPlain(view.getNetPerHour()) + "/hr" : "", ColorScheme.LIGHT_GRAY_COLOR, false));
			statGrid.add(PanelUi.statCell("Gains", "+" + PanelUi.gpPlain(view.getGains()), PanelUi.GAIN_COLOR, false));
			statGrid.add(PanelUi.statCell("Losses", "-" + PanelUi.gpPlain(view.getLosses()), PanelUi.LOSS_COLOR, false));
			if (view.getAtRisk() > 0)
			{
				statGrid.add(PanelUi.statCell("At risk", PanelUi.gpPlain(view.getAtRisk()), PanelUi.DEATH_COLOR, false));
				statGrid.add(new JLabel());
			}
		}
		statGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, statGrid.getPreferredSize().height));

		if (sessionShowing)
		{
			PanelUi.fillGrid(gainGrid, view.getGainItems(), PanelUi.GAIN_CELL, itemManager);
			PanelUi.fillGrid(lossGrid, view.getLossItems(), PanelUi.LOSS_CELL, itemManager);
		}
		else
		{
			gainGrid.setVisible(false);
			lossGrid.setVisible(false);
		}

		fillLines(incomeSection, incomePanel, sessionShowing && view.isShowIncomeList(), view.getIncomeEvents());
		fillLines(costSection, costPanel, sessionShowing && view.isShowCostList(), view.getCostEvents());

		deathsSection.setVisible(sessionShowing && !view.getDeaths().isEmpty());
		deathsPanel.removeAll();
		if (sessionShowing)
		{
			for (ProfitLossCalculatorPanel.DeathRow d : view.getDeaths())
			{
				deathsPanel.add(PanelUi.deathRow(d, controls));
				deathsPanel.add(PanelUi.vgap(4));
			}
		}

		revalidate();
		repaint();
	}

	private void fillLines(JPanel holder, JPanel body, boolean show, List<ProfitLossCalculatorPanel.EventLine> lines)
	{
		holder.setVisible(show && !lines.isEmpty());
		body.removeAll();
		for (ProfitLossCalculatorPanel.EventLine line : lines)
		{
			body.add(PanelUi.eventLine(line));
		}
	}
}
