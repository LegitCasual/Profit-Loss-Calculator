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
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * Shared Swing helpers for the panel tabs (Session and Targeted render the same kind of
 * summary block, icon grids, cost rows and death rows).
 */
final class PanelUi
{
	static final Color SUPPLIES_COLOR = new Color(120, 190, 255);
	static final Color SPELL_COLOR = new Color(180, 140, 255);
	static final Color TELEPORT_COLOR = new Color(120, 220, 200);
	static final Color AMMO_COLOR = new Color(230, 190, 110);
	static final Color DEATH_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color GAIN_COLOR = new Color(120, 210, 140);
	static final Color LOSS_COLOR = new Color(225, 120, 120);
	static final Color GAIN_CELL = new Color(35, 60, 42);
	static final Color LOSS_CELL = new Color(64, 38, 38);

	static final int GRID_COLS = 5;
	static final int CELL = 36;

	private PanelUi()
	{
	}

	static Color colorFor(String kind)
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
			case "alch":
				return GAIN_COLOR;
			default:
				return SUPPLIES_COLOR;
		}
	}

	/** A "Label value" cell: grey label, coloured value, packed two per row. */
	static JLabel statCell(String label, String value, Color valueColor, boolean bold)
	{
		final String hex = String.format("#%02x%02x%02x",
			valueColor.getRed(), valueColor.getGreen(), valueColor.getBlue());
		final JLabel l = new JLabel("<html>"
			+ (label.isEmpty() ? "" : "<font color='#a0a0a0'>" + label + "</font> ")
			+ "<font color='" + hex + "'>" + value + "</font></html>");
		l.setFont(bold
			? l.getFont().deriveFont(Font.BOLD)
			: FontManager.getRunescapeSmallFont());
		return l;
	}

	static void fillGrid(JPanel grid, List<SessionCostTrackerPanel.GridItem> items, Color cell, ItemManager itemManager)
	{
		grid.removeAll();
		if (items.isEmpty())
		{
			grid.setVisible(false);
			return;
		}
		grid.setVisible(true);
		final int rows = (items.size() + GRID_COLS - 1) / GRID_COLS;
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * CELL + (rows - 1) * 2));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < rows * GRID_COLS; i++)
		{
			final JPanel slot = new JPanel(new BorderLayout());
			slot.setBackground(cell);
			if (i < items.size())
			{
				final SessionCostTrackerPanel.GridItem gi = items.get(i);
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

	static JPanel eventLine(SessionCostTrackerPanel.EventLine line)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
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

	/** "#37  14:32" left, that kill's collected value right, items in the tooltip. */
	static JPanel killRow(SessionCostTrackerPanel.KillRow k)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, k.getCollected() >= 0 ? GAIN_COLOR : LOSS_COLOR),
			BorderFactory.createEmptyBorder(2, 5, 2, 5)));

		final JLabel left = new JLabel("#" + k.getIndex() + "   " + k.getTime());
		left.setFont(FontManager.getRunescapeSmallFont());
		final JLabel right = new JLabel(sign(k.getCollected()));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(GAIN_COLOR);
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

	static JPanel deathRow(SessionCostTrackerPanel.DeathRow d, SessionCostTrackerPanel.Controls controls)
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

	static JLabel sectionLabel(String text)
	{
		final JLabel l = new JLabel(text.toUpperCase());
		l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f).deriveFont(Font.BOLD));
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(2, 0, 3, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	static Component vgap(int h)
	{
		return Box.createVerticalStrut(h);
	}

	/** Left-align and let a component fill the panel width; height is left to the layout. */
	static <T extends JComponent> T stretch(T c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getMaximumSize().height));
		return c;
	}

	static String sign(long v)
	{
		return (v >= 0 ? "+" : "") + gpPlain(v);
	}

	static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}

	static String gpPlain(long v)
	{
		return QuantityFormatter.formatNumber(v);
	}

	/** A duration for the panel: "24s", "2m 05s", "1h 20m". */
	static String secs(long s)
	{
		if (s <= 0)
		{
			return "0s";
		}
		if (s < 60)
		{
			return s + "s";
		}
		if (s < 3600)
		{
			return (s / 60) + "m " + String.format("%02ds", s % 60);
		}
		return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
	}
}
