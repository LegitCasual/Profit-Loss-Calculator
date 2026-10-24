/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * "What's new" screen for people who already had the plugin. Shown in place of the tabs, like
 * {@link WelcomeContent}, the first time the panel opens after an update whose
 * {@link #VERSION} they haven't confirmed yet - one OK and it stays gone until the next
 * release. A fresh install sees the welcome screen instead, and never
 * this one for the release it installed on.
 *
 * <p>To ship a new set of notes: bump {@link #VERSION} (and {@code version} in
 * runelite-plugin.properties to match), set {@link #RELEASE_DATE}, and replace {@link #NOTES}.
 * Any new version shows the screen again - it's compared for a match, not ordered. Leaving the
 * version alone keeps the screen hidden for everyone who has already confirmed it.
 */
class WhatsNewContent extends JPanel
{
	/** The release these notes belong to - shown on the screen, and what "seen" is keyed on. */
	static final String VERSION = "0.3";

	/** Display only. */
	static final String RELEASE_DATE = "25 Oct 2026";

	/** True unless {@code confirmedVersion} (the version last OK'd; blank = never) is this one. */
	static boolean isUnseen(String confirmedVersion)
	{
		return !VERSION.equals(confirmedVersion);
	}

	/** Player-facing, one short line each - the panel is ~215px wide. */
	static final List<String> NOTES = Collections.unmodifiableList(Arrays.asList(
		"<b>New: History dashboard.</b> Hit <b>Open dashboard</b> on the History tab for charts of "
			+ "your profit over time and your best and worst activities, in a window you can put "
			+ "on a second screen.",
		"Pick a date range, and include or leave out combat, skilling and time out of combat.",
		"<b>Loot</b> shows what your loot is made of, and whether what you sold went for more or "
			+ "less than it was worth when you looted it.",
		"<b>Costs</b> shows where your gp goes - deaths, supplies, spells, teleports - and which "
			+ "activities cost the most.",
		"gp / hr only shows for runs spent on one activity for now. Full gp / hr is coming."
	));

	/** Optional sign-off under the notes - {@code null} to leave it out for a release. */
	static final String THANKS = "300+ of you have decided to not only try this plugin, but are "
		+ "actively using it (or haven't disabled it) - and for that, thank you.";

	private static final Color OK_GREEN = new Color(92, 143, 100);
	/** Brighter than ColorScheme.LIGHT_GRAY_COLOR - this screen is meant to be read. */
	private static final Color NOTE_TEXT = new Color(220, 220, 220);

	/** Wrap width for the text blocks - stays inside the ~215px plugin panel. */
	private static final String W = "width:165px";

	WhatsNewContent(Runnable onOk)
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 12));

		final JPanel col = new JPanel();
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

		final JLabel title = new JLabel("<html><div style='" + W + "'>Profit Loss Calculator</div></html>");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel badge = new JLabel("WHAT'S NEW");
		badge.setOpaque(true);
		badge.setBackground(ColorScheme.BRAND_ORANGE);
		badge.setForeground(ColorScheme.DARKER_GRAY_COLOR);
		badge.setFont(FontManager.getRunescapeSmallFont());
		badge.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
		badge.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel version = new JLabel("Version " + VERSION + "  ·  " + RELEASE_DATE);
		version.setFont(FontManager.getRunescapeSmallFont());
		version.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		version.setAlignmentX(Component.LEFT_ALIGNMENT);

		col.add(title);
		col.add(PanelUi.vgap(6));
		col.add(badge);
		col.add(PanelUi.vgap(4));
		col.add(version);
		col.add(PanelUi.vgap(12));
		for (String note : NOTES)
		{
			final JLabel l = new JLabel("<html><div style='" + W + "'>&bull; " + note + "</div></html>");
			l.setFont(FontManager.getRunescapeFont());
			l.setForeground(NOTE_TEXT);
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			col.add(l);
			col.add(PanelUi.vgap(10));
		}

		if (THANKS != null)
		{
			col.add(PanelUi.vgap(4));
			col.add(PanelUi.sectionLabel("Thank you"));
			final JLabel thanks = new JLabel("<html><div style='" + W + "'>" + THANKS + "</div></html>");
			thanks.setFont(FontManager.getRunescapeFont());
			thanks.setForeground(NOTE_TEXT);
			thanks.setAlignmentX(Component.LEFT_ALIGNMENT);
			col.add(thanks);
		}

		col.add(PanelUi.vgap(12));
		col.add(PanelUi.sectionLabel("Bugs & suggestions"));
		final JLabel ask = new JLabel("<html><div style='" + W + "'>If you have any suggestions, "
			+ "or run into any issues, please let me know on GitHub.</div></html>");
		ask.setFont(FontManager.getRunescapeFont());
		ask.setForeground(NOTE_TEXT);
		ask.setAlignmentX(Component.LEFT_ALIGNMENT);
		col.add(ask);
		col.add(PanelUi.vgap(6));
		final JButton github = new JButton("Open GitHub issues");
		github.setFocusPainted(false);
		github.addActionListener(e -> LinkBrowser.browse(WelcomeContent.ISSUES_URL));
		col.add(PanelUi.stretch(github));

		final JButton ok = new JButton("OK");
		ok.setFocusPainted(false);
		ok.setBackground(OK_GREEN);
		ok.addActionListener(e -> onOk.run());
		col.add(PanelUi.vgap(14));
		col.add(PanelUi.stretch(ok));

		add(col, BorderLayout.NORTH);
	}
}
