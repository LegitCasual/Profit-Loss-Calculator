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
import javax.swing.JTextField;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The "Targeted" tab. Type one mob name, hit Start, and every kill of that mob is tracked
 * individually - loot in, and every cost incurred while the farm runs charged against it.
 * The summary shows the farm net and the per-kill average. Loot from anything else killed
 * during the farm shows under "Other income" and is not part of the net.
 */
class TargetedContent extends JPanel
{
	private final ProfitLossCalculatorPanel.Controls controls;
	private final ItemManager itemManager;

	private final JTextField mobField = new JTextField();
	private final JButton startBtn = new JButton("Start farm");
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

	TargetedContent(ProfitLossCalculatorPanel.Controls controls, ItemManager itemManager)
	{
		this.controls = controls;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		final JPanel search = new JPanel(new BorderLayout(4, 0));
		search.setAlignmentX(LEFT_ALIGNMENT);
		mobField.setToolTipText("Exact NPC name, e.g. Brutus");
		mobField.addActionListener(e -> controls.onStartFarm(mobField.getText()));
		startBtn.setFocusPainted(false);
		startBtn.addActionListener(e -> controls.onStartFarm(mobField.getText()));
		search.add(mobField, BorderLayout.CENTER);
		search.add(startBtn, BorderLayout.EAST);

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

		content.add(PanelUi.stretch(search));
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
		final boolean farm = view.isTargeted() && (view.isActive() || view.isFinished());
		final boolean sessionRunning = !view.isTargeted() && view.isActive();

		startBtn.setEnabled(!view.isActive());
		mobField.setEnabled(!view.isActive());
		if (farm && !mobField.isFocusOwner())
		{
			mobField.setText(view.getTargetMob());
		}
		runButtons.setVisible(farm && view.isActive());
		pauseBtn.setText(view.isPaused() ? "Resume" : "Pause");

		hintLabel.setText(sessionRunning
			? "A session is running - stop it to start a farm"
			: farm ? "" : "Type a mob name and Start to track its profit per kill");
		hintLabel.setVisible(!hintLabel.getText().isEmpty());

		titleLabel.setText(farm
			? view.getTargetMob() + (view.getState().isEmpty() ? "" : "  ·  " + view.getState())
			: "");
		killsLabel.setText(farm && view.getKills() > 0
			? view.getKills() + " kill" + (view.getKills() == 1 ? "" : "s")
			: "");

		statGrid.removeAll();
		statGrid.setVisible(farm);
		if (farm)
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

		if (farm)
		{
			PanelUi.fillGrid(gainGrid, view.getGainItems(), PanelUi.GAIN_CELL, itemManager);
			PanelUi.fillGrid(lossGrid, view.getLossItems(), PanelUi.LOSS_CELL, itemManager);
		}
		else
		{
			gainGrid.setVisible(false);
			lossGrid.setVisible(false);
		}

		killSection.setVisible(farm && !view.getKillRows().isEmpty());
		killPanel.removeAll();
		for (ProfitLossCalculatorPanel.KillRow k : view.getKillRows())
		{
			killPanel.add(PanelUi.killRow(k));
			killPanel.add(PanelUi.vgap(2));
		}

		fillLines(otherSection, otherPanel, farm, view.getIncomeEvents());
		fillLines(costSection, costPanel, farm && view.isShowCostList(), view.getCostEvents());

		deathsSection.setVisible(farm && !view.getDeaths().isEmpty());
		deathsPanel.removeAll();
		if (farm)
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
