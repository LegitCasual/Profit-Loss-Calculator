/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * The "Items" tab - the global loot ledger. One row per item you have ever looted (any run,
 * any mob): how many, worth how much at snapshot, how many have been sold / alched and for
 * what, and how many are still unsold. Read-only.
 *
 * <p>Fed the same lifetime {@link SessionHistory.Snapshot} the History tab gets. One
 * BoxLayout-Y column in a BorderLayout NORTH slot, rebuilt from scratch each render.
 */
@Slf4j
class ItemsContent extends JPanel
{
	private final ItemManager itemManager;
	private final JPanel content = new JPanel();

	private SessionHistory.Snapshot lifetime = SessionHistory.Snapshot.EMPTY;
	private Map<Integer, String> itemNames = java.util.Collections.emptyMap();

	ItemsContent(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.NORTH);
		rebuild();
	}

	void render(SessionHistory.Snapshot snapshot, Map<Integer, String> names)
	{
		this.lifetime = snapshot == null ? SessionHistory.Snapshot.EMPTY : snapshot;
		this.itemNames = names == null ? java.util.Collections.emptyMap() : names;
		rebuild();
	}

	private void rebuild()
	{
		content.removeAll();
		try
		{
			buildHeader();
			final List<SessionHistory.ItemStat> items = lifetime.getItems();
			if (items.isEmpty())
			{
				put(small("No loot recorded yet"));
			}
			for (SessionHistory.ItemStat it : items)
			{
				put(row(it));
				put(Box.createVerticalStrut(3));
			}
		}
		catch (RuntimeException ex)
		{
			log.warn("items render failed", ex);
		}
		content.revalidate();
		content.repaint();
		revalidate();
		repaint();
	}

	private void buildHeader()
	{
		put(heading("LOOT LEDGER · LIFETIME"));
		put(Box.createVerticalStrut(3));

		final JLabel potential = new JLabel("Potential  +" + PanelUi.gpPlain(lifetime.getGained()));
		potential.setFont(potential.getFont().deriveFont(Font.BOLD, potential.getFont().getSize2D() + 1f));
		potential.setForeground(PanelUi.GAIN_COLOR);
		put(potential);

		if (lifetime.getRealisedNet() > 0 || lifetime.getSoldValue() > 0)
		{
			final JLabel actual = new JLabel("Actual net  " + PanelUi.sign(lifetime.getActualNet()));
			actual.setFont(actual.getFont().deriveFont(Font.BOLD, actual.getFont().getSize2D() + 1f));
			actual.setForeground(lifetime.getActualNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR);
			put(actual);
			put(small("<html>realised <font color='#78d28c'>" + PanelUi.gpPlain(lifetime.getRealisedNet())
				+ "</font>"
				+ (lifetime.getRealisedTax() > 0
					? "  −<font color='#e17878'>" + PanelUi.gpPlain(lifetime.getRealisedTax()) + "</font> tax" : "")
				+ (lifetime.getBankedValue() > 0
					? "  ·  banked <font color='#78baff'>" + PanelUi.gpPlain(lifetime.getBankedValue()) + "</font>" : "")
				+ "</html>"));
		}
		put(small(lifetime.getItems().size() + (lifetime.getItems().size() == 1 ? " item" : " items")
			+ "  ·  cost " + PanelUi.gpPlain(lifetime.getCost())));
		put(Box.createVerticalStrut(8));
	}

	/** icon + name over a grey "looted / sold / unsold" line. All BorderLayout (no nested BoxLayout). */
	private JPanel row(SessionHistory.ItemStat it)
	{
		final JPanel box = new JPanel(new BorderLayout(6, 0));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0,
				it.getEffectiveSold() > 0 ? PanelUi.GAIN_COLOR : ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));

		final JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);
		try
		{
			final AsyncBufferedImage img = itemManager.getImage((int) it.getItemId(),
				(int) Math.max(1, it.getLootedQty()), it.getLootedQty() > 1);
			if (img != null)
			{
				img.addTo(icon);
			}
		}
		catch (RuntimeException ignored)
		{
			// sprite not ready
		}

		final StringBuilder sub = new StringBuilder("<html>looted <b>")
			.append(QuantityFormatter.formatNumber(it.getLootedQty())).append("</b> (")
			.append(PanelUi.gpPlain(it.getLootedValue())).append(")");
		if (it.getSoldQty() > 0)
		{
			sub.append("  ·  sold ").append(QuantityFormatter.formatNumber(it.getEffectiveSold()))
				.append(" &rarr; <font color='#78d28c'>").append(PanelUi.gpPlain(it.getSoldNet())).append("</font>");
			if (it.getSoldTax() > 0)
			{
				sub.append(" <font color='#e17878'>−").append(PanelUi.gpPlain(it.getSoldTax())).append("</font>");
			}
			if (it.getSoldQty() > it.getLootedQty())
			{
				sub.append(" <font color='#a0a0a0'>(").append(QuantityFormatter.formatNumber(it.getSoldQty()))
					.append(" sold, capped at looted)</font>");
			}
		}
		if (it.getUnsoldQty() > 0)
		{
			sub.append("  ·  ").append(QuantityFormatter.formatNumber(it.getUnsoldQty()))
				.append(" unsold (").append(PanelUi.gpPlain(it.getUnsoldValue())).append(")");
		}
		sub.append("</html>");

		final JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		final JLabel name = new JLabel(itemName((int) it.getItemId()));
		text.add(name, BorderLayout.NORTH);
		text.add(small(sub.toString()), BorderLayout.CENTER);

		box.add(icon, BorderLayout.WEST);
		box.add(text, BorderLayout.CENTER);
		return box;
	}

	// ------------------------------------------------------------------ helpers (mirror HistoryContent)

	private void put(Component c)
	{
		if (c instanceof JLabel)
		{
			final JPanel w = new JPanel(new BorderLayout());
			w.setOpaque(false);
			w.add(c, BorderLayout.CENTER);
			w.setAlignmentX(LEFT_ALIGNMENT);
			content.add(w);
			return;
		}
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
		}
		content.add(c);
	}

	private static JLabel small(String text)
	{
		final JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private static JLabel heading(String text)
	{
		final JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f).deriveFont(Font.BOLD));
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private String itemName(int id)
	{
		return itemNames.getOrDefault(id, "Item " + id);
	}

}
