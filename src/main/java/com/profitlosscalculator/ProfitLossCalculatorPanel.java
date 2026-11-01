/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

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
 *
 * <p>A red "Needs review" box sits at the top of the nav bar whenever a death is waiting for
 * an approve / deny (see {@link ReviewContent}), and is hidden entirely when nothing is.
 */
class ProfitLossCalculatorPanel extends PluginPanel
{
	interface Controls
	{
		/** Session-tab flip button: start a plain session when idle, else pause / resume. */
		void onStartPauseResume();

		/** Targeted-tab start button: begin a farm of {@code mobs} (one name, or several when a
		 *  collective label like "Moons of Peril" was picked). */
		void onStartFarm(List<String> mobs);

		/** Targeted-tab "Add mob" button: grow the current farm's target group. */
		void onAddTargetMob(String mob);

		/** Slayer-tab start button: begin tracking the currently detected task. */
		void onStartSlayer();

		void onStop();

		void onRestart();

		void onConfirmDeath(int deathId, long fee);

		void onGravestone(int deathId);

		/** Needs review: the player's verdict on a full-loss death. Approve keeps the charge;
		 *  Deny lowers it to {@code fee} (the items came back from the grave). */
		void onReviewDeath(DeathReviews.Review review, boolean approved, long fee);

		/** Wipe history.jsonl and the detail logs (the panel confirms with the user first). */
		void onClearHistory();

		/** Drop one mob from the recorded history (the panel confirms first). */
		void onDeleteMob(String mob);

		/** Re-read history.jsonl from disk and push a fresh snapshot to the History tab. */
		void onRefreshHistory();

		/** History tab's "Open dashboard" button: show the dashboard window. */
		void onOpenDashboard();

		/** The first-run screen's Continue button was clicked - persist that it's been seen. */
		void onWelcomeDismissed();

		/** The "What's new" screen's OK button was clicked - persist which release was seen. */
		void onWhatsNewDismissed();

		/** A Costs list header's order toggle was clicked - flip newest / oldest first. */
		void onToggleCostOrder();
	}

	/** Which one-off screen, if any, stands in for the tabs when the panel first opens. */
	enum Notice
	{
		NONE, WELCOME, WHATS_NEW
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

	/** One target mob's own block in a multi-target Target Farm - only rendered when the
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
	@Builder(toBuilder = true)
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
		/** Potential net: collected loot at GE snapshot − cost. The "if I sold it all now" figure. */
		long net;
		/** Actual net: what the run has really made so far - realised (alched / sold) proceeds
		 *  plus loot still held at GE, minus cost. Equals {@link #net} until something is cashed
		 *  out. A live hint; the History tab is the source of truth. */
		long actualNet;
		long netPerHour;
		long gpPerKill;
		/** Targeted farm / Slayer task: run seconds / kills - the average time per kill. */
		long secPerKill;
		long potential;
		long atRisk;
		/** Live only: value High Alched, and net value moved to a bank, during this run so far
		 *  (0 unless you actually alched / banked mid-run). Realised / banked totals proper live
		 *  in the History tab. */
		long realisedSoFar;
		long bankedSoFar;
		boolean showIncomeList;
		boolean showCostList;
		boolean costsNewestFirst;
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
	private static final String MODE_TARGETED = "Target Farm";
	private static final String MODE_SLAYER = "Slayer";
	private static final String MODE_ITEMS = "Items";
	private static final String CARD_MODES = "modes";
	private static final String CARD_HISTORY = "history";
	private static final String CARD_REVIEW = "review";
	private static final Color REVIEW_BG = new Color(120, 30, 30);

	private final Controls controls;

	private final JButton historyBtn = new JButton("History");
	private final JComboBox<String> modeSelector = new JComboBox<>(new String[]{MODE_SESSION, MODE_TARGETED, MODE_SLAYER, MODE_ITEMS});
	private final CardLayout modeLayout = new CardLayout();
	private final JPanel modeHost = new JPanel(modeLayout);
	private final CardLayout bodyLayout = new CardLayout();
	private final JPanel bodyHost = new JPanel(bodyLayout);
	/** The body card on show: {@link #CARD_MODES}, {@link #CARD_HISTORY} or {@link #CARD_REVIEW}. */
	private String card = CARD_MODES;
	private final JLabel reviewBox = new JLabel("", SwingConstants.CENTER);
	private int reviewCount;

	private final SessionContent sessionContent;
	private final TargetedContent targetedContent;
	private final SlayerContent slayerContent;
	private final HistoryContent historyContent;
	private final ItemsContent itemsContent;
	private final ReviewContent reviewContent;

	/** Holds the nav bar + body. Swapped out for {@link #notice} on first run / after an update. */
	private final JPanel contentHost = new JPanel(new BorderLayout());
	/** The welcome or "What's new" screen currently covering the tabs, or {@code null}. */
	private JPanel notice;

