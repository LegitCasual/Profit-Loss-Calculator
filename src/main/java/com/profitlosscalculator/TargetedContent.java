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
 * The "Target Farm" tab. Type a mob name, hit Start, and every kill of it is tracked
 * individually - loot in, and every cost incurred while the farm runs charged against it. More
 * mobs can be added to the same farm at any time (the same field relabels to "Add mob" once
 * running) - each gets its own block with its own net and gain icon grid, stacked under a
 * combined farm total, with one combined per-kill list at the bottom carrying each row's mob
 * name once there's more than one target. Loot from anything else killed during the farm shows
 * under "Other income" and is not part of the net.
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

	private final JPanel mobBlocksSection = new JPanel();
	private final JPanel mobBlocksPanel = new JPanel();
	private final JPanel killSection = new JPanel();
	private final JPanel killPanel = new JPanel();
	private final JPanel otherSection = new JPanel();
	private final JPanel otherPanel = new JPanel();
	private final JPanel costSection = new JPanel();
	private final JPanel costPanel = new JPanel();
	private final JPanel deathsSection = new JPanel();
	private final JPanel deathsPanel = new JPanel();

	/** True while a farm is running or paused (not finished) - decides what the shared
	 *  field/button pair does when submitted: Start a new farm, or Add to the running one. */
	private boolean farmActive;

	TargetedContent(ProfitLossCalculatorPanel.Controls controls, ItemManager itemManager)
	{
		this.controls = controls;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		mobField.setToolTipText("Exact NPC name, e.g. Brutus - start typing for suggestions");
		mobField.addActionListener(e -> submitMobField());
		AutocompletePopup.attach(mobField, KnownMobNames.all());
		startBtn.setFocusPainted(false);
		startBtn.addActionListener(e -> submitMobField());

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

		// full-width search field with the Start farm / Add mob button stacked flush below it
		content.add(PanelUi.stretch(mobField));
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

		mobBlocksPanel.setLayout(new BoxLayout(mobBlocksPanel, BoxLayout.Y_AXIS));
		section(mobBlocksSection, "Per mob", mobBlocksPanel);
		killPanel.setLayout(new BoxLayout(killPanel, BoxLayout.Y_AXIS));
		section(killSection, "Per kill", killPanel);
		otherPanel.setLayout(new BoxLayout(otherPanel, BoxLayout.Y_AXIS));
		section(otherSection, "Other income", otherPanel);
		costPanel.setLayout(new BoxLayout(costPanel, BoxLayout.Y_AXIS));
		section(costSection, "Costs", costPanel);
		deathsPanel.setLayout(new BoxLayout(deathsPanel, BoxLayout.Y_AXIS));
		section(deathsSection, "Deaths", deathsPanel);
		content.add(mobBlocksSection);
		content.add(killSection);
		content.add(otherSection);
		content.add(costSection);
		content.add(deathsSection);

		add(content, BorderLayout.NORTH);
		render(ProfitLossCalculatorPanel.View.builder().build());
	}

	/** The shared field/button pair: Start a new farm when idle, or add another mob to the
	 *  group when one is already running/paused. Clears the field after a successful add so
	 *  it's ready for the next name - "type, Add, type the next one, Add". A collective label
	 *  ("Moons of Peril", ...) expands to all of its bosses at once. */
	private void submitMobField()
	{
		final java.util.List<String> mobs = KnownMobNames.expand(mobField.getText());
		if (mobs.isEmpty())
		{
			return;
		}
		if (farmActive)
		{
			mobs.forEach(controls::onAddTargetMob);
		}
		else
		{
			controls.onStartFarm(mobs);
		}
		mobField.setText("");
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
		final boolean otherModeActive = !view.isTargeted() && view.isActive();
		final boolean multiTarget = view.getMobBlocks().size() > 1;
		farmActive = view.isTargeted() && view.isActive();

		startBtn.setEnabled(!otherModeActive);
		startBtn.setText(farmActive ? "Add mob" : "Start farm");
		// enabled whenever it's usable: idle and free to start, or live and free to add to
		mobField.setEnabled(!otherModeActive);
		runButtons.setVisible(farm && view.isActive());
		pauseBtn.setText(view.isPaused() ? "Resume" : "Pause");

		hintLabel.setText(otherModeActive
			? (view.isSlayer() ? "A Slayer task is running - see the Slayer tab"
				: "A session is running - stop it to start a farm")
			: farmActive ? "Type another mob and Add to grow this farm"
			: farm ? "" : "Type a mob name and Start to track its profit per kill");
		hintLabel.setVisible(!hintLabel.getText().isEmpty());

		titleLabel.setText(farm
			? (multiTarget ? "Target Farm · " + view.getMobBlocks().size() + " mobs" : view.getTitle())
				+ (view.getState().isEmpty() ? "" : "  ·  " + view.getState())
			: "");
		killsLabel.setText(farm && view.getKills() > 0
			? view.getKills() + " kill" + (view.getKills() == 1 ? "" : "s")
			: "");

		statGrid.removeAll();
		statGrid.setVisible(farm);
		if (farm)
		{
			PanelUi.addNetCells(statGrid, view, PanelUi.statCell("", view.getKills() > 0
				? PanelUi.sign(view.getGpPerKill()) + "/kill" : "", ColorScheme.LIGHT_GRAY_COLOR, false));
			statGrid.add(PanelUi.statCell("Potential", "+" + PanelUi.gpPlain(view.getGains()), PanelUi.GAIN_COLOR, false));
			statGrid.add(PanelUi.statCell("Losses", "-" + PanelUi.gpPlain(view.getLosses()), PanelUi.LOSS_COLOR, false));
			statGrid.add(PanelUi.statCell("Rate", view.getNetPerHour() != 0
				? PanelUi.gpPlain(view.getNetPerHour()) + "/hr" : "-", ColorScheme.LIGHT_GRAY_COLOR, false));
			statGrid.add(PanelUi.statCell("Kill time", view.getSecPerKill() > 0
				? "~" + PanelUi.secs(view.getSecPerKill()) : "-", ColorScheme.LIGHT_GRAY_COLOR, false));
			PanelUi.addRealisedSoFar(statGrid, view);
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

		mobBlocksSection.setVisible(farm && multiTarget);
		mobBlocksPanel.removeAll();
		if (multiTarget)
		{
			for (ProfitLossCalculatorPanel.MobFarmBlock mb : view.getMobBlocks())
			{
				mobBlocksPanel.add(PanelUi.mobFarmBlock(mb, itemManager));
				mobBlocksPanel.add(PanelUi.vgap(4));
			}
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
