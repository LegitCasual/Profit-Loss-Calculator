/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel. A History button sits above a dropdown that picks between the three tracking
 * modes:
 *
 * <ul>
 *     <li><b>Session</b> - a flat "record my profit / loss for this stretch of time" run.
 *         Net, gp/hr and two item grids (green picked up, red consumed), plus optional
 *         income / cost lists and any unresolved deaths (see {@link SessionContent}).</li>
 *     <li><b>Targeted</b> - type one mob, Start, and track a clean per-kill ledger for just
 *         that mob (see {@link TargetedContent}).</li>
 *     <li><b>Slayer</b> - auto-detects the current Slayer task and tracks a per-kill ledger
 *         for every mob that matches it (see {@link SlayerContent}).</li>
 * </ul>
 *
 * The History button swaps the whole mode view out for the lifetime, per-mob record of every
 * run (see {@link HistoryContent}); clicking it again ("◀  Back") returns to whichever mode
 * was selected. Only one run (session, farm or Slayer task) is active at a time.
 */
class ProfitLossCalculatorPanel extends PluginPanel
{
	interface Controls
	{
		/** Session-tab flip button: start a plain session when idle, else pause / resume. */
		void onStartPauseResume();

		/** Targeted-tab start button: begin a farm of {@code mob}. */
		void onStartFarm(String mob);

		/** Targeted-tab "Add mob" button: grow the current farm's target group. */
		void onAddTargetMob(String mob);

		/** Slayer-tab start button: begin tracking the currently detected task. */
		void onStartSlayer();

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

	/** One kill in a Targeted farm or Slayer task. */
	@Value
	static class KillRow
	{
		int index;
		String time;
		long collected;
		long dropped;
		String tooltip;
		/** The mob's name - populated for Slayer (several species can appear), blank for
		 *  Targeted (redundant - it's always the one farmed mob). */
		String mobName;
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

	/** One target mob's own block in a multi-target Boss Target Farm - only rendered when the
	 *  farm has more than one target (a single-target farm looks exactly like it always has). */
	@Value
	static class MobFarmBlock
	{
		String mobName;
		int kills;
		long net;
		long gains;
		long losses;
		long gpPerKill;
		List<GridItem> gainItems;
	}

	@Value
	@Builder
	static class View
	{
		boolean active;
		boolean paused;
		boolean finished;
		@Builder.Default
		Session.RunMode mode = Session.RunMode.SESSION;
		/** Live-detected Slayer task info - populated even when idle, so the Slayer tab can
		 *  preview the current task before Start is pressed. */
		@Builder.Default
		String slayerTaskName = "";
		@Builder.Default
		String slayerTaskLocation = "";
		int slayerInitialAmount;
		int slayerRemainingAmount;
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
		/** Targeted farm / Slayer task: run seconds / kills - the average time per kill. */
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
		/** One entry per target mob in a Targeted farm, in add-order - empty for Session/Slayer
		 *  and for a single-target farm (which just uses the regular summary/grids as-is). */
		@Singular
		List<MobFarmBlock> mobBlocks;

		boolean isTargeted()
		{
			return mode == Session.RunMode.TARGETED;
		}

		boolean isSlayer()
		{
			return mode == Session.RunMode.SLAYER;
		}

		/** True for either grouped mode (Targeted or Slayer) - has a per-kill bucket and an
		 *  "Other income" split, as opposed to a plain session where everything just counts. */
		boolean isGrouped()
		{
			return mode != Session.RunMode.SESSION;
		}
	}

	private static final String MODE_SESSION = "Session";
	private static final String MODE_TARGETED = "Boss Target Farm";
	private static final String MODE_SLAYER = "Slayer";
	private static final String CARD_MODES = "modes";
	private static final String CARD_HISTORY = "history";

	private final Controls controls;

	private final JButton historyBtn = new JButton("History");
	private final JComboBox<String> modeSelector = new JComboBox<>(new String[]{MODE_SESSION, MODE_TARGETED, MODE_SLAYER});
	private final CardLayout modeLayout = new CardLayout();
	private final JPanel modeHost = new JPanel(modeLayout);
	private final CardLayout bodyLayout = new CardLayout();
	private final JPanel bodyHost = new JPanel(bodyLayout);
	private boolean showingHistory;

	private final SessionContent sessionContent;
	private final TargetedContent targetedContent;
	private final SlayerContent slayerContent;
	private final HistoryContent historyContent;

	/** Holds the nav bar + body. Swapped out for {@link #welcome} on first run. */
	private final JPanel contentHost = new JPanel(new BorderLayout());
	private final WelcomeContent welcome;

	ProfitLossCalculatorPanel(Controls controls, ItemManager itemManager, boolean showWelcome)
	{
		this.controls = controls;
		this.sessionContent = new SessionContent(controls, itemManager);
		this.targetedContent = new TargetedContent(controls, itemManager);
		this.slayerContent = new SlayerContent(controls, itemManager);
		this.historyContent = new HistoryContent(controls, itemManager);

		setLayout(new BorderLayout());

		historyBtn.setFocusPainted(false);
		historyBtn.addActionListener(e -> toggleHistory());

		modeSelector.setFocusable(false);
		modeSelector.addActionListener(e -> modeLayout.show(modeHost, (String) modeSelector.getSelectedItem()));

		final JPanel navBar = new JPanel();
		navBar.setLayout(new BoxLayout(navBar, BoxLayout.Y_AXIS));
		navBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
		navBar.add(PanelUi.stretch(historyBtn));
		navBar.add(PanelUi.vgap(4));
		navBar.add(PanelUi.stretch(modeSelector));

		modeHost.add(sessionContent, MODE_SESSION);
		modeHost.add(targetedContent, MODE_TARGETED);
		modeHost.add(slayerContent, MODE_SLAYER);

		bodyHost.add(modeHost, CARD_MODES);
		bodyHost.add(historyContent, CARD_HISTORY);

		contentHost.add(navBar, BorderLayout.NORTH);
		contentHost.add(bodyHost, BorderLayout.CENTER);

		welcome = new WelcomeContent(this::dismissWelcome);
		add(showWelcome ? welcome : contentHost, BorderLayout.CENTER);

		render(View.builder().build());
	}

	/** History button flip: "History" navigates to the lifetime record, "◀  Back" returns to
	 *  whichever tracking mode is selected in the dropdown. Mirrors the exact swap technique
	 *  {@link #dismissWelcome} uses for the first-run screen. */
	private void toggleHistory()
	{
		showingHistory = !showingHistory;
		historyBtn.setText(showingHistory ? "◀  Back" : "History");
		modeSelector.setVisible(!showingHistory);
		if (showingHistory)
		{
			controls.onRefreshHistory();
		}
		bodyLayout.show(bodyHost, showingHistory ? CARD_HISTORY : CARD_MODES);
	}

	/** Leave the first-run screen for good and reveal the panel. */
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

	void renderHistory(SessionHistory.Snapshot lifetime, java.util.Map<Integer, String> itemNames)
	{
		historyContent.render(lifetime, itemNames);
	}

	void render(View view)
	{
		sessionContent.render(view);
		targetedContent.render(view);
		slayerContent.render(view);
	}
}
