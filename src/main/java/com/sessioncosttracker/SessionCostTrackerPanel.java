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
 * Sidebar panel: a one-click Start/Stop toggle, live session/trip totals, and - for
 * testing visibility - every tracked event listed on its own line under its trip, plus
 * an interactive row per unresolved death.
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
		void onStartStop();

		void onConfirmDeath(int deathId, long fee);

		void onGravestone(int deathId);
	}

	@Value
	static class EventLine
	{
		/** "supplies", "spell" or "death". */
		String kind;
		String time;
		String label;
		long gp;
		String tooltip;
	}

	@Value
	static class TripView
	{
		int tripId;
		boolean current;
		long total;
		List<EventLine> lines;
	}

	@Value
	static class DeathRow
	{
		int deathId;
		int tripId;
		String itemSummary;
		long estimatedFee;
		long fullValue;
		boolean returned;
	}

	@Value
	static class View
	{
		boolean active;
		int currentTripId;
		long currentTripCost;
		long sessionTotal;
		long atRisk;
		List<TripView> trips;
		List<DeathRow> deaths;
	}

	private final Controls controls;

	private final JButton toggle = new JButton("Start session");
	private final JLabel sessionTotalLabel = new JLabel();
	private final JLabel currentTripLabel = new JLabel();
	private final JLabel atRiskLabel = new JLabel();
	private final JPanel tripsPanel = new JPanel();
	private final JPanel deathsPanel = new JPanel();

	SessionCostTrackerPanel(Controls controls)
	{
		this.controls = controls;

		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(0, 8));

		toggle.setFocusPainted(false);
		toggle.addActionListener(e -> controls.onStartStop());

		final JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.add(toggle);
		north.add(box(6));

		final JPanel totals = new JPanel(new GridLayout(0, 1, 0, 3));
		sessionTotalLabel.setFont(sessionTotalLabel.getFont().deriveFont(Font.BOLD));
		currentTripLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		atRiskLabel.setForeground(DEATH_COLOR);
		totals.add(sessionTotalLabel);
		totals.add(currentTripLabel);
		totals.add(atRiskLabel);
		north.add(totals);
		add(north, BorderLayout.NORTH);

		final JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		tripsPanel.setLayout(new BoxLayout(tripsPanel, BoxLayout.Y_AXIS));
		deathsPanel.setLayout(new BoxLayout(deathsPanel, BoxLayout.Y_AXIS));
		center.add(sectionLabel("Trips"));
		center.add(tripsPanel);
		center.add(box(10));
		center.add(sectionLabel("Unresolved deaths"));
		center.add(deathsPanel);
		add(center, BorderLayout.CENTER);

		render(new View(false, 0, 0, 0, 0, new ArrayList<>(), new ArrayList<>()));
	}

	void render(View view)
	{
		toggle.setText(view.isActive() ? "Stop session" : "Start session");
		toggle.setBackground(view.isActive()
			? ColorScheme.PROGRESS_ERROR_COLOR
			: ColorScheme.PROGRESS_COMPLETE_COLOR);

		sessionTotalLabel.setText("Session total: " + gp(view.getSessionTotal()));
		currentTripLabel.setText(view.isActive()
			? "Current trip #" + view.getCurrentTripId() + ": " + gp(view.getCurrentTripCost())
			: " ");
		atRiskLabel.setText(view.getAtRisk() > 0 ? "At risk: " + gp(view.getAtRisk()) : " ");

		tripsPanel.removeAll();
		if (view.getTrips().isEmpty())
		{
			tripsPanel.add(muted(view.isActive() ? "No events yet" : "No trips with spending"));
		}
		for (TripView t : view.getTrips())
		{
			tripsPanel.add(tripBlock(t));
			tripsPanel.add(box(6));
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

	private JPanel tripBlock(TripView t)
	{
		final JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		final JLabel title = new JLabel("Trip #" + t.getTripId() + (t.isCurrent() ? "  (open)" : ""));
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		final JLabel value = new JLabel(gp(t.getTotal()));
		value.setFont(value.getFont().deriveFont(Font.BOLD));
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		header.add(title, BorderLayout.WEST);
		header.add(value, BorderLayout.EAST);
		block.add(header);

		if (t.getLines().isEmpty())
		{
			final JLabel none = muted("   no events");
			none.setBorder(BorderFactory.createEmptyBorder(0, 4, 3, 4));
			block.add(none);
		}
		for (EventLine line : t.getLines())
		{
			block.add(eventLine(line));
		}
		return block;
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

		final JLabel head = new JLabel(String.format("Death (trip #%d) - lost %s",
			d.getTripId(), gp(d.getFullValue())));
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
