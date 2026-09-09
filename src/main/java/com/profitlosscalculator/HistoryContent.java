/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The "History" tab. A lifetime, per-mob record built from every run - plain sessions and
 * targeted farms alike. One loot-tracker-style box per mob (money gained vs cost to stay
 * alive, coloured net, drop icons), with a drill-down into the runs that fought it.
 *
 * <p>Everything is one BoxLayout-Y column inside a BorderLayout NORTH slot and rebuilt from
 * scratch each render - no nested BoxLayout panels (they cache stale zero sizes).
 */
@Slf4j
class HistoryContent extends JPanel
{
	private static final DateTimeFormatter WHEN =
		DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
	private static final String NOT_IN_COMBAT = "Not in combat";

	private final ProfitLossCalculatorPanel.Controls controls;
	private final ItemManager itemManager;

	private final JPanel content = new JPanel();

	/** The two lifetime views, toggled by a button pair at the top of every screen. */
	private enum Tab { MONEY, MOBS }

	private SessionHistory.Snapshot lifetime = SessionHistory.Snapshot.EMPTY;
	/** id -&gt; name, resolved by the plugin on the client thread (ItemManager forbids the EDT). */
	private java.util.Map<Integer, String> itemNames = java.util.Collections.emptyMap();
	private Tab tab = Tab.MONEY;
	private String openMob;
	/** true = showing the flat GE Sales log instead of the by-mob list / a mob drill-down. */
	private boolean showSales;

	HistoryContent(ProfitLossCalculatorPanel.Controls controls, ItemManager itemManager)
	{
		this.controls = controls;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.NORTH);

