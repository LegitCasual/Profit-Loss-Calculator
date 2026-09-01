/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

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
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar panel with three tabs:
 *
 * <ul>
 *     <li><b>Session</b> - a flat "record my profit / loss for this stretch of time" run.
 *         Net, gp/hr and two item grids (green picked up, red consumed), plus optional
 *         income / cost lists and any unresolved deaths.</li>
 *     <li><b>Targeted</b> - type one mob, Start, and track a clean per-kill ledger for just
 *         that mob (see {@link TargetedContent}).</li>
 *     <li><b>History</b> - the lifetime, per-mob record of targeted farms
 *         (see {@link HistoryContent}).</li>
 * </ul>
 *
 * Only one run (session or farm) is active at a time.
 */
class SessionCostTrackerPanel extends PluginPanel
{
	interface Controls
	{
		/** Session-tab flip button: start a plain session when idle, else pause / resume. */
		void onStartPauseResume();

		/** Targeted-tab start button: begin a farm of {@code mob}. */
		void onStartFarm(String mob);

		void onStop();

		void onRestart();

		void onConfirmDeath(int deathId, long fee);

		void onGravestone(int deathId);

		/** Wipe history.jsonl and the detail logs (the panel confirms with the user first). */
		void onClearHistory();

		/** Drop one mob from the recorded history (the panel confirms first). */
		void onDeleteMob(String mob);

		/** Re-read history.jsonl from disk and push a fresh snapshot to the History tab. */
		void onRefreshHistory();

		/** The first-run screen's Continue button was clicked - persist that it's been seen. */
		void onWelcomeDismissed();
	}

	/** One item cell in a gain / loss grid. */
	@Value
	static class GridItem
	{
		int id;
		int qty;
		long value;
		String name;
	}

	@Value
	static class EventLine
	{
		/** "supplies", "spell", "teleport", "ammo", "death" or an income kind. */
		String kind;
		String time;
		String label;
		long gp;
		String tooltip;
	}

	/** One kill in a targeted farm. */
	@Value
	static class KillRow
	{
		int index;
		String time;
		long collected;
		long dropped;
		String tooltip;
	}

	@Value
	static class DeathRow
	{
		int deathId;
		String itemSummary;
		long estimatedFee;
		long fullValue;
		boolean returned;
	}

	@Value
	@Builder
	static class View
	{
		boolean active;
		boolean paused;
		boolean finished;
		boolean targeted;
		@Builder.Default
		String targetMob = "";
		@Builder.Default
		String state = "";
		@Builder.Default
		String title = "Session";
		int kills;
		long elapsedSeconds;
		long gains;
		long losses;
		long net;
		long netPerHour;
		long gpPerKill;
		/** Targeted farm: run seconds / kills - the average time per kill. */
		long secPerKill;
		long potential;
		long atRisk;
		boolean showIncomeList;
		boolean showCostList;
		@Singular
		List<GridItem> gainItems;
		@Singular
		List<GridItem> lossItems;
		@Singular
		List<KillRow> killRows;
		@Singular
		List<EventLine> incomeEvents;
		@Singular
		List<EventLine> costEvents;
		@Singular
		List<DeathRow> deaths;
	}

	private final Controls controls;

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

	private final ItemManager itemManager;
	private final TargetedContent targetedContent;
	private final HistoryContent historyContent;

	/** Holds the tabs + tab content. Swapped out for {@link #welcome} on first run. */
	private final JPanel contentHost = new JPanel(new BorderLayout());
	private final WelcomeContent welcome;

	SessionCostTrackerPanel(Controls controls, ItemManager itemManager, boolean showWelcome)
	{
		this.controls = controls;
		this.itemManager = itemManager;
		this.targetedContent = new TargetedContent(controls, itemManager);
		this.historyContent = new HistoryContent(controls, itemManager);

		setLayout(new BorderLayout());

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

		final JPanel sessionContent = new JPanel(new BorderLayout());
		sessionContent.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
		sessionContent.add(currentStack, BorderLayout.NORTH);

		final JPanel display = new JPanel(new BorderLayout());
		final MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		final MaterialTab sessionTab = new MaterialTab("Session", tabs, sessionContent);
		final MaterialTab targetedTab = new MaterialTab("Targeted", tabs, targetedContent);
		final MaterialTab historyTab = new MaterialTab("History", tabs, historyContent);
		// opening the History tab re-reads history.jsonl from disk and re-lays the panel -
		// covers a first render that happened before the client (or the tab) was ready
		historyTab.setOnSelectEvent(() ->
		{
			controls.onRefreshHistory();
			return true;
		});
		tabs.addTab(sessionTab);
		tabs.addTab(targetedTab);
		tabs.addTab(historyTab);
		contentHost.add(tabs, BorderLayout.NORTH);
		contentHost.add(display, BorderLayout.CENTER);
		tabs.select(sessionTab);

		welcome = new WelcomeContent(this::dismissWelcome);
		add(showWelcome ? welcome : contentHost, BorderLayout.CENTER);

		render(View.builder().build());
	}

	/** Leave the first-run screen for good and reveal the tabs. */
	private void dismissWelcome()
	{
		if (welcome.getParent() == null)
		{
			return;
		}
		remove(welcome);
		add(contentHost, BorderLayout.CENTER);
		revalidate();
		repaint();
		controls.onWelcomeDismissed();
	}

	private static void section(JPanel holder, String title, JPanel body)
	{
		holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.add(PanelUi.sectionLabel(title));
		holder.add(body);
		holder.add(PanelUi.vgap(10));
	}

	void renderHistory(SessionHistory.Snapshot lifetime, java.util.Map<Integer, String> itemNames)
	{
		historyContent.render(lifetime, itemNames);
	}

	void render(View view)
	{
		targetedContent.render(view);

		final boolean farmActive = view.isTargeted() && view.isActive();
		final boolean sessionRunning = !view.isTargeted() && view.isActive();
		final boolean sessionShowing = !view.isTargeted() && (view.isActive() || view.isFinished());

		primaryBtn.setEnabled(!farmActive);
		primaryBtn.setText(farmActive ? "Farm running"
			: !sessionRunning ? "Start session"
			: view.isPaused() ? "Resume" : "Pause");
		primaryBtn.setBackground(farmActive ? ColorScheme.MEDIUM_GRAY_COLOR
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
		noSessionLabel.setText(farmActive
			? "A targeted farm is running - see the Targeted tab"
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
			for (DeathRow d : view.getDeaths())
			{
				deathsPanel.add(PanelUi.deathRow(d, controls));
				deathsPanel.add(PanelUi.vgap(4));
			}
		}

		revalidate();
		repaint();
	}

	private void fillLines(JPanel holder, JPanel body, boolean show, List<EventLine> lines)
	{
		holder.setVisible(show && !lines.isEmpty());
		body.removeAll();
		for (EventLine line : lines)
		{
			body.add(PanelUi.eventLine(line));
		}
	}
}
