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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The "Slayer" tab. Auto-detects the player's current Slayer task (name, location, progress -
 * see {@link SlayerTaskTracker}) and, once started, tracks a clean per-kill ledger for every
 * mob that has matched the task. Unlike Targeted farm's single mob, a Slayer run can involve
 * several species (and, since the run isn't stopped when the task changes, can span more than
 * one task assignment) - so its per-kill list carries a mob name per row. Loot from anything
 * that never matched the task shows under "Other income" and is not part of the net, same as
 * Targeted's stray kills.
 */
class SlayerContent extends JPanel
{
	private final ProfitLossCalculatorPanel.Controls controls;
	private final ItemManager itemManager;

	private final JLabel taskLabel = new JLabel();
	private final JButton startBtn = new JButton("Start tracking");
	private final JButton pauseBtn = new JButton("Pause");
	private final JButton stopBtn = new JButton("Stop");
	private final JButton restartBtn = new JButton("Restart");
	private final JPanel runButtons = new JPanel(new GridLayout(1, 3, 4, 0));

	private final JLabel titleLabel = new JLabel();
	private final JLabel killsLabel = new JLabel();
	private final JLabel hintLabel = new JLabel();
	private final JPanel statGrid = new JPanel(new GridLayout(0, 2, 8, 1));
	private final JPanel gainGrid = new JPanel(new GridLayout(0, PanelUi.GRID_COLS, 2, 2));
	private final JPanel lossGrid = new JPanel(new GridLayout(0, PanelUi.GRID_COLS, 2, 2));

	private final JPanel killSection = new JPanel();
	private final JPanel killPanel = new JPanel();
	private final JPanel otherSection = new JPanel();
	private final JPanel otherPanel = new JPanel();
	private final JPanel costSection = new JPanel();
	private final JPanel costPanel = new JPanel();
	private final JPanel deathsSection = new JPanel();
	private final JPanel deathsPanel = new JPanel();

