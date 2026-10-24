/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Hand-drawn Java2D charts for the {@link DashboardWindow} - no chart library, so nothing extra
 * for the Plugin Hub to review. Styled by {@link DashboardTheme}.
 */
final class DashboardCharts
{
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");
	private static final float[] DASH = {3f, 4f};

	private DashboardCharts()
	{
	}

	/** "1.2M", "-340k", "12" - short enough for an axis label. */
	static String compact(long v)
	{
		final long a = Math.abs(v);
		final String s;
		if (a >= 1_000_000_000L)
		{
			s = trim(a / 1e9) + "B";
		}
		else if (a >= 1_000_000L)
		{
			s = trim(a / 1e6) + "M";
		}
		else if (a >= 1_000L)
		{
			s = trim(a / 1e3) + "k";
		}
		else
		{
			s = Long.toString(a);
		}
		return v < 0 ? "-" + s : s;
	}

	private static String trim(double d)
	{
		return d >= 100 ? String.valueOf(Math.round(d)) : String.format("%.1f", d).replace(".0", "");
	}

	/** Shared chrome: a rounded card, a title, a legend slot, hover tracking and a tooltip. */
	private abstract static class Chart extends JComponent
	{
		private static final int PAD = 16;
		private static final int HEADER = 40;

		private final String title;
		/** index under the mouse, or -1. */
		int hover = -1;

		Chart(String title)
		{
			this.title = title;
			setPreferredSize(new Dimension(420, 250));
			ToolTipManager.sharedInstance().registerComponent(this);
			final MouseAdapter m = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					final int h = hit(e.getX(), e.getY());
					if (h != hover)
					{
						hover = h;
						repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = -1;
					repaint();
				}
			};
			addMouseListener(m);
			addMouseMotionListener(m);
		}

		@Override
		public String getToolTipText(MouseEvent e)
		{
			final int i = hit(e.getX(), e.getY());
			return i < 0 ? null : tip(i);
		}

		/** Index of the bar / row at a point, or -1. */
		abstract int hit(int x, int y);

		abstract String tip(int i);

		abstract void draw(Graphics2D g, int x, int y, int w, int h);

		/** Right-aligned legend in the header; nothing by default. */
		void legend(Graphics2D g, int right, int baseline)
		{
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			DashboardTheme.paintCard(g0, this);
			final Graphics2D g = DashboardTheme.smooth(g0);
			try
			{
				g.setFont(DashboardTheme.font(Font.BOLD, 14f));
				g.setColor(DashboardTheme.TEXT);
				g.drawString(title, PAD, PAD + g.getFontMetrics().getAscent());
				g.setFont(DashboardTheme.font(Font.PLAIN, 11.5f));
				legend(g, getWidth() - PAD, PAD + g.getFontMetrics().getAscent() + 1);
				draw(g, PAD, HEADER + 4, getWidth() - PAD * 2, getHeight() - HEADER - 4 - PAD);
			}
			finally
			{
				g.dispose();
			}
		}

		static void empty(Graphics2D g, int x, int y, int w, int h)
		{
			final String s = "Nothing recorded in this range";
			g.setColor(DashboardTheme.MUTED);
			g.drawString(s, x + (w - g.getFontMetrics().stringWidth(s)) / 2, y + h / 2);
		}

