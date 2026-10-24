/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * The History dashboard: a separate, non-modal window over the client. A date-range and
 * category filter and headline tiles sit above three pages - Overview (net over time, top
 * activities, a sortable per-activity table), Loot (what the loot is made of, and how sold
 * loot's price drifted) and Costs (where the gp went, by type and by item). Fed via
 * {@link #setData}; all filtering is {@link DashboardStats}, all styling {@link DashboardTheme}.
 *
 * <p>EDT only.
 */
class DashboardWindow extends JFrame
{
	/** An activity needs this much timed play before it can be crowned best gp/hr. */
	private static final long BEST_MIN_SEC = 30 * 60;
	private static final int LIST_ROWS = 15;

	private final ItemManager itemManager;
	/** Remembers range / categories / page between opens (hidden keys). */
	private final ProfitLossCalculatorConfig config;
	private final Runnable onRefresh;

	private DashboardStats.Input input = new DashboardStats.Input(
		Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	private Map<Integer, String> names = Collections.emptyMap();
	private final Map<Integer, AsyncBufferedImage> icons = new HashMap<>();
	private DashboardStats.Range range = DashboardStats.Range.WEEK;
	private final Set<DashboardStats.Category> categories = EnumSet.allOf(DashboardStats.Category.class);

	private final Map<DashboardStats.Range, DashboardTheme.Chip> rangeChips = new EnumMap<>(DashboardStats.Range.class);
	private final Map<Page, DashboardTheme.Tab> tabs = new EnumMap<>(Page.class);
	private final Map<Tile, DashboardTheme.Stat> tiles = new EnumMap<>(Tile.class);
	private final DashboardTheme.Stat lootedStat = new DashboardTheme.Stat("Looted · estimated value");
	private final DashboardTheme.Stat soldStat = new DashboardTheme.Stat("Sold & alched · money received");
	private final CardLayout pages = new CardLayout();
	private final JPanel pageHolder = new JPanel(pages);
	private final JLabel footnote = new JLabel();

	private final DashboardCharts.NetOverTime netChart = new DashboardCharts.NetOverTime();
	private final DashboardCharts.BarList topChart =
		new DashboardCharts.BarList("Top activities by net", 10, "Nothing recorded in this range");
	private final ActivityModel model = new ActivityModel();
	private final DashboardCharts.BarList lootChart =
		new DashboardCharts.BarList("Loot by estimated value", LIST_ROWS, "No loot in this range");
	private final DashboardCharts.BarList driftChart =
		new DashboardCharts.BarList("Price drift on sold loot", LIST_ROWS, "Nothing sold or alched in this range");
	private final DashboardCharts.StackedBar costTypeChart =
		new DashboardCharts.StackedBar("Cost by type", "No costs in this range");
	private final DashboardCharts.BarList costActivityChart =
		new DashboardCharts.BarList("Cost by activity", LIST_ROWS, "No costs in this range");
	private final DashboardCharts.BarList costItemChart =
		new DashboardCharts.BarList("Biggest costs", LIST_ROWS, "No supplies, spells or teleports in this range");

	private enum Tile
	{
		NET("Net"), PER_HOUR("Best gp / hr"), TIME("Time tracked"), KILLS("Kills"), COST("Cost");

		private final String label;

		Tile(String label)
		{
			this.label = label;
		}
	}

	private enum Page
	{
		OVERVIEW("Overview", "gp / hr only counts runs where that activity was the only one - mixed sessions have "
			+ "no per-activity time yet. Best gp / hr needs " + (BEST_MIN_SEC / 60) + "+ min of timed runs."),
		LOOT("Loot", "Looted value is an estimate - the GE price of what you picked up, frozen at each run's end. "
			+ "Price drift compares what sold or alched loot actually brought in (after GE tax) with that estimate."),
		COSTS("Costs", "From the session logs. Ammo and weapon charges aren't logged one by one - they show as "
			+ "\"Ammo, charges & other\" when every category is included.");

		private final String label;
		private final String note;

		Page(String label, String note)
		{
			this.label = label;
			this.note = note;
		}
	}

	/**
	 * @param client the RuneLite window - only used to place this one over it on first open; the
	 *               dashboard is its own top-level frame (own taskbar entry, can sit behind the
	 *               game), not a dialog owned by the client
	 */
	DashboardWindow(Window client, ItemManager itemManager, ProfitLossCalculatorConfig config, Runnable onRefresh)
	{
		super("Profit Loss Dashboard");
		this.itemManager = itemManager;
		this.config = config;
		this.onRefresh = onRefresh;
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		setIconImage(ImageUtil.loadImageResource(ProfitLossCalculatorPlugin.class, "panel_icon.png"));

		range = parse(DashboardStats.Range.class, config.dashboardRange(), DashboardStats.Range.WEEK);
		categories.clear();
		for (String c : config.dashboardCategories().split(","))
		{
			final DashboardStats.Category cat = parse(DashboardStats.Category.class, c.trim(), null);
			if (cat != null)
			{
				categories.add(cat);
			}
		}
		final Page page = parse(Page.class, config.dashboardPage(), Page.OVERVIEW);

		final JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBackground(DashboardTheme.BG);
		root.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

		final JPanel top = new JPanel(new BorderLayout(0, 12));
		top.setOpaque(false);
		top.add(tabRow(), BorderLayout.NORTH);
		final JPanel filtersAndTiles = new JPanel(new BorderLayout(0, 12));
		filtersAndTiles.setOpaque(false);
		filtersAndTiles.add(filterBar(), BorderLayout.NORTH);
		filtersAndTiles.add(tileRow(), BorderLayout.CENTER);
		top.add(filtersAndTiles, BorderLayout.CENTER);
		root.add(top, BorderLayout.NORTH);

		pageHolder.setOpaque(false);
		pageHolder.add(overviewPage(), Page.OVERVIEW.name());
		pageHolder.add(pair(column(lootedStat, lootChart), column(soldStat, driftChart)), Page.LOOT.name());
		pageHolder.add(column(costTypeChart, pair(costActivityChart, costItemChart)), Page.COSTS.name());
		root.add(pageHolder, BorderLayout.CENTER);

		footnote.setFont(DashboardTheme.font(Font.PLAIN, 11.5f));
		footnote.setForeground(DashboardTheme.MUTED);
		root.add(footnote, BorderLayout.SOUTH);

		setContentPane(root);
		setMinimumSize(new Dimension(860, 640));
		setSize(1120, 880);
		setLocationRelativeTo(client);
		showPage(page);
		recompute();
	}

	/** Bring the window up - restored if minimised - and to the front. */
	void open()
	{
		if ((getExtendedState() & ICONIFIED) != 0)
		{
			setExtendedState(getExtendedState() & ~ICONIFIED);
		}
		setVisible(true);
		toFront();
		requestFocus();
	}

	/** A saved enum name back to its constant, or {@code fallback} if it's blank or unknown. */
	private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback)
	{
		try
		{
			return name == null || name.isEmpty() ? fallback : Enum.valueOf(type, name);
		}
		catch (IllegalArgumentException ex)
		{
			return fallback;
		}
	}

	private void saveCategories()
	{
		final StringBuilder sb = new StringBuilder();
		for (DashboardStats.Category c : categories)
		{
			sb.append(sb.length() == 0 ? "" : ",").append(c.name());
		}
		config.dashboardCategories(sb.toString());
	}

	/** Fresh data from the history executor, with item names resolved on the client thread. */
	void setData(DashboardStats.Input input, Map<Integer, String> names)
	{
		this.input = input;
		this.names = names == null ? Collections.emptyMap() : names;
		recompute();
	}

	private void recompute()
	{
		final DashboardStats.Result r = DashboardStats.compute(input, range, categories, Instant.now(), ZoneId.systemDefault());

		setTile(Tile.NET, PanelUi.sign(r.getNet()), signColor(r.getNet()),
			PanelUi.gpPlain(r.getGained()) + " gained");
		final DashboardStats.Activity best = r.bestPerHour(BEST_MIN_SEC);
		if (best == null)
		{
			setTile(Tile.PER_HOUR, "—", DashboardTheme.MUTED,
				"No profitable activity with " + (BEST_MIN_SEC / 60) + "+ min timed");
		}
		else
		{
			setTile(Tile.PER_HOUR, PanelUi.sign(best.gpPerHour()), signColor(best.gpPerHour()), best.getName());
		}
		setTile(Tile.TIME, PanelUi.secs(r.getTrackedSec()), DashboardTheme.TEXT,
			r.getRuns() + (r.getRuns() == 1 ? " run" : " runs"));
		setTile(Tile.KILLS, QuantityFormatter.formatNumber(r.getKills()), DashboardTheme.TEXT,
			r.getKills() > 0 ? PanelUi.sign(r.getNet() / r.getKills()) + " per kill" : " ");
		setTile(Tile.COST, PanelUi.gpPlain(r.getCost()), DashboardTheme.LOSS,
			r.getGained() > 0 ? (r.getCost() * 100 / r.getGained()) + "% of loot value" : " ");

		netChart.setData(r.getBuckets());
		topChart.setData(activityBars(r.getActivities()), null);
		model.setRows(r.getActivities());
		lootBars(r.getLoot());
		driftBars(r.getDrift());
		costBars(r.getCosts());
		costActivityBars(r.getActivities());
	}

	/** Run cost per activity, from history - the session logs can't say which mob a cost was for. */
	private void costActivityBars(List<DashboardStats.Activity> activities)
	{
		final List<DashboardStats.Activity> sorted = new ArrayList<>();
		long total = 0;
		for (DashboardStats.Activity a : activities)
		{
			if (a.getCost() > 0)
			{
				sorted.add(a);
				total += a.getCost();
			}
		}
		sorted.sort((a, b) -> Long.compare(b.getCost(), a.getCost()));
		final List<DashboardCharts.Bar> bars = new ArrayList<>();
		for (DashboardStats.Activity a : sorted)
		{
			final String name = a.getName().isEmpty() ? DashboardStats.Category.NOT_IN_COMBAT.getLabel() : a.getName();
			bars.add(new DashboardCharts.Bar(name, a.getCost(), PanelUi.gpPlain(a.getCost()), DashboardTheme.LOSS,
				"<b>" + PanelUi.escape(name) + "</b>"
					+ "<br>Cost " + PanelUi.gp(a.getCost()) + " · " + pct(a.getCost(), total) + " of cost"
					+ "<br>" + a.getRuns() + (a.getRuns() == 1 ? " run" : " runs")
					+ (a.getKills() > 0 ? " · " + PanelUi.gp(a.getCost() / a.getKills()) + " per kill" : "")
					+ "<br>Gained " + PanelUi.gp(a.getGained()) + " · net " + PanelUi.sign(a.getNet()),
				null));
		}
		costActivityChart.setData(bars, total > 0 ? "Total " + PanelUi.gpPlain(total) : null);
	}

	// ------------------------------------------------------------------ chart rows

	private List<DashboardCharts.Bar> activityBars(List<DashboardStats.Activity> activities)
	{
		final List<DashboardStats.Activity> sorted = new ArrayList<>(activities);
		// biggest magnitude first, so a heavy loss still shows next to the best earners
		sorted.sort((a, b) -> Long.compare(Math.abs(b.getNet()), Math.abs(a.getNet())));
		final List<DashboardCharts.Bar> bars = new ArrayList<>();
		for (DashboardStats.Activity a : sorted)
		{
			if (a.getNet() == 0)
			{
				continue; // kills with no loot or cost - a bar of nothing (still listed in the table)
			}
			final String name = a.getName().isEmpty() ? DashboardStats.Category.NOT_IN_COMBAT.getLabel() : a.getName();
			bars.add(new DashboardCharts.Bar(name, a.getNet(), PanelUi.sign(a.getNet()), signColor(a.getNet()),
				"<b>" + PanelUi.escape(name) + "</b>"
					+ "<br>Gained " + PanelUi.gp(a.getGained())
					+ "<br>Cost " + PanelUi.gp(a.getCost())
					+ (a.getKills() > 0 ? "<br>" + a.getKills() + " kills · " + PanelUi.sign(a.gpPerKill()) + " each" : "")
					+ (a.hasRate() ? "<br>" + PanelUi.sign(a.gpPerHour()) + " gp/hr" : ""),
				null));
		}
		return bars;
	}

	private void lootBars(List<DashboardStats.ItemTotal> loot)
	{
		long total = 0;
		long coins = 0;
		long units = 0;
		for (DashboardStats.ItemTotal it : loot)
		{
			total += it.getGp();
			if (isCoins(it.getItemId()))
			{
				coins += it.getGp();
			}
			else
			{
				units += it.getQty();
			}
		}
		lootedStat.set(total > 0 ? "≈ " + PanelUi.gpPlain(total) : "—", DashboardTheme.TEXT, total > 0
			? QuantityFormatter.formatNumber(units) + " items" + (coins > 0 ? " + " + PanelUi.gpPlain(coins) + " coins" : "")
				+ "  ·  GE value when looted, not money in hand"
			: "No loot in this range");

		final List<DashboardCharts.Bar> bars = new ArrayList<>();
		for (DashboardStats.ItemTotal it : loot)
		{
			final String name = name(it.getItemId());
			bars.add(new DashboardCharts.Bar(name, it.getGp(), PanelUi.gpPlain(it.getGp()), DashboardTheme.GAIN,
				"<b>" + PanelUi.escape(name) + "</b>"
					+ "<br>Looted ×" + QuantityFormatter.formatNumber(it.getQty())
					+ "<br>Estimated " + PanelUi.gp(it.getGp()) + " (" + PanelUi.gp(it.getGp() / Math.max(1, it.getQty()))
					+ " each, GE value at run end)"
					+ (total > 0 ? "<br>" + pct(it.getGp(), total) + " of estimated loot value" : ""),
				icon(it.getItemId(), lootChart)));
		}
		lootChart.setData(bars, null);
	}

	private static boolean isCoins(int itemId)
	{
		return itemId == net.runelite.api.gameval.ItemID.COINS || itemId == net.runelite.api.gameval.ItemID.PLATINUM;
	}

	private void driftBars(List<DashboardStats.Drift> drift)
	{
		long net = 0;
		long projected = 0;
		long tax = 0;
		long units = 0;
		final List<DashboardCharts.Bar> bars = new ArrayList<>();
		for (DashboardStats.Drift d : drift)
		{
			net += d.getNet();
			projected += d.getProjected();
			tax += d.getTax();
			units += d.getQty();
			final String name = name(d.getItemId());
			bars.add(new DashboardCharts.Bar(name, d.drift(), PanelUi.sign(d.drift()), signColor(d.drift()),
				"<b>" + PanelUi.escape(name) + "</b>"
					+ "<br>Sold ×" + QuantityFormatter.formatNumber(d.getQty()) + " for " + PanelUi.gp(d.getNet())
					+ (d.getTax() > 0 ? " (after " + PanelUi.gpPlain(d.getTax()) + " tax)" : "")
					+ "<br>Valued at " + PanelUi.gp(d.getProjected()) + " when looted"
					+ "<br>Drift " + PanelUi.sign(d.drift())
					+ (d.getProjected() > 0 ? " (" + signedPct(d.drift(), d.getProjected()) + ")" : ""),
				icon(d.getItemId(), driftChart)));
		}
		soldStat.set(drift.isEmpty() ? "—" : PanelUi.gpPlain(net), drift.isEmpty() ? DashboardTheme.MUTED : DashboardTheme.GAIN,
			drift.isEmpty() ? "Nothing sold or alched in this range"
				: QuantityFormatter.formatNumber(units) + " items"
					+ (tax > 0 ? "  ·  after " + PanelUi.gpPlain(tax) + " GE tax" : "")
					+ "  ·  valued at " + PanelUi.gpPlain(projected) + " when looted");
		driftChart.setData(bars, drift.isEmpty() ? null : "Overall " + PanelUi.sign(net - projected)
			+ (projected > 0 ? " (" + signedPct(net - projected, projected) + ")" : ""));
	}

	private void costBars(DashboardStats.Costs costs)
	{
		final long total = costs.getTypes().stream().mapToLong(DashboardStats.TypeTotal::getGp).sum();
		final List<DashboardCharts.Bar> types = new ArrayList<>();
		for (DashboardStats.TypeTotal t : costs.getTypes())
		{
			types.add(new DashboardCharts.Bar(t.getType().getLabel(), t.getGp(), PanelUi.gpPlain(t.getGp()),
				costColor(t.getType()),
				"<b>" + t.getType().getLabel() + "</b><br>" + PanelUi.gp(t.getGp())
					+ (total > 0 ? " · " + pct(t.getGp(), total) + " of cost" : ""),
				null));
		}
		costTypeChart.setData(types, total > 0 ? "Total " + PanelUi.gpPlain(total) : null);

		final List<DashboardCharts.Bar> items = new ArrayList<>();
		for (DashboardStats.CostItem c : costs.getItems())
		{
			// deaths are one line in the type bar already; here they'd just bury the supplies
			if (c.getType() == DashboardStats.CostType.DEATHS)
			{
				continue;
			}
			items.add(new DashboardCharts.Bar(c.getLabel(), c.getGp(), PanelUi.gpPlain(c.getGp()),
				costColor(c.getType()),
				"<b>" + PanelUi.escape(c.getLabel()) + "</b><br>" + c.getType().getLabel()
					+ "<br>×" + QuantityFormatter.formatNumber(c.getCount())
					+ " · " + PanelUi.gp(c.getGp() / Math.max(1, c.getCount())) + " each",
				null));
		}
		costItemChart.setData(items, null);
	}

	private static Color costColor(DashboardStats.CostType t)
	{
		switch (t)
		{
			case SUPPLIES:
				return PanelUi.SUPPLIES_COLOR;
			case SPELLS:
				return PanelUi.SPELL_COLOR;
			case TELEPORTS:
				return PanelUi.TELEPORT_COLOR;
			case DEATHS:
				return DashboardTheme.LOSS;
			case SKILLING:
				return PanelUi.SKILLING_COLOR;
			default:
				return PanelUi.AMMO_COLOR;
		}
	}

	private String name(int itemId)
	{
		return names.getOrDefault(itemId, "Item " + itemId);
	}

	/** The item's sprite, requested once and repainting {@code chart} when it finishes loading. */
	private Image icon(int itemId, Component chart)
	{
		if (itemManager == null)
		{
			return null;
		}
		final Function<Integer, AsyncBufferedImage> load = id ->
		{
			final AsyncBufferedImage img = itemManager.getImage(id);
			img.onLoaded(() ->
			{
				lootChart.repaint();
				driftChart.repaint();
			});
			return img;
		};
		return icons.computeIfAbsent(itemId, load);
	}

	private static String pct(long part, long whole)
	{
		return (part * 100 / whole) + "%";
	}

	private static String signedPct(long part, long whole)
	{
		final long p = part * 100 / whole;
		return (p >= 0 ? "+" : "") + p + "%";
	}

	private static Color signColor(long v)
	{
		return v >= 0 ? DashboardTheme.GAIN : DashboardTheme.LOSS;
	}

	// ------------------------------------------------------------------ layout

	private JPanel tabRow()
	{
		final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		for (Page p : Page.values())
		{
			final DashboardTheme.Tab tab = new DashboardTheme.Tab(p.label, false, () ->
			{
				showPage(p);
				config.dashboardPage(p.name());
			});
			tabs.put(p, tab);
			row.add(tab);
			row.add(gap(24));
		}
		return row;
	}

	private void showPage(Page p)
	{
		pages.show(pageHolder, p.name());
		tabs.forEach((k, t) -> t.setOn(k == p));
		footnote.setText(p.note);
	}

	private JPanel overviewPage()
	{
		final JPanel page = new JPanel(new BorderLayout(0, 12));
		page.setOpaque(false);
		page.add(pair(netChart, topChart), BorderLayout.NORTH);
		page.add(table(), BorderLayout.CENTER);
		return page;
	}

	/** A stat card stacked over a chart that fills the rest of the height. */
	private static JPanel column(Component head, Component body)
	{
		final JPanel p = new JPanel(new BorderLayout(0, 12));
		p.setOpaque(false);
		p.add(head, BorderLayout.NORTH);
		p.add(body, BorderLayout.CENTER);
		return p;
	}

	private static JPanel pair(Component left, Component right)
	{
		final JPanel p = new JPanel(new GridLayout(1, 2, 12, 0));
		p.setOpaque(false);
		p.add(left);
		p.add(right);
		return p;
	}

	private JPanel filterBar()
	{
		final JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);

		final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		left.add(caption("Range"));
		for (DashboardStats.Range r : DashboardStats.Range.values())
		{
			final DashboardTheme.Chip chip = new DashboardTheme.Chip(r.getLabel(), r == range, () ->
			{
				range = r;
				config.dashboardRange(r.name());
				rangeChips.forEach((k, c) -> c.setOn(k == r));
				recompute();
			});
			rangeChips.put(r, chip);
			left.add(chip);
		}
		left.add(gap(18));
		left.add(caption("Include"));
		for (DashboardStats.Category c : DashboardStats.Category.values())
		{
			final DashboardTheme.Chip[] self = new DashboardTheme.Chip[1];
			self[0] = new DashboardTheme.Chip(c.getLabel(), categories.contains(c), () ->
			{
				self[0].setOn(!self[0].isOn());
				if (self[0].isOn())
				{
					categories.add(c);
				}
				else
				{
					categories.remove(c);
				}
				saveCategories();
				recompute();
			});
			left.add(self[0]);
		}
		bar.add(left, BorderLayout.CENTER);
		bar.add(new DashboardTheme.Chip("Refresh", false, onRefresh), BorderLayout.EAST);
		return bar;
	}

	private JPanel tileRow()
	{
		final JPanel row = new JPanel(new GridLayout(1, Tile.values().length, 12, 0));
		row.setOpaque(false);
		for (Tile t : Tile.values())
		{
			final DashboardTheme.Stat tile = new DashboardTheme.Stat(t.label);
			tiles.put(t, tile);
			row.add(tile);
		}
		return row;
	}

	private JPanel table()
	{
		final JTable t = new JTable(model);
		t.setFillsViewportHeight(true);
		t.setRowHeight(30);
		t.setShowGrid(false);
		t.setIntercellSpacing(new Dimension(0, 0));
		t.setFont(DashboardTheme.font(Font.PLAIN, 13f));
		t.setBackground(DashboardTheme.CARD);
		t.setForeground(DashboardTheme.TEXT);
		t.setSelectionBackground(DashboardTheme.HOVER);
		t.setSelectionForeground(DashboardTheme.TEXT);
		t.setBorder(BorderFactory.createEmptyBorder());

		final JTableHeader header = t.getTableHeader();
		header.setReorderingAllowed(false);
		header.setBackground(DashboardTheme.CARD);
		header.setDefaultRenderer(new HeaderRenderer());
		header.setPreferredSize(new Dimension(0, 34));

		final TableRowSorter<ActivityModel> sorter = new TableRowSorter<>(model);
		t.setRowSorter(sorter);
		sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(ActivityModel.NET, SortOrder.DESCENDING)));

		t.setDefaultRenderer(String.class, new CellRenderer());
		t.setDefaultRenderer(Long.class, new CellRenderer());
		t.getColumnModel().getColumn(0).setPreferredWidth(220);
		t.getColumnModel().getColumn(1).setPreferredWidth(130);

		final JScrollPane sp = new JScrollPane(t);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.setOpaque(false);
		sp.getViewport().setBackground(DashboardTheme.CARD);
		sp.getVerticalScrollBar().setUI(new DashboardTheme.SlimScrollBarUI());
		sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
		sp.getVerticalScrollBar().setOpaque(false);
		sp.getVerticalScrollBar().setUnitIncrement(30);
		sp.getHorizontalScrollBar().setUI(new DashboardTheme.SlimScrollBarUI());
		sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
		sp.getHorizontalScrollBar().setOpaque(false);

		final DashboardTheme.Card card = new DashboardTheme.Card(new BorderLayout());
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		card.add(sp, BorderLayout.CENTER);
		return card;
	}

	private void setTile(Tile t, String value, Color color, String sub)
	{
		tiles.get(t).set(value, color, sub);
	}

	private static JLabel caption(String text)
	{
		final JLabel l = new JLabel(text.toUpperCase());
		l.setFont(DashboardTheme.caps(10.5f));
		l.setForeground(DashboardTheme.MUTED);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		return l;
	}

	private static Component gap(int w)
	{
		final JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(w, 1));
		return l;
	}

	// ------------------------------------------------------------------ table

	/** One row per activity; numeric cells are Longs so the sorter orders them numerically. */
	private static final class ActivityModel extends AbstractTableModel
	{
		static final String[] COLUMNS = {"Activity", "Type", "Runs", "Kills", "Gained", "Cost", "Net",
			"gp / kill", "gp / hr", "Timed"};
		static final int NET = 6;
		static final int TIMED = 9;

		private List<DashboardStats.Activity> rows = new ArrayList<>();

		void setRows(List<DashboardStats.Activity> rows)
		{
			this.rows = new ArrayList<>(rows);
			fireTableDataChanged();
		}

		@Override
		public int getRowCount()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount()
		{
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int c)
		{
			return COLUMNS[c];
		}

		@Override
		public Class<?> getColumnClass(int c)
		{
			return c < 2 ? String.class : Long.class;
		}

		@Override
		public Object getValueAt(int r, int c)
		{
			final DashboardStats.Activity a = rows.get(r);
			switch (c)
			{
				case 0:
					return a.getName().isEmpty() ? DashboardStats.Category.NOT_IN_COMBAT.getLabel() : a.getName();
				case 1:
					return a.getCategory().getLabel();
				case 2:
					return (long) a.getRuns();
				case 3:
					return (long) a.getKills();
				case 4:
					return a.getGained();
				case 5:
					return a.getCost();
				case NET:
					return a.getNet();
				case 7:
					return a.getKills() > 0 ? a.gpPerKill() : null;
				case 8:
					return a.hasRate() ? a.gpPerHour() : null;
				case TIMED:
					return a.hasRate() ? a.getTimedSec() : null;
				default:
					return null;
			}
		}
	}

	/** Small muted caps over a hairline, aligned to match the column's cells. */
	private static final class HeaderRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
			boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(table, value, false, false, row, col);
			final int c = table.convertColumnIndexToModel(col);
			String text = value == null ? "" : value.toString().toUpperCase();
			final List<? extends RowSorter.SortKey> keys = table.getRowSorter() == null
				? Collections.emptyList() : table.getRowSorter().getSortKeys();
			if (!keys.isEmpty() && keys.get(0).getColumn() == c)
			{
				text += keys.get(0).getSortOrder() == SortOrder.ASCENDING ? "  ↑" : "  ↓";
			}
			setText(text);
			setFont(DashboardTheme.caps(10.5f));
			setForeground(DashboardTheme.MUTED);
			setBackground(DashboardTheme.CARD);
			setHorizontalAlignment(c < 2 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, DashboardTheme.BORDER),
				BorderFactory.createEmptyBorder(0, 12, 0, 12)));
			return this;
		}
	}

	/** Padded, zebra-striped cells: gp right-aligned, Net / rate columns coloured by sign, Timed
	 *  as a duration. */
	private static final class CellRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
			boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(table, value, selected, false, row, col);
			final int c = table.convertColumnIndexToModel(col);
			setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
			setBackground(selected ? DashboardTheme.HOVER : row % 2 == 0 ? DashboardTheme.CARD : DashboardTheme.CARD_ALT);
			setHorizontalAlignment(c < 2 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			setForeground(c == 1 ? DashboardTheme.MUTED : DashboardTheme.TEXT);
			if (c < 2)
			{
				return this;
			}
			if (value == null)
			{
				setText("—");
				setForeground(DashboardTheme.alpha(DashboardTheme.MUTED, 140));
				return this;
			}
			final long v = (Long) value;
			if (c == ActivityModel.TIMED)
			{
				setText(PanelUi.secs(v));
			}
			else if (c >= ActivityModel.NET)
			{
				setText(PanelUi.sign(v));
				setForeground(v > 0 ? DashboardTheme.GAIN : v < 0 ? DashboardTheme.LOSS : DashboardTheme.MUTED);
			}
			else
			{
				setText(QuantityFormatter.formatNumber(v));
			}
			return this;
		}
	}
}
