/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
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
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
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
 */
final class AutocompletePopup
{
	private static final int MAX_SUGGESTIONS = 8;

	private AutocompletePopup()
	{
	}

	static void attach(JTextField field, List<String> dictionary)
	{
		final DefaultListModel<String> model = new DefaultListModel<>();
		final JList<String> list = new JList<>(model);
		list.setFocusable(false);
		list.setFont(FontManager.getRunescapeSmallFont());
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JScrollPane scroll = new JScrollPane(list,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setFocusable(false);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));

		final JWindow popup = new JWindow();
		popup.setFocusableWindowState(false);
		popup.setLayout(new BorderLayout());
		popup.add(scroll, BorderLayout.CENTER);

		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				final String picked = list.getSelectedValue();
				if (picked != null)
				{
					field.setText(picked);
					popup.setVisible(false);
					field.requestFocusInWindow();
				}
			}
		});

		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh(field, dictionary, model, list, popup);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh(field, dictionary, model, list, popup);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh(field, dictionary, model, list, popup);
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
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					popup.setVisible(false);
				}
			}
		});
	}

	private static void refresh(JTextField field, List<String> dictionary,
		DefaultListModel<String> model, JList<String> list, JWindow popup)
	{
		// the document listener fires mid-edit, before Swing has finished laying the field out
		// on first show - push the actual matching/positioning to the next EDT cycle
		SwingUtilities.invokeLater(() ->
		{
			final String typed = field.getText().trim();
			model.clear();
			if (typed.isEmpty() || !field.isShowing())
			{
				popup.setVisible(false);
				return;
			}

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

			final Point loc = field.getLocationOnScreen();
			popup.pack();
			popup.setSize(field.getWidth(), popup.getHeight());
			popup.setLocation(loc.x, loc.y + field.getHeight());
			popup.setVisible(true);
		});
	}
}