	ProfitLossCalculatorPanel(Controls controls, ItemManager itemManager, Notice startNotice)
	{
		this.controls = controls;
		this.sessionContent = new SessionContent(controls, itemManager);
		this.targetedContent = new TargetedContent(controls, itemManager);
		this.slayerContent = new SlayerContent(controls, itemManager);
		this.historyContent = new HistoryContent(controls, itemManager);
		this.itemsContent = new ItemsContent(itemManager);
		this.reviewContent = new ReviewContent(controls);

		setLayout(new BorderLayout());

		historyBtn.setFocusPainted(false);
		historyBtn.addActionListener(e -> toggleHistory());

		reviewBox.setOpaque(true);
		reviewBox.setBackground(REVIEW_BG);
		reviewBox.setForeground(Color.WHITE);
		reviewBox.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));
		reviewBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		reviewBox.setToolTipText("Deaths charged as a full loss - approve or deny each one");
		reviewBox.setVisible(false);
		reviewBox.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showCard(CARD_REVIEW);
			}
		});

		modeSelector.setFocusable(false);
		modeSelector.addActionListener(e ->
		{
			final String pick = (String) modeSelector.getSelectedItem();
			modeLayout.show(modeHost, pick);
			if (MODE_ITEMS.equals(pick))
			{
				controls.onRefreshHistory();
			}
		});

		// Bug-report link in the nav bar on every tab (modes and History alike), so it outlives
		// the first-run screen that used to be its only home. Sits up here rather than in a
		// SOUTH footer, which got pinned to the bottom of the full-height sidebar.
		final JButton reportIssue = new JButton("Report an issue / suggest a feature");
		reportIssue.setFocusPainted(false);
		reportIssue.setFont(FontManager.getRunescapeSmallFont());
		reportIssue.setToolTipText("Opens the plugin's GitHub issues page");
		reportIssue.addActionListener(e -> LinkBrowser.browse(WelcomeContent.ISSUES_URL));

		final JPanel navBar = new JPanel();
		navBar.setLayout(new BoxLayout(navBar, BoxLayout.Y_AXIS));
		navBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
		navBar.add(PanelUi.stretch(reviewBox));
		navBar.add(PanelUi.vgap(4));
		navBar.add(PanelUi.stretch(historyBtn));
		navBar.add(PanelUi.vgap(4));
		navBar.add(PanelUi.stretch(modeSelector));
		navBar.add(PanelUi.vgap(4));
		navBar.add(PanelUi.stretch(reportIssue));

		modeHost.add(sessionContent, MODE_SESSION);
		modeHost.add(targetedContent, MODE_TARGETED);
		modeHost.add(slayerContent, MODE_SLAYER);
		modeHost.add(itemsContent, MODE_ITEMS);

		bodyHost.add(modeHost, CARD_MODES);
		bodyHost.add(historyContent, CARD_HISTORY);
		bodyHost.add(reviewContent, CARD_REVIEW);

		contentHost.add(navBar, BorderLayout.NORTH);
		contentHost.add(bodyHost, BorderLayout.CENTER);

		switch (startNotice)
		{
			case WELCOME:
				notice = new WelcomeContent(() -> dismissNotice(controls::onWelcomeDismissed));
				break;
			case WHATS_NEW:
				notice = new WhatsNewContent(() -> dismissNotice(controls::onWhatsNewDismissed));
				break;
			default:
				notice = null;
		}
		add(notice != null ? notice : contentHost, BorderLayout.CENTER);

		render(View.builder().build());
	}

	/** History button flip: "History" navigates to the lifetime record, "◀  Back" returns to
	 *  whichever tracking mode is selected in the dropdown. Mirrors the exact swap technique
	 *  {@link #dismissNotice} uses for the first-run screen. */
	private void toggleHistory()
	{
		showCard(CARD_MODES.equals(card) ? CARD_HISTORY : CARD_MODES);
	}

	/** Swap the body to {@code next}. Off the mode view, the History button reads "◀  Back"
	 *  and the mode dropdown (and the review box, while reviewing) steps aside. */
	private void showCard(String next)
	{
		card = next;
		final boolean modes = CARD_MODES.equals(next);
		historyBtn.setText(modes ? "History" : "◀  Back");
		modeSelector.setVisible(modes);
		if (CARD_HISTORY.equals(next))
		{
			controls.onRefreshHistory();
		}
		reviewBox.setVisible(reviewCount > 0 && !CARD_REVIEW.equals(next));
		bodyLayout.show(bodyHost, next);
	}

	/** Refresh the Needs review list; the red box shows only while something needs review. */
	void renderReviews(List<DeathReviews.Review> reviews)
	{
		reviewCount = reviews.size();
		reviewBox.setText(reviewCount == 1 ? "1 death needs review" : reviewCount + " deaths need review");
		reviewContent.render(reviews);
		if (reviewCount == 0 && CARD_REVIEW.equals(card))
		{
			showCard(CARD_MODES);
		}
		reviewBox.setVisible(reviewCount > 0 && !CARD_REVIEW.equals(card));
		revalidate();
		repaint();
	}

	/** Leave the welcome / "What's new" screen for good and reveal the panel. */
	private void dismissNotice(Runnable persist)
	{
		if (notice == null || notice.getParent() == null)
		{
			return;
		}
		remove(notice);
		notice = null;
		add(contentHost, BorderLayout.CENTER);
		revalidate();
		repaint();
		persist.run();
	}

	void renderHistory(SessionHistory.Snapshot lifetime, java.util.Map<Integer, String> itemNames)
	{
		historyContent.render(lifetime, itemNames);
		itemsContent.render(lifetime, itemNames);
	}

	void render(View view)
	{
		sessionContent.render(view);
		targetedContent.render(view);
		slayerContent.render(view);
	}
}
