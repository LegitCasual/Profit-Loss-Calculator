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
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Sidebar panel: a Start / Pause / Resume flip button, Stop and Restart, an optional boss
 * name with a kill tally, the running session total, every tracked cost on its own line,
 * and an interactive row per unresolved death.
 */
class SessionCostTrackerPanel extends PluginPanel
{
	private static final Color SUPPLIES_COLOR = new Color(120, 190, 255);
	private static final Color SPELL_COLOR = new Color(180, 140, 255);
	private static final Color TELEPORT_COLOR = new Color(120, 220, 200);
	private static final Color AMMO_COLOR = new Color(230, 190, 110);
	private static final Color DEATH_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;

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

	@Value
	static class EventLine
	{
		/** "supplies", "spell", "teleport", "ammo" or "death". */
		String kind;
		String time;
		String label;
		long gp;
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
	static class View
	{
		boolean active;
		boolean paused;
		boolean finished;
		long sessionTotal;
		long atRisk;
		String bossName;
		int bossKills;
		List<EventLine> events;
		List<DeathRow> deaths;
	}

	private final Controls controls;

	private final JButton primaryBtn = new JButton("Start session");
	private final JButton stopBtn = new JButton("Stop");
	private final JButton restartBtn = new JButton("Restart");
	private final JPanel secondaryRow = new JPanel(new GridLayout(1, 2, 4, 0));
	private final JTextField bossField = new JTextField();
	private final JButton killBtn = new JButton("+1 kill");
	private final JLabel killsLabel = new JLabel();
	private final JLabel sessionTotalLabel = new JLabel();
	private final JLabel stateLabel = new JLabel();
	private final JLabel atRiskLabel = new JLabel();
	private final JPanel eventsPanel = new JPanel();
	private final JPanel deathsPanel = new JPanel();

	SessionCostTrackerPanel(Controls controls)
	{
		this.controls = controls;

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
		killsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		final JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.add(primaryBtn);
		north.add(secondaryRow);
		north.add(box(8));
		north.add(labelledField("Boss", bossField));
		final JPanel killRow = new JPanel(new BorderLayout(6, 0));
		killRow.add(killsLabel, BorderLayout.CENTER);
		killRow.add(killBtn, BorderLayout.EAST);
		killRow.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		killRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, killBtn.getPreferredSize().height + 4));
		north.add(killRow);
		north.add(box(8));

		final JPanel totals = new JPanel(new GridLayout(0, 1, 0, 3));
		sessionTotalLabel.setFont(sessionTotalLabel.getFont().deriveFont(Font.BOLD));
		stateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		atRiskLabel.setForeground(DEATH_COLOR);
		totals.add(sessionTotalLabel);
		totals.add(stateLabel);
		totals.add(atRiskLabel);
		north.add(totals);
		add(north, BorderLayout.NORTH);

		final JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));
		deathsPanel.setLayout(new BoxLayout(deathsPanel, BoxLayout.Y_AXIS));
		center.add(sectionLabel("Costs"));
		center.add(eventsPanel);
		center.add(box(10));
		center.add(sectionLabel("Unresolved deaths"));
		center.add(deathsPanel);
		add(center, BorderLayout.CENTER);

		render(new View(false, false, false, 0, 0, "", 0, new ArrayList<>(), new ArrayList<>()));
	}

	void render(View view)
	{
		final boolean idle = !view.isActive();
		primaryBtn.setText(idle ? "Start session" : view.isPaused() ? "Resume" : "Pause");
		primaryBtn.setBackground(idle || view.isPaused()
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_INPROGRESS_COLOR);
		secondaryRow.setVisible(view.isActive());

		if (!bossField.isFocusOwner() && !bossField.getText().equals(view.getBossName()))
		{
			bossField.setText(view.getBossName());
		}
		killBtn.setEnabled(view.isActive());
		killsLabel.setText("Kills: " + view.getBossKills());

		sessionTotalLabel.setText("Session total: " + gp(view.getSessionTotal()));
		if (view.isPaused())
		{
			stateLabel.setText("PAUSED");
			stateLabel.setForeground(AMMO_COLOR);
		}
		else if (view.isFinished())
		{
			stateLabel.setText("Stopped");
			stateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else if (view.isActive())
		{
			stateLabel.setText("Running");
			stateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else
		{
			stateLabel.setText(" ");
		}
		if (view.getBossKills() > 0 && view.getSessionTotal() > 0)
		{
			stateLabel.setText(stateLabel.getText() + "  ·  "
				+ gp(view.getSessionTotal() / view.getBossKills()) + "/kill");
		}
		atRiskLabel.setText(view.getAtRisk() > 0 ? "At risk: " + gp(view.getAtRisk()) : " ");

		eventsPanel.removeAll();
		if (view.getEvents().isEmpty())
		{
			eventsPanel.add(muted(view.isActive() ? "No costs yet" : "No session"));
		}
		for (EventLine line : view.getEvents())
		{
			eventsPanel.add(eventLine(line));
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

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}
}