		/** A small legend entry drawn leftwards from {@code right}; returns the new right edge. */
		static int legendItem(Graphics2D g, int right, int baseline, Color swatch, boolean line, String text)
		{
			final FontMetrics fm = g.getFontMetrics();
			final int tx = right - fm.stringWidth(text);
			g.setColor(DashboardTheme.MUTED);
			g.drawString(text, tx, baseline);
			final int sy = baseline - fm.getAscent() / 2 - 1;
			g.setColor(swatch);
			if (line)
			{
				g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawLine(tx - 18, sy, tx - 6, sy);
				return tx - 32;
			}
			g.fill(new RoundRectangle2D.Float(tx - 16, sy - 4, 9, 9, 3, 3));
			return tx - 30;
		}
	}

	/** Net per day (or week) as green / coral bars, with the running total as an accent line
	 *  over a soft gradient. */
	static final class NetOverTime extends Chart
	{
		private List<DashboardStats.Bucket> buckets = Collections.emptyList();
		/** last drawn plot area, for hit-testing. */
		private int plotX;
		private int plotW;

		NetOverTime()
		{
			super("Net over time");
		}

		void setData(List<DashboardStats.Bucket> buckets)
		{
			this.buckets = buckets == null ? Collections.emptyList() : buckets;
			hover = -1;
			repaint();
		}

		@Override
		void legend(Graphics2D g, int right, int baseline)
		{
			final int r = legendItem(g, right, baseline, DashboardTheme.ACCENT, true, "Running total");
			legendItem(g, r, baseline, DashboardTheme.GAIN, false, "Net");
		}

		@Override
		void draw(Graphics2D g, int x, int y, int w, int h)
		{
			if (buckets.isEmpty())
			{
				empty(g, x, y, w, h);
				return;
			}
			final int n = buckets.size();
			final long[] cum = new long[n];
			long run = 0;
			long lo = 0;
			long hi = 0;
			for (int i = 0; i < n; i++)
			{
				final long v = buckets.get(i).getNet();
				run += v;
				cum[i] = run;
				lo = Math.min(lo, Math.min(v, run));
				hi = Math.max(hi, Math.max(v, run));
			}
			if (hi == lo)
			{
				hi = lo + 1;
			}

			final FontMetrics fm = g.getFontMetrics();
			final int axisW = Math.max(fm.stringWidth(compact(hi)), fm.stringWidth(compact(lo))) + 10;
			final int px = x + axisW;
			final int pw = w - axisW;
			final int ph = h - fm.getHeight() - 6;
			plotX = px;
			plotW = pw;
			final int zeroY = yOf(0, lo, hi, y, ph);

			// dashed gridlines at the top / bottom, a solid zero line
			final long[] ticks = lo < 0 && hi > 0 ? new long[]{hi, 0, lo} : new long[]{hi, lo};
			int lastLabelY = y - ph;
			for (long t : ticks)
			{
				final int ty = yOf(t, lo, hi, y, ph);
				g.setColor(DashboardTheme.GRID);
				g.setStroke(t == 0 ? new BasicStroke(1f)
					: new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH, 0f));
				g.drawLine(px, ty, px + pw, ty);
				// a small dip just under zero would print its label on top of zero's - skip it
				if (ty - lastLabelY < fm.getHeight())
				{
					continue;
				}
				lastLabelY = ty;
				g.setColor(DashboardTheme.MUTED);
				final String s = compact(t);
				g.drawString(s, px - 8 - fm.stringWidth(s), ty + fm.getAscent() / 2 - 1);
			}

			final double slot = (double) pw / n;
			final float barW = (float) Math.max(3, Math.min(36, slot * 0.8));
			if (hover >= 0)
			{
				g.setColor(DashboardTheme.alpha(DashboardTheme.TEXT, 14));
				g.fill(new RoundRectangle2D.Double(px + hover * slot, y, slot, ph, 6, 6));
			}
			for (int i = 0; i < n; i++)
			{
				final long v = buckets.get(i).getNet();
				if (v == 0)
				{
					continue;
				}
				final float bx = (float) (px + i * slot + (slot - barW) / 2);
				final int vy = yOf(v, lo, hi, y, ph);
				final Color c = v > 0 ? DashboardTheme.GAIN : DashboardTheme.LOSS;
				g.setColor(hover < 0 || hover == i ? c : DashboardTheme.alpha(c, 120));
				g.fill(new RoundRectangle2D.Float(bx, Math.min(vy, zeroY), barW,
					Math.max(2, Math.abs(vy - zeroY)), 5, 5));
			}

			// running total: gradient area down to the zero line, then the line itself
			final Path2D.Float line = new Path2D.Float();
			for (int i = 0; i < n; i++)
			{
				final float cx = (float) (px + i * slot + slot / 2);
				final float cy = yOf(cum[i], lo, hi, y, ph);
				if (i == 0)
				{
					line.moveTo(cx, cy);
				}
				else
				{
					line.lineTo(cx, cy);
				}
			}
			final Path2D.Float area = new Path2D.Float(line);
			area.lineTo((float) (px + (n - 1) * slot + slot / 2), zeroY);
			area.lineTo((float) (px + slot / 2), zeroY);
			area.closePath();
			g.setPaint(new GradientPaint(0, y, DashboardTheme.alpha(DashboardTheme.ACCENT, 70),
				0, zeroY, DashboardTheme.alpha(DashboardTheme.ACCENT, 0)));
			g.fill(area);
			g.setPaint(DashboardTheme.ACCENT);
			g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(line);
			if (hover >= 0)
			{
				final float cx = (float) (px + hover * slot + slot / 2);
				final float cy = yOf(cum[hover], lo, hi, y, ph);
				g.setColor(DashboardTheme.CARD);
				g.fill(new Ellipse2D.Float(cx - 5, cy - 5, 10, 10));
				g.setColor(DashboardTheme.ACCENT);
				g.draw(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
			}

			// dates: first, middle, last
			g.setColor(DashboardTheme.MUTED);
			final int ly = y + h;
			g.drawString(label(buckets.get(0)), px, ly);
			if (n >= 5)
			{
				final String mid = label(buckets.get(n / 2));
				g.drawString(mid, (int) (px + (n / 2) * slot + slot / 2 - fm.stringWidth(mid) / 2.0), ly);
			}
			final String last = label(buckets.get(n - 1));
			g.drawString(last, px + pw - fm.stringWidth(last), ly);
		}

		@Override
		int hit(int mx, int my)
		{
			if (buckets.isEmpty() || plotW <= 0 || mx < plotX || mx >= plotX + plotW)
			{
				return -1;
			}
			return Math.min(buckets.size() - 1, (int) ((mx - plotX) / ((double) plotW / buckets.size())));
		}

		@Override
		String tip(int i)
		{
			long cum = 0;
			for (int k = 0; k <= i; k++)
			{
				cum += buckets.get(k).getNet();
			}
			final DashboardStats.Bucket b = buckets.get(i);
			return "<html><b>" + (b.getDays() > 1 ? "Week of " : "") + label(b) + "</b>"
				+ "<br>Net " + PanelUi.sign(b.getNet()) + "<br>Running total " + PanelUi.sign(cum) + "</html>";
		}

		private static String label(DashboardStats.Bucket b)
		{
			return DAY.format(b.getStart());
		}

		private static int yOf(long v, long lo, long hi, int y, int h)
		{
			return y + (int) ((double) (hi - v) / (hi - lo) * h);
		}
	}

	/** One full-width bar split into proportional segments, with a legend of value and share
	 *  under it. Segments are {@link Bar}s; only label / value / color / tip are used. */
	static final class StackedBar extends Chart
	{
		private static final float BAR_H = 16f;

		private final String emptyText;
		private List<Bar> segments = Collections.emptyList();
		private String note;
		/** last drawn segment x-ranges ({@code [start, end)}) and bar row, for hit-testing. */
		private int[] edges = new int[0];
		private int barY;

		StackedBar(String title, String emptyText)
		{
			super(title);
			this.emptyText = emptyText;
			setPreferredSize(new Dimension(420, 108));
		}

		void setData(List<Bar> segments, String note)
		{
			this.segments = segments == null ? Collections.emptyList() : segments;
			this.note = note;
			hover = -1;
			repaint();
		}

		@Override
		void legend(Graphics2D g, int right, int baseline)
		{
			if (note != null)
			{
				g.setColor(DashboardTheme.MUTED);
				g.drawString(note, right - g.getFontMetrics().stringWidth(note), baseline);
			}
		}

		@Override
		void draw(Graphics2D g, int x, int y, int w, int h)
		{
			final long total = segments.stream().mapToLong(b -> Math.max(0, b.value)).sum();
			if (total <= 0)
			{
				g.setColor(DashboardTheme.MUTED);
				g.drawString(emptyText, x, y + g.getFontMetrics().getAscent());
				return;
			}
			barY = y;
			edges = new int[segments.size() + 1];
			edges[0] = x;
			// each segment is filled as (its rectangle ∩ the rounded bar) rather than clipped -
			// Java2D doesn't antialias clip edges, which left the rounded ends jagged
			final Area pill = new Area(new RoundRectangle2D.Float(x, y, w, BAR_H, BAR_H, BAR_H));
			long run = 0;
			for (int i = 0; i < segments.size(); i++)
			{
				final Bar b = segments.get(i);
				run += Math.max(0, b.value);
				edges[i + 1] = x + (int) Math.round((double) run / total * w);
				// a 2px gap between segments
				final int sx = edges[i] + (i > 0 ? 1 : 0);
				final Area seg = new Area(new java.awt.Rectangle(sx, y - 1,
					Math.max(0, edges[i + 1] - sx - (i + 1 < segments.size() ? 1 : 0)), (int) BAR_H + 2));
				seg.intersect(pill);
				g.setColor(hover < 0 || hover == i ? b.color : DashboardTheme.alpha(b.color, 120));
				g.fill(seg);
			}

			// legend: swatch, label, value and share, flowing left to right
			final FontMetrics fm = g.getFontMetrics();
			int lx = x;
			int ly = y + (int) BAR_H + 10 + fm.getAscent();
			for (int i = 0; i < segments.size(); i++)
			{
				final Bar b = segments.get(i);
				final String head = b.label + "  ";
				final String tail = b.valueText + "  " + (Math.max(0, b.value) * 100 / total) + "%";
				final int itemW = 14 + fm.stringWidth(head) + fm.stringWidth(tail) + 22;
				if (lx > x && lx + itemW > x + w)
				{
					lx = x;
					ly += fm.getHeight() + 4;
				}
				g.setColor(b.color);
				g.fill(new RoundRectangle2D.Float(lx, ly - fm.getAscent() / 2f - 5, 9, 9, 3, 3));
				g.setColor(i == hover ? DashboardTheme.TEXT : DashboardTheme.MUTED);
				g.drawString(head, lx + 14, ly);
				g.setColor(DashboardTheme.TEXT);
				g.drawString(tail, lx + 14 + fm.stringWidth(head), ly);
				lx += itemW;
			}
		}

		@Override
		int hit(int mx, int my)
		{
			if (edges.length < 2 || my < barY - 4 || my > barY + BAR_H + 4)
			{
				return -1;
			}
			for (int i = 0; i + 1 < edges.length; i++)
			{
				if (mx >= edges[i] && mx < edges[i + 1])
				{
					return i;
				}
			}
			return -1;
		}

		@Override
		String tip(int i)
		{
			final Bar b = segments.get(i);
			return b.tip == null ? null : "<html>" + b.tip + "</html>";
		}
	}

	/** One row of a {@link BarList}. */
	static final class Bar
	{
		final String label;
		final long value;
		final String valueText;
		final Color color;
		/** tooltip html body (no {@code <html>} wrapper). */
		final String tip;
		/** optional item icon drawn before the label. */
		final Image icon;

		Bar(String label, long value, String valueText, Color color, String tip, Image icon)
		{
			this.label = label;
			this.value = value;
			this.valueText = valueText;
			this.color = color;
			this.tip = tip;
			this.icon = icon;
		}
	}

	/** Labelled horizontal bars, diverging from a zero line when any value is negative, with an
	 *  optional icon per row and a short note in the header. */
	static final class BarList extends Chart
	{
		private final int maxRows;
		private final String emptyText;
		private List<Bar> rows = Collections.emptyList();
		private String note;
		private int rowTop;
		private int rowH;

		BarList(String title, int maxRows, String emptyText)
		{
			super(title);
			this.maxRows = maxRows;
			this.emptyText = emptyText;
		}

		void setData(List<Bar> bars, String note)
		{
			rows = bars == null ? Collections.emptyList() : bars.subList(0, Math.min(maxRows, bars.size()));
			this.note = note;
			hover = -1;
			repaint();
		}

		@Override
		void legend(Graphics2D g, int right, int baseline)
		{
			if (note != null)
			{
				g.setColor(DashboardTheme.MUTED);
				g.drawString(note, right - g.getFontMetrics().stringWidth(note), baseline);
			}
		}

		@Override
		void draw(Graphics2D g, int x, int y, int w, int h)
		{
			if (rows.isEmpty())
			{
				g.setColor(DashboardTheme.MUTED);
				g.drawString(emptyText, x + (w - g.getFontMetrics().stringWidth(emptyText)) / 2, y + h / 2);
				return;
			}
			g.setFont(DashboardTheme.font(Font.PLAIN, 12.5f));
			final FontMetrics fm = g.getFontMetrics();
			rowTop = y;
			rowH = Math.min(28, h / rows.size());
			final boolean icons = rows.stream().anyMatch(b -> b.icon != null);
			final int iconW = icons ? Math.min(30, rowH + 4) : 0;

			int nameW = 0;
			long lo = 0;
			long hi = 0;
			int valW = 0;
			for (Bar b : rows)
			{
				nameW = Math.max(nameW, fm.stringWidth(b.label));
				lo = Math.min(lo, b.value);
				hi = Math.max(hi, b.value);
				valW = Math.max(valW, fm.stringWidth(b.valueText));
			}
			nameW = Math.min(nameW, w / 3) + 14;
			valW += 10;
			final int px = x + iconW + nameW;
			final int pw = Math.max(10, w - iconW - nameW - valW);
			final double span = Math.max(1, hi - lo);
			final int zeroX = px + (int) (-lo / span * pw);
			final float barH = Math.max(6, Math.min(14, rowH - 10));

			for (int i = 0; i < rows.size(); i++)
			{
				final Bar b = rows.get(i);
				final int ry = y + i * rowH;
				if (i == hover)
				{
					g.setColor(DashboardTheme.HOVER);
					g.fill(new RoundRectangle2D.Float(x - 6, ry, w + 12, rowH, 8, 8));
				}
				if (b.icon != null)
				{
					// item sprites are 36x32; scale to the row, keeping the aspect
					final int ih = rowH - 2;
					final int iw = ih * 36 / 32;
					g.drawImage(b.icon, x, ry + 1, iw, ih, null);
				}
				final int textY = ry + (rowH + fm.getAscent() - fm.getDescent()) / 2;
				g.setColor(i == hover ? DashboardTheme.TEXT : DashboardTheme.MUTED);
				g.drawString(clip(b.label, fm, nameW - 14), x + iconW, textY);

				final float len = (float) (Math.abs(b.value) / span * pw);
				final boolean pos = b.value >= 0;
				final float bx = pos ? zeroX : zeroX - len;
				if (len >= 1)
				{
					g.setColor(hover < 0 || hover == i ? b.color : DashboardTheme.alpha(b.color, 120));
					g.fill(new RoundRectangle2D.Float(bx, ry + (rowH - barH) / 2, len, barH, barH, barH));
				}
				g.setColor(b.color);
				// a negative bar's label sits just right of the zero line, clear of its bar
				g.drawString(b.valueText, pos ? bx + len + 6 : zeroX + 6, textY);
			}
			if (lo < 0)
			{
				g.setColor(DashboardTheme.GRID);
				g.setStroke(new BasicStroke(1f));
				g.drawLine(zeroX, y, zeroX, y + rows.size() * rowH);
			}
		}

		@Override
		int hit(int mx, int my)
		{
			if (rows.isEmpty() || rowH <= 0 || my < rowTop)
			{
				return -1;
			}
			final int i = (my - rowTop) / rowH;
			return i < rows.size() ? i : -1;
		}

		@Override
		String tip(int i)
		{
			final Bar b = rows.get(i);
			return b.tip == null ? null : "<html>" + b.tip + "</html>";
		}

		private static String clip(String s, FontMetrics fm, int w)
		{
			if (fm.stringWidth(s) <= w)
			{
				return s;
			}
			String t = s;
			while (t.length() > 1 && fm.stringWidth(t + "…") > w)
			{
				t = t.substring(0, t.length() - 1);
			}
			return t + "…";
		}
	}
}
