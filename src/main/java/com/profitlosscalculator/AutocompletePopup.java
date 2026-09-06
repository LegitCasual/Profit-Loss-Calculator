/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A lightweight "search as you type" suggestion list attached to a plain {@link JTextField} -
 * matches the current text against a static dictionary (case-insensitive, "starts with" ranked
 * ahead of "contains elsewhere"), showing up to {@link #MAX_SUGGESTIONS} results in a small
 * popup under the field. Click a suggestion to fill the field with it.
 *
 * <p>The popup is deliberately non-focusable, so it never steals keyboard focus from the field
 * - typing, Enter-to-submit and everything else about the field's own behaviour is unaffected.
 * It's purely a mouse-driven visual aid.
 *
 * <p>Rows use the full-size RuneScape font with generous padding for an easy click target, and
 * a name too long for the popup width wraps onto a second line rather than being clipped.
 */
final class AutocompletePopup
{
	private static final int MAX_SUGGESTIONS = 8;
	/** Popup never grows past this - beyond it the list scrolls. */
	private static final int MAX_POPUP_HEIGHT = 260;

	private AutocompletePopup()
	{
	}

	static void attach(JTextField field, List<String> dictionary)
	{
		final DefaultListModel<String> model = new DefaultListModel<>();
		final JList<String> list = new JList<>(model);
		list.setFocusable(false);
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		final SuggestionRenderer renderer = new SuggestionRenderer(FontManager.getRunescapeFont());
		list.setCellRenderer(renderer);

		final JScrollPane scroll = new JScrollPane(list,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setFocusable(false);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));

		final JWindow popup = new JWindow();
		popup.setFocusableWindowState(false);
		popup.setLayout(new BorderLayout());
		popup.add(scroll, BorderLayout.CENTER);

		// The value the user last chose from the list. While the field still holds it verbatim
		// the popup stays closed - otherwise filling the field with an exact match would just
		// re-open the popup showing that one match. Cleared as soon as the text changes again.
		final String[] accepted = {null};

		final MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				// keep the highlighted row under the cursor so it's obvious what a click hits
				final int i = list.locationToIndex(e.getPoint());
				if (i >= 0)
				{
					list.setSelectedIndex(i);
				}
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				final String picked = list.getSelectedValue();
				if (picked != null)
				{
					accepted[0] = picked;
					field.setText(picked);
					popup.setVisible(false);
					field.requestFocusInWindow();
				}
			}
		};
		list.addMouseListener(mouse);
		list.addMouseMotionListener(mouse);

		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh(field, dictionary, model, list, popup, renderer, accepted);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh(field, dictionary, model, list, popup, renderer, accepted);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh(field, dictionary, model, list, popup, renderer, accepted);
			}
		});

		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				popup.setVisible(false);
			}
		});

		field.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				// Esc dismisses; Enter submits (handled by the field's own action listener) and
				// should take the popup down with it rather than leaving it hanging
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					popup.setVisible(false);
				}
			}
		});
	}

	private static void refresh(JTextField field, List<String> dictionary,
		DefaultListModel<String> model, JList<String> list, JWindow popup, SuggestionRenderer renderer,
		String[] accepted)
	{
		// the document listener fires mid-edit, before Swing has finished laying the field out
		// on first show - push the actual matching/positioning to the next EDT cycle
		SwingUtilities.invokeLater(() ->
		{
			final String typed = field.getText().trim();
			model.clear();
			if (typed.isEmpty() || !field.isShowing())
			{
				accepted[0] = null;
				popup.setVisible(false);
				return;
			}
			if (typed.equalsIgnoreCase(accepted[0]))
			{
				// field still holds exactly what was just picked - stay closed
				popup.setVisible(false);
				return;
			}
			accepted[0] = null;

			final String needle = typed.toLowerCase(Locale.ROOT);
			final List<String> startsWith = new ArrayList<>();
			final List<String> contains = new ArrayList<>();
			for (String name : dictionary)
			{
				final String lower = name.toLowerCase(Locale.ROOT);
				if (lower.startsWith(needle))
				{
					startsWith.add(name);
				}
				else if (lower.contains(needle))
				{
					contains.add(name);
				}
			}
			startsWith.addAll(contains);
			if (startsWith.isEmpty())
			{
				popup.setVisible(false);
				return;
			}

			startsWith.stream().limit(MAX_SUGGESTIONS).forEach(model::addElement);
			list.setVisibleRowCount(model.size());
			// wrap names to the popup width (minus the row padding and a little slack for the
			// scrollbar) instead of clipping them
			renderer.setWrapWidth(field.getWidth() - 28);

			final Point loc = field.getLocationOnScreen();
			popup.pack();
			final int height = Math.min(list.getPreferredSize().height, MAX_POPUP_HEIGHT) + 2;
			popup.setSize(field.getWidth(), height);
			popup.setLocation(loc.x, loc.y + field.getHeight());
			popup.setVisible(true);
		});
	}

	/** Renders one suggestion: full-size font, padded for an easy click target, long names
	 *  wrapped to a second line (HTML width) rather than truncated with an ellipsis. */
	private static final class SuggestionRenderer extends JLabel implements ListCellRenderer<String>
	{
		private int wrapWidth = 180;

		SuggestionRenderer(Font font)
		{
			setFont(font);
			setOpaque(true);
			setVerticalAlignment(SwingConstants.TOP);
			setBorder(new EmptyBorder(4, 7, 4, 7));
		}

		void setWrapWidth(int px)
		{
			wrapWidth = Math.max(60, px);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends String> list, String value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			setText("<html><div style='width:" + wrapWidth + "px'>" + escape(value) + "</div></html>");
			setBackground(isSelected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			setForeground(isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			return this;
		}

		private static String escape(String s)
		{
			return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		}
	}
}
