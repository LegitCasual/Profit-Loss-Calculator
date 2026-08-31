/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * Sidebar panel. Top: the Start / Pause / Resume flip button, Stop and Restart, and the
 * boss name. Then a summary block for the boss (or the whole session) - net, gp/hr, and
 * two icon grids: green for everything picked up, red for everything consumed. Below that,
 * one row per boss kill (hover for that fight's drops and spend), then the optional flat
 * income and cost lists, then a row per unresolved death.
 */
class SessionCostTrackerPanel extends PluginPanel
{
	private static final Color SUPPLIES_COLOR = new Color(120, 190, 255);
	private static final Color SPELL_COLOR = new Color(180, 140, 255);
	private static final Color TELEPORT_COLOR = new Color(120, 220, 200);
	private static final Color AMMO_COLOR = new Color(230, 190, 110);
	private static final Color DEATH_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color GAIN_COLOR = new Color(120, 210, 140);
	private static final Color LOSS_COLOR = new Color(225, 120, 120);
	private static final Color GAIN_CELL = new Color(38, 66, 44);
	private static final Color LOSS_CELL = new Color(70, 40, 40);

	private static final int GRID_COLS = 5;

	interface Controls
	{
		/** Flip: start when idle, pause when running, resume when paused. */
		void onStartPauseResume();

		void onStop();

		void onRestart();

		/** Manual +1 to the boss kill tally. */
		void onBossKill();

		void onBossName(String name);

		void onConfirmDeath(int deathId, long fee);

		void onGravestone(int deathId);
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

	/** One boss kill row - the tooltip carries that fight's full breakdown. */
	@Value
	static class KillRow
	{
		int index;
		String name;
		String time;
		long net;
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
		@Builder.Default
		String state = "";
		@Builder.Default
		String title = "Session";
		@Builder.Default
		String bossName = "";
		int kills;
		long elapsedSeconds;
		long gains;
		long losses;
		long net;
		long netPerHour;
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
	private final ItemManager itemManager;

	private final JButton primaryBtn = new JButton("Start session");
	private final JButton stopBtn = new JButton("Stop");
	private final JButton restartBtn = new JButton("Restart");
	private final JPanel secondaryRow = new JPanel(new GridLayout(1, 2, 4, 0));
	private final JTextField bossField = new JTextField();
	private final JButton killBtn = new JButton("+1 kill");

	private final JLabel titleLabel = new JLabel();
	private final JLabel netLabel = new JLabel();
	private final JLabel rateLabel = new JLabel();
	private final JLabel gainsLossesLabel = new JLabel();
	private final JLabel atRiskLabel = new JLabel();
	private final JPanel gainGrid = new JPanel();
	private final JPanel lossGrid = new JPanel();

	private final JPanel killSection = new JPanel();
	private final JPanel killPanel = new JPanel();
	private final JPanel incomeSection = new JPanel();
	private final JPanel incomePanel = new JPanel();
	private final JPanel costSection = new JPanel();
	private final JPanel costPanel = new JPanel();
	private final JPanel deathsPanel = new JPanel();

	SessionCostTrackerPanel(Controls controls, ItemManager itemManager)
	{
		this.controls = controls;
		this.itemManager = itemManager;

		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(0, 8));

		primaryBtn.setFocusPainted(false);
		primaryBtn.addActionListener(e -> controls.onStartPauseResume());
		stopBtn.setFocusPainted(false);
		restartBtn.setFocusPainted(false);
		stopBtn.addActionListener(e -> controls.onStop());
		restartBtn.addActionListener(e -> controls.onRestart());
		secondaryRow.add(stopBtn);
		secondaryRow.add(restartBtn);
		secondaryRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		bossField.setToolTipText("Boss name - kills of an NPC matching this bump the tally automatically");
		bossField.addActionListener(e -> controls.onBossName(bossField.getText()));
		bossField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				controls.onBossName(bossField.getText());
			}
		});
		killBtn.setFont(FontManager.getRunescapeSmallFont());
		killBtn.setFocusPainted(false);
		killBtn.addActionListener(e -> controls.onBossKill());

		final JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.add(primaryBtn);
		north.add(secondaryRow);
		north.add(box(8));
		north.add(labelledField("Boss", bossField));
		final JPanel killRow = new JPanel(new BorderLayout(6, 0));
		killRow.add(killBtn, BorderLayout.EAST);
		killRow.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		killRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, killBtn.getPreferredSize().height + 4));
		north.add(killRow);
		add(north, BorderLayout.NORTH);

		// --- summary block ---
		final JPanel summary = new JPanel();
		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summary.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 0, 8, 0)));

		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		netLabel.setFont(netLabel.getFont().deriveFont(Font.BOLD, netLabel.getFont().getSize2D() + 2f));
		rateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		rateLabel.setFont(FontManager.getRunescapeSmallFont());
		gainsLossesLabel.setFont(FontManager.getRunescapeSmallFont());
		atRiskLabel.setForeground(DEATH_COLOR);
		atRiskLabel.setFont(FontManager.getRunescapeSmallFont());

		summary.add(leftRow(titleLabel));
		summary.add(box(2));
		summary.add(leftRow(netLabel));
		summary.add(leftRow(gainsLossesLabel));
		summary.add(leftRow(rateLabel));
		summary.add(leftRow(atRiskLabel));
		summary.add(box(6));
		gainGrid.setLayout(new GridLayout(0, GRID_COLS, 2, 2));
		lossGrid.setLayout(new GridLayout(0, GRID_COLS, 2, 2));
		summary.add(gainGrid);
		summary.add(box(3));
		summary.add(lossGrid);

		final JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.add(summary);
		center.add(box(8));

		killPanel.setLayout(new BoxLayout(killPanel, BoxLayout.Y_AXIS));
		section(killSection, "Kill log", killPanel);
		incomePanel.setLayout(new BoxLayout(incomePanel, BoxLayout.Y_AXIS));
		section(incomeSection, "Income", incomePanel);
		costPanel.setLayout(new BoxLayout(costPanel, BoxLayout.Y_AXIS));
		section(costSection, "Costs", costPanel);
		deathsPanel.setLayout(new BoxLayout(deathsPanel, BoxLayout.Y_AXIS));

		center.add(killSection);
		center.add(incomeSection);
		center.add(costSection);
		center.add(sectionLabel("Unresolved deaths"));
		center.add(deathsPanel);
		add(center, BorderLayout.CENTER);

		render(View.builder().build());
	}

	private static void section(JPanel holder, String title, JPanel body)
	{
		holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
		holder.add(sectionLabel(title));
		holder.add(body);
		holder.add(box(10));
	}

	void render(View view)
	{
		final boolean idle = !view.isActive();
		primaryBtn.setText(idle ? "Start session" : view.isPaused() ? "Resume" : "Pause");
		primaryBtn.setBackground(idle || view.isPaused()
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_INPROGRESS_COLOR);
		secondaryRow.setVisible(view.isActive());
		killBtn.setEnabled(view.isActive());

		if (!bossField.isFocusOwner() && !bossField.getText().equals(view.getBossName()))
		{
			bossField.setText(view.getBossName());
		}

		final boolean anySession = view.isActive() || view.isFinished();
		titleLabel.setText(view.getTitle()
			+ (view.getKills() > 0 ? "   ·   " + view.getKills() + " kill" + (view.getKills() == 1 ? "" : "s") : "")
			+ (view.getState().isEmpty() ? "" : "   ·   " + view.getState()));

		netLabel.setText(anySession ? "Net  " + gp(view.getNet()) : "No session");
		netLabel.setForeground(!anySession
			? ColorScheme.LIGHT_GRAY_COLOR
			: view.getNet() >= 0 ? GAIN_COLOR : LOSS_COLOR);

		if (anySession)
		{
			final JLabel gl = gainsLossesLabel;
			gl.setText("<html><span style='color:#78d28c'>+" + gpPlain(view.getGains())
				+ "</span>   <span style='color:#e17878'>-" + gpPlain(view.getLosses()) + "</span></html>");
			gl.setToolTipText(view.getPotential() != view.getGains()
				? gpPlain(view.getGains()) + " collected of " + gpPlain(view.getPotential()) + " dropped" : null);
			rateLabel.setText(view.getNetPerHour() != 0
				? gp(view.getNetPerHour()) + "/hr"
					+ (view.getKills() > 0 ? "   ·   " + gp(view.getNet() / view.getKills()) + "/kill" : "")
				: elapsed(view.getElapsedSeconds()));
		}
		else
		{
			gainsLossesLabel.setText(" ");
			rateLabel.setText(" ");
		}
		atRiskLabel.setText(view.getAtRisk() > 0 ? "At risk  " + gp(view.getAtRisk()) : " ");

		fillGrid(gainGrid, view.getGainItems(), GAIN_CELL);
		fillGrid(lossGrid, view.getLossItems(), LOSS_CELL);

		killSection.setVisible(!view.getKillRows().isEmpty());
		killPanel.removeAll();
		for (KillRow k : view.getKillRows())
		{
			killPanel.add(killRow(k));
			killPanel.add(box(2));
		}

		incomeSection.setVisible(view.isShowIncomeList());
		incomePanel.removeAll();
		if (view.getIncomeEvents().isEmpty())
		{
			incomePanel.add(muted(view.isActive() ? "Nothing yet" : "None"));
		}
		for (EventLine line : view.getIncomeEvents())
		{
			incomePanel.add(eventLine(line));
		}

		costSection.setVisible(view.isShowCostList());
		costPanel.removeAll();
		if (view.getCostEvents().isEmpty())
		{
			costPanel.add(muted(view.isActive() ? "Nothing yet" : "None"));
		}
		for (EventLine line : view.getCostEvents())
		{
			costPanel.add(eventLine(line));
		}

		deathsPanel.removeAll();
		if (view.getDeaths().isEmpty())
		{
			deathsPanel.add(muted("None"));
		}
		for (DeathRow d : view.getDeaths())
		{
			deathsPanel.add(deathRow(d));
			deathsPanel.add(box(4));
		}

		revalidate();
		repaint();
	}

	private void fillGrid(JPanel grid, List<GridItem> items, Color cell)
	{
		grid.removeAll();
		if (items.isEmpty())
		{
			grid.setVisible(false);
			return;
		}
		grid.setVisible(true);
		final int rows = (items.size() + GRID_COLS - 1) / GRID_COLS;
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * 42 + 4));
		for (int i = 0; i < rows * GRID_COLS; i++)
		{
			final JPanel slot = new JPanel(new BorderLayout());
			slot.setBackground(cell);
			slot.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
			if (i < items.size())
			{
				final GridItem gi = items.get(i);
				final JLabel icon = new JLabel();
				icon.setHorizontalAlignment(SwingConstants.CENTER);
				icon.setVerticalAlignment(SwingConstants.CENTER);
				final String tip = gi.getName() + "  ·  " + gp(gi.getValue())
					+ (gi.getQty() > 1 ? "  (" + QuantityFormatter.formatNumber(gi.getQty()) + ")" : "");
				icon.setToolTipText(tip);
				slot.setToolTipText(tip);
				final AsyncBufferedImage img = itemManager.getImage(gi.getId(), gi.getQty(), gi.getQty() > 1);
				if (img != null)
				{
					img.addTo(icon);
				}
				slot.add(icon);
			}
			grid.add(slot);
		}
	}

	private JPanel killRow(KillRow k)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, k.getNet() >= 0 ? GAIN_COLOR : LOSS_COLOR),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));

		final JLabel left = new JLabel("#" + k.getIndex() + "  " + k.getName()
			+ (k.getTime().isEmpty() ? "" : "  ·  " + k.getTime()));
		left.setFont(FontManager.getRunescapeSmallFont());

		final JLabel right = new JLabel((k.getNet() >= 0 ? "+" : "") + gpPlain(k.getNet()));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(k.getNet() >= 0 ? GAIN_COLOR : LOSS_COLOR);
		right.setHorizontalAlignment(SwingConstants.RIGHT);

		if (k.getTooltip() != null)
		{
			row.setToolTipText(k.getTooltip());
			left.setToolTipText(k.getTooltip());
			right.setToolTipText(k.getTooltip());
		}

		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private JPanel eventLine(EventLine line)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, colorFor(line.getKind())),
			BorderFactory.createEmptyBorder(2, 5, 2, 5)));

		final JLabel left = new JLabel((line.getTime().isEmpty() ? "" : line.getTime() + "  ") + line.getLabel());
		left.setFont(FontManager.getRunescapeSmallFont());
		if (line.getTooltip() != null && !line.getTooltip().isEmpty())
		{
			left.setToolTipText(line.getTooltip());
			row.setToolTipText(line.getTooltip());
		}

		final JLabel right = new JLabel(gp(line.getGp()));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(colorFor(line.getKind()));
		right.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private JPanel deathRow(DeathRow d)
	{
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel head = new JLabel("Death - lost " + gp(d.getFullValue()));
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(head);

		final JLabel items = new JLabel("<html>" + escape(d.getItemSummary()) + "</html>");
		items.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		items.setFont(FontManager.getRunescapeSmallFont());
		items.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(items);

		final JLabel status = new JLabel(d.isReturned()
			? "Items back - confirm the fee paid:"
			: "Pending - not reclaimed yet. If reclaimed, set fee:");
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(status);

		final JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		controlsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		controlsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JTextField fee = new JTextField(String.valueOf(d.getEstimatedFee()), 7);
		final JButton confirm = new JButton("Confirm");
		confirm.addActionListener(e ->
		{
			try
			{
				controls.onConfirmDeath(d.getDeathId(), Long.parseLong(fee.getText().trim().replace(",", "")));
			}
			catch (NumberFormatException ignored)
			{
				// leave the field for the user to fix
			}
		});
		final JButton free = new JButton("Free");
		free.addActionListener(e -> controls.onGravestone(d.getDeathId()));
		controlsRow.add(fee);
		controlsRow.add(confirm);
		controlsRow.add(free);
		p.add(controlsRow);
		return p;
	}

	private static JPanel leftRow(Component c)
	{
		final JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(c, BorderLayout.WEST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 2));
		return p;
	}

	private static JPanel labelledField(String label, JTextField field)
	{
		final JPanel p = new JPanel(new BorderLayout(6, 0));
		final JLabel l = new JLabel(label);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		p.add(l, BorderLayout.WEST);
		p.add(field, BorderLayout.CENTER);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height + 4));
		return p;
	}

	private static Color colorFor(String kind)
	{
		switch (kind)
		{
			case "spell":
				return SPELL_COLOR;
			case "teleport":
				return TELEPORT_COLOR;
			case "ammo":
				return AMMO_COLOR;
			case "death":
				return DEATH_COLOR;
			case "npc_loot":
			case "pvp_loot":
			case "event_loot":
			case "pickpocket":
			case "pickup":
				return GAIN_COLOR;
			default:
				return SUPPLIES_COLOR;
		}
	}

	private static JLabel sectionLabel(String text)
	{
		final JLabel l = new JLabel(text.toUpperCase());
		l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f).deriveFont(Font.BOLD));
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(2, 0, 3, 0));
		return l;
	}

	private static JLabel muted(String text)
	{
		final JLabel l = new JLabel(text);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static Component box(int h)
	{
		final JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, h));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		return p;
	}

	private static String elapsed(long seconds)
	{
		if (seconds < 60)
		{
			return seconds + "s";
		}
		final long m = seconds / 60;
		return m < 60 ? m + " min" : (m / 60) + "h " + (m % 60) + "m";
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}

	private static String gpPlain(long v)
	{
		return QuantityFormatter.formatNumber(v);
	}
}
