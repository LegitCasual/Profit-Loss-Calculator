/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The {@link DashboardWindow}'s own look - a softer dark palette and the system UI font rather
 * than the sidebar's RuneScape pixel font - plus the two custom-painted pieces it is built from:
 * rounded {@link Card}s and pill-shaped {@link Chip} toggles.
 */
final class DashboardTheme
{
	static final Color BG = new Color(0x16, 0x18, 0x1d);
	static final Color CARD = new Color(0x1f, 0x22, 0x29);
	static final Color CARD_ALT = new Color(0x23, 0x26, 0x2e);
	static final Color BORDER = new Color(0x2c, 0x30, 0x39);
	static final Color HOVER = new Color(0x2a, 0x2e, 0x37);
	static final Color TEXT = new Color(0xe6, 0xe8, 0xec);
	static final Color MUTED = new Color(0x8b, 0x91, 0x9c);
	static final Color GRID = new Color(0x2c, 0x30, 0x39);
	static final Color GAIN = new Color(0x5c, 0xc9, 0x8a);
	static final Color LOSS = new Color(0xec, 0x7a, 0x72);
	static final Color ACCENT = new Color(0xf0, 0xa0, 0x4b);

	static final int RADIUS = 12;

	/** First installed of these wins; the logical SansSerif font is the last resort. */
	private static final List<String> FAMILIES = Arrays.asList(
		"Inter", "Segoe UI", "SF Pro Text", ".AppleSystemUIFont", "Helvetica Neue", "Noto Sans", "Ubuntu",
		"DejaVu Sans");
	private static final String FAMILY = pickFamily();

	private DashboardTheme()
	{
	}

	private static String pickFamily()
	{
		try
		{
			final Set<String> installed = new HashSet<>(Arrays.asList(
				GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
			for (String f : FAMILIES)
			{
				if (installed.contains(f))
				{
					return f;
				}
			}
		}
		catch (RuntimeException ex)
		{
			// headless or a broken font config - fall through
		}
		return Font.SANS_SERIF;
	}

	static Font font(int style, float size)
	{
		return new Font(FAMILY, style, 1).deriveFont(size);
	}

	/** Small, letter-spaced caps for labels and column headers. */
	static Font caps(float size)
	{
		return font(Font.BOLD, size).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.06f));
	}

	static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	static Graphics2D smooth(Graphics g0)
	{
		final Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}

	/** Paint a rounded card background with a hairline border over {@code c}'s bounds. */
	static void paintCard(Graphics g0, JComponent c)
	{
		final Graphics2D g = smooth(g0);
		try
		{
			final RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f,
				c.getWidth() - 1f, c.getHeight() - 1f, RADIUS, RADIUS);
			g.setColor(CARD);
			g.fill(r);
			g.setColor(BORDER);
			g.setStroke(new BasicStroke(1f));
			g.draw(r);
		}
		finally
		{
			g.dispose();
		}
	}

	/** A rounded, bordered container. */
	static class Card extends JPanel
	{
		Card(LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			paintCard(g, this);
		}
	}

	/** A pill-shaped toggle / button: filled with the accent when on, outlined when off. */
	static final class Chip extends JComponent
	{
		private final String text;
		private boolean on;
		private boolean hover;

		Chip(String text, boolean on, Runnable onClick)
		{
			this.text = text;
			this.on = on;
			setFont(font(Font.PLAIN, 13f));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		void setOn(boolean on)
		{
			this.on = on;
			repaint();
		}

		boolean isOn()
		{
			return on;
		}

		@Override
		public Dimension getPreferredSize()
		{
			final FontMetrics fm = getFontMetrics(getFont());
			return new Dimension(fm.stringWidth(text) + 28, 30);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			final Graphics2D g = smooth(g0);
			try
			{
				final float h = getHeight() - 1f;
				final RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, h, h, h);
				g.setColor(on ? alpha(ACCENT, 46) : hover ? HOVER : CARD);
				g.fill(r);
				g.setColor(on ? alpha(ACCENT, 170) : BORDER);
				g.draw(r);
				g.setFont(getFont());
				final FontMetrics fm = g.getFontMetrics();
				g.setColor(on ? ACCENT : hover ? TEXT : MUTED);
				g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2f,
					(getHeight() - fm.getHeight()) / 2f + fm.getAscent());
			}
			finally
			{
				g.dispose();
			}
		}
	}
}
