/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
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
import net.runelite.client.util.QuantityFormatter;

/**
 * The History dashboard: a separate, non-modal window over the client with a date-range and
 * category filter, headline tiles, two charts and a sortable per-activity table. Fed a copy of
 * every {@link RunRecord} via {@link #setRuns}; all filtering is {@link DashboardStats}, all
 * styling {@link DashboardTheme}.
 *
 * <p>EDT only.
 */
class DashboardWindow extends JDialog
{
	/** An activity needs this much timed play before it can be crowned best gp/hr. */
	private static final long BEST_MIN_SEC = 30 * 60;

	private final Runnable onRefresh;

	private List<RunRecord> runs = Collections.emptyList();
	private DashboardStats.Range range = DashboardStats.Range.WEEK;
	private final Set<DashboardStats.Category> categories = EnumSet.allOf(DashboardStats.Category.class);

	private final Map<DashboardStats.Range, DashboardTheme.Chip> rangeChips = new EnumMap<>(DashboardStats.Range.class);
	private final Map<Tile, JLabel[]> tiles = new EnumMap<>(Tile.class);
	private final DashboardCharts.NetOverTime netChart = new DashboardCharts.NetOverTime();
	private final DashboardCharts.TopActivities topChart = new DashboardCharts.TopActivities();
	private final ActivityModel model = new ActivityModel();

	private enum Tile
	{
		NET("Net"), PER_HOUR("Best gp / hr"), TIME("Time tracked"), KILLS("Kills"), COST("Cost");

		private final String label;

		Tile(String label)
		{
			this.label = label;
		}
	}

	DashboardWindow(Window owner, Runnable onRefresh)
	{
		super(owner, "Profit Loss Dashboard", ModalityType.MODELESS);
		this.onRefresh = onRefresh;
		setDefaultCloseOperation(HIDE_ON_CLOSE);

		final JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBackground(DashboardTheme.BG);
		root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

		final JPanel top = new JPanel(new BorderLayout(0, 12));
		top.setOpaque(false);
		top.add(filterBar(), BorderLayout.NORTH);
		top.add(tileRow(), BorderLayout.CENTER);
		final JPanel charts = new JPanel(new GridLayout(1, 2, 12, 0));
		charts.setOpaque(false);
		charts.add(netChart);
		charts.add(topChart);
		top.add(charts, BorderLayout.SOUTH);
		root.add(top, BorderLayout.NORTH);

		root.add(table(), BorderLayout.CENTER);

		final JLabel footnote = new JLabel("gp / hr only counts runs where that activity was the only one - "
			+ "mixed sessions have no per-activity time yet. Best gp / hr needs " + (BEST_MIN_SEC / 60)
			+ "+ min of timed runs.");
		footnote.setFont(DashboardTheme.font(Font.PLAIN, 11.5f));
		footnote.setForeground(DashboardTheme.MUTED);
		root.add(footnote, BorderLayout.SOUTH);

		setContentPane(root);
		setMinimumSize(new Dimension(860, 640));
		setSize(1120, 800);
		setLocationRelativeTo(owner);
		recompute();
	}

	void setRuns(List<RunRecord> runs)
	{
		this.runs = runs == null ? Collections.emptyList() : runs;
		recompute();
	}

	private void recompute()
	{
		final DashboardStats.Result r = DashboardStats.compute(runs, range, categories, Instant.now(), ZoneId.systemDefault());

		setTile(Tile.NET, PanelUi.sign(r.getNet()), signColor(r.getNet()),
			PanelUi.gpPlain(r.getGained()) + " gained");
		final DashboardStats.Activity best = r.bestPerHour(BEST_MIN_SEC);
		if (best == null)
		{
			setTile(Tile.PER_HOUR, "—", DashboardTheme.MUTED, "Needs " + (BEST_MIN_SEC / 60) + "+ min on one activity");
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
		topChart.setData(r.getActivities());
		model.setRows(r.getActivities());
	}

	private static Color signColor(long v)
	{
		return v >= 0 ? DashboardTheme.GAIN : DashboardTheme.LOSS;
	}

	// ------------------------------------------------------------------ layout

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
			final DashboardTheme.Card tile = new DashboardTheme.Card(new BorderLayout(0, 4));
			tile.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
			final JLabel name = new JLabel(t.label.toUpperCase());
			name.setFont(DashboardTheme.caps(10.5f));
			name.setForeground(DashboardTheme.MUTED);
			final JLabel value = new JLabel("—");
			value.setFont(DashboardTheme.font(Font.BOLD, 24f));
			final JLabel sub = new JLabel(" ");
			sub.setFont(DashboardTheme.font(Font.PLAIN, 12f));
			sub.setForeground(DashboardTheme.MUTED);
			tile.add(name, BorderLayout.NORTH);
			tile.add(value, BorderLayout.CENTER);
			tile.add(sub, BorderLayout.SOUTH);
			tiles.put(t, new JLabel[]{value, sub});
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

		final JScrollPane sp = new JScrollPane(t);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.setOpaque(false);
		sp.getViewport().setBackground(DashboardTheme.CARD);

		final DashboardTheme.Card card = new DashboardTheme.Card(new BorderLayout());
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		card.add(sp, BorderLayout.CENTER);
		return card;
	}

	private void setTile(Tile t, String value, Color color, String sub)
	{
		final JLabel[] l = tiles.get(t);
		l[0].setText(value);
		l[0].setForeground(color);
		l[1].setText(sub);
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
