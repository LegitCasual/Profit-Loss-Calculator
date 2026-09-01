/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * One-time first-run screen. Shown in place of the tabs the first time the panel is
 * opened after install, then dismissed for good (the plugin persists a hidden config
 * flag). Says the plugin is an early build and points at the GitHub issue tracker for
 * bug reports and suggestions.
 */
class WelcomeContent extends JPanel
{
	static final String REPO_URL = "https://github.com/LegitCasual/Runelite---profit-loss";
	private static final String ISSUES_URL = REPO_URL + "/issues";

	/** A calmer green than the Session tab's start button - this screen isn't urgent. */
	private static final Color CONTINUE_GREEN = new Color(92, 143, 100);

	/** Wrap width for the text blocks - stays inside the ~215px plugin panel. */
	private static final String W = "width:165px";

	WelcomeContent(Runnable onContinue)
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 12));

		final JPanel col = new JPanel();
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

		final JLabel title = new JLabel("<html><div style='" + W + "'>Session Cost Tracker</div></html>");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel badge = new JLabel("IN DEVELOPMENT");
		badge.setOpaque(true);
		badge.setBackground(ColorScheme.BRAND_ORANGE);
		badge.setForeground(ColorScheme.DARKER_GRAY_COLOR);
		badge.setFont(FontManager.getRunescapeSmallFont());
		badge.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
		badge.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel body = html(
			"Thanks for testing this early build. It tracks the profit and loss of a play "
			+ "session, or of a targeted farm of one mob you name.<br><br>"
			+ "Working out gp in RuneScape is fiddly, so expect rough edges - especially "
			+ "around deaths, High Alchemy and multi-phase bosses. The README covers what "
			+ "is and isn't counted.");
		body.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		final JLabel ask = html(
			"Found a bug, or want a feature? Please put it on GitHub - every report and "
			+ "idea is welcome.");
		ask.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		final JButton github = new JButton("Open GitHub issues");
		github.setFocusPainted(false);
		github.addActionListener(e -> LinkBrowser.browse(ISSUES_URL));

		final JButton cont = new JButton("Continue");
		cont.setFocusPainted(false);
		cont.setBackground(CONTINUE_GREEN);
		cont.addActionListener(e -> onContinue.run());

		col.add(title);
		col.add(PanelUi.vgap(6));
		col.add(badge);
		col.add(PanelUi.vgap(10));
		col.add(body);
		col.add(PanelUi.vgap(12));
		col.add(PanelUi.sectionLabel("Bugs & suggestions"));
		col.add(ask);
		col.add(PanelUi.vgap(6));
		col.add(PanelUi.stretch(github));
		col.add(PanelUi.vgap(14));
		col.add(PanelUi.stretch(cont));

		add(col, BorderLayout.NORTH);
	}

	/** A left-aligned, fixed-width wrapping label. */
	private static JLabel html(String inner)
	{
		final JLabel l = new JLabel("<html><div style='" + W + "'>" + inner + "</div></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
}