		rebuild();
	}

	void render(SessionHistory.Snapshot snapshot, java.util.Map<Integer, String> names)
	{
		this.lifetime = snapshot == null ? SessionHistory.Snapshot.EMPTY : snapshot;
		this.itemNames = names == null ? java.util.Collections.emptyMap() : names;
		if (openMob != null && find(openMob) == null)
		{
			openMob = null;
		}
		rebuild();
	}

	private void rebuild()
	{
		content.removeAll();
		try
		{
			tabBar();
			if (showSales)
			{
				buildSales();
			}
			else if (openMob != null)
			{
				buildDetail(find(openMob));
			}
			else if (tab == Tab.MONEY)
			{
				buildMoney();
			}
			else
			{
				buildList();
			}
		}
		catch (RuntimeException ex)
		{
			log.warn("history render failed", ex);
		}
		content.revalidate();
		content.repaint();
		revalidate();
		repaint();
	}

	/** The seamless "Money | Mobs" toggle sat above every lifetime screen. */
	private void tabBar()
	{
		final JPanel bar = new JPanel(new GridLayout(1, 2, 0, 0));
		bar.setOpaque(false);
		bar.add(tabButton("Money", Tab.MONEY));
		bar.add(tabButton("Mobs", Tab.MOBS));
		add(content, bar);
		add(content, Box.createVerticalStrut(8));
	}

	private JButton tabButton(String text, Tab which)
	{
		final boolean on = tab == which;
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setBackground(on ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		b.setForeground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		b.setFont(b.getFont().deriveFont(on ? Font.BOLD : Font.PLAIN));
		b.setBorder(BorderFactory.createMatteBorder(0, 0, on ? 2 : 0, 0, ColorScheme.BRAND_ORANGE));
		b.addActionListener(e ->
		{
			tab = which;
			openMob = null;
			showSales = false;
			rebuild();
		});
		return b;
	}

	// ------------------------------------------------------------------ money

	private void buildMoney()
	{
		add(content, heading("MONEY · LIFETIME"));
		add(content, Box.createVerticalStrut(4));

		// Net: the cash that's actually landed, against what it cost to get there
		final long confirmed = lifetime.getRealisedNet();
		final long cost = lifetime.getCost();
		final long cashNet = confirmed - cost;
		final JLabel net = new JLabel("Net  " + PanelUi.sign(cashNet));
		net.setFont(net.getFont().deriveFont(Font.BOLD, net.getFont().getSize2D() + 3f));
		net.setForeground(cashNet >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR);
		add(content, net);
		add(content, small("<html>confirmed <font color='#78d28c'>+" + PanelUi.gpPlain(confirmed) + "</font>"
			+ (lifetime.getRealisedTax() > 0
				? "  <font color='#e17878'>−" + PanelUi.gpPlain(lifetime.getRealisedTax()) + "</font> tax" : "")
			+ "   <font color='#e17878'>−" + PanelUi.gpPlain(cost) + "</font> cost to attain</html>"));
		add(content, Box.createVerticalStrut(8));

		// Estimated item value (GE snapshot of the loot) vs what's actually been turned into gp
		final long estimated = lifetime.getGained();
		add(content, PanelUi.sectionLabel("Estimated value vs money attained"));
		add(content, stat("Estimated item value", PanelUi.gp(estimated), PanelUi.GAIN_COLOR, false));
		add(content, stat("Money attained", PanelUi.gp(confirmed)
			+ (estimated > 0 ? "   (" + (confirmed * 100 / estimated) + "%)" : ""), PanelUi.GAIN_COLOR, false));
		final long toSell = estimated - lifetime.getSoldValue();
		if (toSell > 0)
		{
			add(content, stat("Still to sell", PanelUi.gp(toSell)
				+ (lifetime.getBankedValue() > 0 ? "  ·  " + PanelUi.gp(lifetime.getBankedValue()) + " banked" : ""),
				ColorScheme.LIGHT_GRAY_COLOR, false));
		}
		add(content, Box.createVerticalStrut(8));

		// Estimated profit / loss for each mob (loot GE value − cost; realised is a global
		// figure now, not per mob - see the Items tab for the per-item split)
		add(content, PanelUi.sectionLabel("Profit / loss by mob"));
		final List<SessionHistory.MobStats> mobs = lifetime.getMobs();
		if (mobs.isEmpty())
		{
			add(content, small("No runs recorded yet"));
		}
		for (SessionHistory.MobStats m : mobs)
		{
			add(content, moneyMobRow(m));
			add(content, Box.createVerticalStrut(2));
		}
		add(content, Box.createVerticalStrut(8));

		final JButton sales = new JButton(lifetime.getSales().isEmpty()
			? "GE Sales log" : "GE Sales log  ·  " + lifetime.getSales().size());
		sales.setFocusPainted(false);
		sales.addActionListener(e ->
		{
			showSales = true;
			rebuild();
		});
		add(content, PanelUi.stretch(sales));
		add(content, Box.createVerticalStrut(6));
		addManageButtons();
	}

	/** Compact clickable "Brutus ×426 … +2.1M" row for the Money tab's by-mob P/L. */
	private JPanel moneyMobRow(SessionHistory.MobStats m)
	{
		final Color accent = m.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR;
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		final JLabel name = new JLabel("<html>" + PanelUi.escape(label(m.getName()))
			+ (m.getKills() > 0
				? "  <font color='#a0a0a0'>×" + QuantityFormatter.formatNumber(m.getKills()) + "</font>" : "")
			+ "</html>");
		name.setFont(FontManager.getRunescapeSmallFont());

		final JLabel val = new JLabel(PanelUi.sign(m.getNet()));
		val.setFont(FontManager.getRunescapeSmallFont());
		val.setForeground(accent);
		val.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(name, BorderLayout.CENTER);
		row.add(val, BorderLayout.EAST);
		onClick(row, () -> open(m.getName()));
		return row;
	}

	// ------------------------------------------------------------------ mobs

	private void buildList()
	{
		add(content, heading("BY MOB · LIFETIME"));
		add(content, Box.createVerticalStrut(3));
		add(content, small("<html>" + (lifetime.getKills() > 0
			? "<b>" + QuantityFormatter.formatNumber(lifetime.getKills()) + "</b> kills  ·  " : "")
			+ lifetime.getRuns() + (lifetime.getRuns() == 1 ? " run" : " runs")
			+ "  ·  <font color='#78d28c'>" + PanelUi.gpPlain(lifetime.getGained())
			+ "</font> looted (est.)</html>"));
		add(content, Box.createVerticalStrut(8));

		final List<SessionHistory.MobStats> mobs = lifetime.getMobs();
		if (mobs.isEmpty())
		{
			add(content, small("No runs recorded yet"));
		}
		for (SessionHistory.MobStats m : mobs)
		{
			add(content, mobBox(m));
			add(content, Box.createVerticalStrut(4));
		}

		add(content, Box.createVerticalStrut(6));
		addManageButtons();
	}

	private void addManageButtons()
	{
		final JButton refresh = new JButton("Refresh");
		refresh.setFocusPainted(false);
		refresh.addActionListener(e -> controls.onRefreshHistory());
		final JButton clear = new JButton("Clear history");
		clear.setFocusPainted(false);
		clear.setForeground(PanelUi.LOSS_COLOR);
		clear.addActionListener(e -> confirmClear());
		final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		buttons.add(refresh);
		buttons.add(clear);
		add(content, buttons);
	}

	// ------------------------------------------------------------------ GE sales log

	private void buildSales()
	{
		final JButton back = new JButton("◀  Back");
		back.setFocusPainted(false);
		back.addActionListener(e ->
		{
			showSales = false;
			rebuild();
		});
		add(content, back);
		add(content, Box.createVerticalStrut(6));
		add(content, heading("GE SALES & HIGH ALCH · LIFETIME"));
		add(content, Box.createVerticalStrut(3));

		final List<SessionHistory.SaleRow> sales = lifetime.getSales();
		long gross = 0;
		long tax = 0;
		long netv = 0;
		for (SessionHistory.SaleRow s : sales)
		{
			gross += s.getGross();
			tax += s.getTax();
			netv += s.getNet();
		}
		add(content, small("<html>" + sales.size() + (sales.size() == 1 ? " sale" : " sales")
			+ "  ·  gross <font color='#78d28c'>" + PanelUi.gpPlain(gross) + "</font>"
			+ (tax > 0 ? "  ·  tax <font color='#e17878'>" + PanelUi.gpPlain(tax) + "</font>" : "")
			+ "  ·  net <font color='#78d28c'>" + PanelUi.gpPlain(netv) + "</font></html>"));
		add(content, Box.createVerticalStrut(6));

		if (sales.isEmpty())
		{
			add(content, small("No GE sales or High Alchs recorded yet"));
		}
		int n = 0;
		for (SessionHistory.SaleRow s : sales)
		{
			if (n++ == 300)
			{
				break;
			}
			add(content, saleRow(s));
			add(content, Box.createVerticalStrut(2));
		}
	}

	private JPanel saleRow(SessionHistory.SaleRow s)
	{
		final boolean alch = "alch".equals(s.getKind());
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, alch ? PanelUi.SPELL_COLOR : PanelUi.GAIN_COLOR),
			BorderFactory.createEmptyBorder(2, 5, 2, 5)));

		final JLabel left = new JLabel("<html>" + when(s.getAt()) + "  "
			+ QuantityFormatter.formatNumber(s.getQty()) + "× " + PanelUi.escape(itemName(s.getItemId()))
			+ (alch ? "  <font color='#b48cff'>alch</font>" : "")
			+ (s.getMob() != null && !s.getMob().isEmpty()
				? "  <font color='#a0a0a0'>" + PanelUi.escape(s.getMob()) + "</font>" : "")
			+ "</html>");
		left.setFont(FontManager.getRunescapeSmallFont());

		final JLabel right = new JLabel("<html>+" + PanelUi.gpPlain(s.getNet())
			+ (s.getTax() > 0 ? "<br><font color='#e17878'>−" + PanelUi.gpPlain(s.getTax()) + "</font>" : "")
			+ "</html>");
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(PanelUi.GAIN_COLOR);
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		right.setVerticalAlignment(SwingConstants.TOP);

		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	/** A loot-tracker-style box: two-line header over an item icon grid. All BorderLayout. */
	private JPanel mobBox(SessionHistory.MobStats m)
	{
		final Color accent = m.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR;

		final JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(4, 6, 6, 6)));
		box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		final JPanel line1 = new JPanel(new BorderLayout(6, 0));
		line1.setOpaque(false);
		final JLabel name = new JLabel(label(m.getName())
			+ (m.getKills() > 0 ? "  ×" + QuantityFormatter.formatNumber(m.getKills()) : ""));
		final JLabel net = new JLabel(PanelUi.sign(m.getNet()) + " gp");
		net.setForeground(accent);
		net.setHorizontalAlignment(SwingConstants.RIGHT);
		line1.add(name, BorderLayout.CENTER);
		line1.add(net, BorderLayout.EAST);

		// one grey sub-block: the breakdown line (only when it adds info beyond the net) then
		// the per-kill / runs line, joined so an empty first line leaves no gap
		final java.util.List<String> lines = new java.util.ArrayList<>();
		if (m.getCost() > 0)
		{
			lines.add("+" + PanelUi.gpPlain(m.getGained()) + " loot   −" + PanelUi.gpPlain(m.getCost()) + " cost");
		}
		lines.add(m.getKills() > 0
			? PanelUi.sign(m.getGpPerKill()) + "/kill"
				+ (m.getSecPerKill() > 0 ? "  ·  ~" + PanelUi.secs(m.getSecPerKill()) + "/kill"
					: m.getRuns() > 1 ? "  ·  " + m.getRuns() + " runs" : "")
			: m.getRuns() + (m.getRuns() == 1 ? " run" : " runs"));

		final JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(line1, BorderLayout.NORTH);
		head.add(small("<html>" + String.join("<br>", lines) + "</html>"), BorderLayout.CENTER);
		box.add(head, BorderLayout.NORTH);

		try
		{
			final List<ProfitLossCalculatorPanel.GridItem> items = gridItems(m.getItems());
			if (!items.isEmpty())
			{
				final JPanel icons = new JPanel(new GridLayout(0, PanelUi.GRID_COLS, 1, 1));
				icons.setOpaque(false);
				icons.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
				PanelUi.fillGrid(icons, items, ColorScheme.DARK_GRAY_COLOR, itemManager);
				box.add(icons, BorderLayout.CENTER);
			}
		}
		catch (RuntimeException ex)
		{
			// item sprites not ready yet - show the box without its icon grid
			log.debug("could not build drop grid for {}", m.getName(), ex);
		}

		onClick(box, () -> open(m.getName()));
		return box;
	}

	// ------------------------------------------------------------------ detail

	private void buildDetail(SessionHistory.MobStats m)
	{
		if (m == null)
		{
			openMob = null;
			buildList();
			return;
		}

		final JButton back = new JButton("◀  " + label(m.getName()));
		back.setFocusPainted(false);
		back.addActionListener(e -> open(null));
		add(content, back);
		add(content, Box.createVerticalStrut(6));

		add(content, stat("Potential net", PanelUi.sign(m.getNet()),
			m.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR, true));
		if (m.getKills() > 0)
		{
			add(content, stat("Per kill", PanelUi.sign(m.getGpPerKill()),
				m.getGpPerKill() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR, false));
			add(content, stat("Kills", QuantityFormatter.formatNumber(m.getKills())
				+ "  ·  " + m.getRuns() + (m.getRuns() == 1 ? " run" : " runs"),
				ColorScheme.LIGHT_GRAY_COLOR, false));
			if (m.getSecPerKill() > 0)
			{
				add(content, stat("Kill time", "~" + PanelUi.secs(m.getSecPerKill())
					+ "  ·  " + (3600 / m.getSecPerKill()) + "/hr", ColorScheme.LIGHT_GRAY_COLOR, false));
			}
		}
		else
		{
			add(content, stat("Runs", Integer.toString(m.getRuns()), ColorScheme.LIGHT_GRAY_COLOR, false));
		}
		add(content, stat("Potential (GE value)", PanelUi.gp(m.getGained())
			+ (m.getDropped() != m.getGained() ? "  of  " + PanelUi.gp(m.getDropped()) + " dropped" : ""),
			PanelUi.GAIN_COLOR, false));
		add(content, stat("Cost to stay alive", PanelUi.gp(m.getCost()), PanelUi.LOSS_COLOR, false));
		if (m.getDeaths() > 0)
		{
			add(content, stat("Deaths", Integer.toString(m.getDeaths()), PanelUi.DEATH_COLOR, false));
		}

		add(content, Box.createVerticalStrut(8));
		add(content, PanelUi.sectionLabel("Runs"));
		for (SessionHistory.RunRow r : m.getRunList())
		{
			add(content, runBox(r));
			add(content, Box.createVerticalStrut(2));
		}

		final List<long[]> items = m.getItems();
		if (items != null && !items.isEmpty())
		{
			add(content, Box.createVerticalStrut(8));
			add(content, PanelUi.sectionLabel("Top drops"));
			int n = 0;
			for (long[] it : items)
			{
				if (n++ == 24)
				{
					break;
				}
				add(content, dropRow((int) it[0], it[1], it.length >= 3 ? it[2] : 0));
			}
		}

		add(content, Box.createVerticalStrut(10));
		final JButton del = new JButton("Delete " + label(m.getName()) + " history");
		del.setFocusPainted(false);
		del.setForeground(PanelUi.LOSS_COLOR);
		del.addActionListener(e -> confirmDelete(m.getName()));
		add(content, del);
	}

	/** "12×  Cowhide" left, its value right - the name wraps rather than clipping. */
	private JPanel dropRow(int id, long qty, long gp)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		final JLabel name = new JLabel("<html>" + QuantityFormatter.formatNumber(qty) + "×  "
			+ PanelUi.escape(itemName(id)) + "</html>");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.CENTER);
		if (gp > 0)
		{
			final JLabel v = new JLabel(PanelUi.gpPlain(gp));
			v.setFont(FontManager.getRunescapeSmallFont());
			v.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			v.setHorizontalAlignment(SwingConstants.RIGHT);
			v.setVerticalAlignment(SwingConstants.TOP);
			row.add(v, BorderLayout.EAST);
		}
		return row;
	}

	private JPanel runBox(SessionHistory.RunRow r)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, r.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));

		final JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);
		final JLabel l1 = new JLabel(when(r.getStart()) + "  ·  " + runKindLabel(r.getKind()));
		l1.setFont(FontManager.getRunescapeSmallFont());
		left.add(l1, BorderLayout.NORTH);
		final boolean rate = ("farm".equals(r.getKind()) || "slayer".equals(r.getKind()))
			&& r.getKills() > 0 && r.getDurationSec() > 0;
		left.add(small((r.getKills() > 0 ? QuantityFormatter.formatNumber(r.getKills()) + " kills  ·  " : "")
			+ dur(r.getDurationSec())
			+ (rate ? "  ·  ~" + PanelUi.secs(r.getDurationSec() / r.getKills()) + "/kill" : "")),
			BorderLayout.CENTER);

		final JLabel right = new JLabel("<html>" + PanelUi.sign(r.getNet())
			+ (r.getKills() > 0 ? "<br><font color='#a0a0a0'>" + PanelUi.sign(r.getNet() / r.getKills())
				+ "/kill</font>" : "") + "</html>");
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(r.getNet() >= 0 ? PanelUi.GAIN_COLOR : PanelUi.LOSS_COLOR);
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		right.setVerticalAlignment(SwingConstants.TOP);

		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	// ------------------------------------------------------------------ helpers

	private void open(String mob)
	{
		openMob = mob;
		rebuild();
	}

	private SessionHistory.MobStats find(String name)
	{
		for (SessionHistory.MobStats m : lifetime.getMobs())
		{
			if (m.getName().equals(name))
			{
				return m;
			}
		}
		return null;
	}

	/**
	 * Add {@code c} to a BoxLayout-Y column. Bare labels are wrapped in a full-width
	 * BorderLayout panel so their {@code <html>} text wraps to the panel width instead of
	 * being clipped with an ellipsis.
	 */
	private static void add(JPanel col, Component c)
	{
		if (c instanceof JLabel)
		{
			final JPanel w = new JPanel(new BorderLayout());
			w.setOpaque(false);
			w.add(c, BorderLayout.CENTER);
			w.setAlignmentX(LEFT_ALIGNMENT);
			col.add(w);
			return;
		}
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
		}
		col.add(c);
	}

	private List<ProfitLossCalculatorPanel.GridItem> gridItems(List<long[]> items)
	{
		final List<ProfitLossCalculatorPanel.GridItem> out = new ArrayList<>();
		if (items != null)
		{
			for (long[] it : items)
			{
				if (it == null || it.length < 2 || it[1] <= 0 || out.size() == 20)
				{
					continue;
				}
				out.add(new ProfitLossCalculatorPanel.GridItem((int) it[0], (int) it[1],
					it.length >= 3 ? it[2] : 0, itemName((int) it[0])));
			}
		}
		return out;
	}

	/** Attach {@code action} to a component and every descendant (Swing clicks don't bubble). */
	private static void onClick(Component c, Runnable action)
	{
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		});
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				onClick(child, action);
			}
		}
	}

	private JLabel stat(String label, String value, Color color, boolean bold)
	{
		return PanelUi.statCell(label, value, color, bold);
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

	private static String label(String name)
	{
		return name == null || name.isEmpty() ? NOT_IN_COMBAT : name;
	}

	private static String runKindLabel(String kind)
	{
		if ("farm".equals(kind))
		{
			return "farm";
		}
		if ("slayer".equals(kind))
		{
			return "slayer task";
		}
		return "session";
	}

	private static String when(String iso)
	{
		try
		{
			return WHEN.format(Instant.parse(iso));
		}
		catch (RuntimeException ex)
		{
			return iso == null ? "?" : iso;
		}
	}

	private static String dur(long secs)
	{
		final Duration d = Duration.ofSeconds(Math.max(0, secs));
		final long h = d.toHours();
		final long m = d.minusHours(h).toMinutes();
		return h > 0 ? h + "h " + m + "m" : m + "m";
	}

	private void confirmClear()
	{
		final int r = JOptionPane.showConfirmDialog(this,
			"Delete all recorded history?\nThis wipes history.jsonl, the realised / banked logs and the "
				+ "per-session logs, and cannot be undone.",
			"Clear history", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r == JOptionPane.YES_OPTION)
		{
			controls.onClearHistory();
		}
	}

	private void confirmDelete(String mob)
	{
		final int r = JOptionPane.showConfirmDialog(this,
			"Delete all recorded history for " + label(mob) + "?\nThis cannot be undone.",
			"Delete mob", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r == JOptionPane.YES_OPTION)
		{
			controls.onDeleteMob(mob);
			open(null);
		}
	}

	private String itemName(int id)
	{
		return itemNames.getOrDefault(id, "Item " + id);
	}
}