	SlayerContent(ProfitLossCalculatorPanel.Controls controls, ItemManager itemManager)
	{
		this.controls = controls;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		taskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		taskLabel.setFont(FontManager.getRunescapeSmallFont());
		taskLabel.setAlignmentX(LEFT_ALIGNMENT);

		startBtn.setFocusPainted(false);
		startBtn.addActionListener(e -> controls.onStartSlayer());

		pauseBtn.setFocusPainted(false);
		stopBtn.setFocusPainted(false);
		restartBtn.setFocusPainted(false);
		pauseBtn.addActionListener(e -> controls.onStartPauseResume());
		stopBtn.addActionListener(e -> controls.onStop());
		restartBtn.addActionListener(e -> controls.onRestart());
		runButtons.add(pauseBtn);
		runButtons.add(stopBtn);
		runButtons.add(restartBtn);
		PanelUi.stretch(runButtons);

		hintLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hintLabel.setFont(FontManager.getRunescapeSmallFont());
		hintLabel.setAlignmentX(LEFT_ALIGNMENT);

		content.add(PanelUi.stretch(taskLabel));
		content.add(PanelUi.vgap(4));
		content.add(PanelUi.stretch(startBtn));
		content.add(PanelUi.vgap(4));
		content.add(runButtons);
		content.add(PanelUi.vgap(4));
		content.add(hintLabel);
		content.add(PanelUi.vgap(4));

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

		statGrid.setOpaque(false);
		statGrid.setAlignmentX(LEFT_ALIGNMENT);

		summary.add(PanelUi.stretch(titleRow));
		summary.add(PanelUi.vgap(4));
		summary.add(statGrid);
		summary.add(PanelUi.vgap(6));
		summary.add(gainGrid);
		summary.add(PanelUi.vgap(3));
		summary.add(lossGrid);
		content.add(summary);
		content.add(PanelUi.vgap(8));

		killPanel.setLayout(new BoxLayout(killPanel, BoxLayout.Y_AXIS));
		section(killSection, "Per kill", killPanel);
		otherPanel.setLayout(new BoxLayout(otherPanel, BoxLayout.Y_AXIS));
		section(otherSection, "Other income", otherPanel);
		costPanel.setLayout(new BoxLayout(costPanel, BoxLayout.Y_AXIS));
		section(costSection, "Costs", costPanel);
		deathsPanel.setLayout(new BoxLayout(deathsPanel, BoxLayout.Y_AXIS));
		section(deathsSection, "Deaths", deathsPanel);
		content.add(killSection);
		content.add(otherSection);
		content.add(costSection);
		content.add(deathsSection);

		add(content, BorderLayout.NORTH);
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
		final boolean slayer = view.isSlayer() && (view.isActive() || view.isFinished());
		final boolean otherModeActive = !view.isSlayer() && view.isActive();
		final boolean hasTask = !view.getSlayerTaskName().isEmpty();

		startBtn.setEnabled(!view.isActive() && hasTask);
		taskLabel.setText(taskPreview(view));

		runButtons.setVisible(slayer && view.isActive());
		pauseBtn.setText(view.isPaused() ? "Resume" : "Pause");

		hintLabel.setText(otherModeActive
			? (view.isTargeted() ? "A Boss Target Farm is running - see the Boss Target Farm tab"
				: "A session is running - stop it to start Slayer tracking")
			: slayer || hasTask ? ""
			: "No Slayer task detected - get one from a Slayer Master");
		hintLabel.setVisible(!hintLabel.getText().isEmpty());

		titleLabel.setText(slayer ? "Slayer" + (view.getState().isEmpty() ? "" : "  ·  " + view.getState()) : "");
		killsLabel.setText(slayer && view.getKills() > 0
			? view.getKills() + " kill" + (view.getKills() == 1 ? "" : "s")
			: "");

		statGrid.removeAll();
		statGrid.setVisible(slayer);
		if (slayer)
		{
			statGrid.add(PanelUi.statCell("Net", PanelUi.sign(view.getNet()),
				view.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR, true));
			statGrid.add(PanelUi.statCell("", view.getKills() > 0
				? PanelUi.sign(view.getGpPerKill()) + "/kill" : "", ColorScheme.LIGHT_GRAY_COLOR, false));
			statGrid.add(PanelUi.statCell("Gains", "+" + PanelUi.gpPlain(view.getGains()), PanelUi.GAIN_COLOR, false));
			statGrid.add(PanelUi.statCell("Losses", "-" + PanelUi.gpPlain(view.getLosses()), PanelUi.LOSS_COLOR, false));
			statGrid.add(PanelUi.statCell("Rate", view.getNetPerHour() != 0
				? PanelUi.gpPlain(view.getNetPerHour()) + "/hr" : "-", ColorScheme.LIGHT_GRAY_COLOR, false));
			statGrid.add(PanelUi.statCell("Kill time", view.getSecPerKill() > 0
				? "~" + PanelUi.secs(view.getSecPerKill()) : "-", ColorScheme.LIGHT_GRAY_COLOR, false));
			if (view.getAtRisk() > 0)
			{
				statGrid.add(PanelUi.statCell("At risk", PanelUi.gpPlain(view.getAtRisk()), PanelUi.DEATH_COLOR, false));
				statGrid.add(new JLabel());
			}
		}
		statGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, statGrid.getPreferredSize().height));

		if (slayer)
		{
			PanelUi.fillGrid(gainGrid, view.getGainItems(), PanelUi.GAIN_CELL, itemManager);
			PanelUi.fillGrid(lossGrid, view.getLossItems(), PanelUi.LOSS_CELL, itemManager);
		}
		else
		{
			gainGrid.setVisible(false);
			lossGrid.setVisible(false);
		}

		killSection.setVisible(slayer && !view.getKillRows().isEmpty());
		killPanel.removeAll();
		for (ProfitLossCalculatorPanel.KillRow k : view.getKillRows())
		{
			killPanel.add(PanelUi.killRow(k));
			killPanel.add(PanelUi.vgap(2));
		}

		fillLines(otherSection, otherPanel, slayer, view.getIncomeEvents());
		fillLines(costSection, costPanel, slayer && view.isShowCostList(), view.getCostEvents());

		deathsSection.setVisible(slayer && !view.getDeaths().isEmpty());
		deathsPanel.removeAll();
		if (slayer)
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

	/** "Aberrant spectres  ·  Catacombs of Kourend  ·  42 left", degrading gracefully as
	 *  location/progress data is missing, or "No Slayer task detected" with none assigned. */
	private static String taskPreview(ProfitLossCalculatorPanel.View view)
	{
		if (view.getSlayerTaskName().isEmpty())
		{
			return "No Slayer task detected";
		}
		final StringBuilder sb = new StringBuilder(view.getSlayerTaskName());
		if (!view.getSlayerTaskLocation().isEmpty())
		{
			sb.append("  ·  ").append(view.getSlayerTaskLocation());
		}
		if (view.getSlayerInitialAmount() > 0)
		{
			sb.append("  ·  ").append(view.getSlayerRemainingAmount()).append(" left");
		}
		return sb.toString();
	}

	private void fillLines(JPanel holder, JPanel body, boolean show, java.util.List<ProfitLossCalculatorPanel.EventLine> lines)
	{
		holder.setVisible(show && !lines.isEmpty());
		body.removeAll();
		for (ProfitLossCalculatorPanel.EventLine line : lines)
		{
			body.add(PanelUi.eventLine(line));
		}
	}
}
