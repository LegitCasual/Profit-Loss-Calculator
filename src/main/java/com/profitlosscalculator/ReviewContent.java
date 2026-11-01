/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The "Needs review" screen: every death that was charged as a full loss because no reclaim
 * was seen (see {@link DeathReviews}). Each one lists the items the log says were lost, with
 * Approve (they really were lost) and Deny (they came back from the grave - charge the fee).
 *
 * <p>One BoxLayout-Y column in a BorderLayout NORTH slot, rebuilt from scratch each render.
 */
class ReviewContent extends JPanel
{
	private static final DateTimeFormatter WHEN =
		DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());

	private final ProfitLossCalculatorPanel.Controls controls;
	private final JPanel content = new JPanel();

	ReviewContent(ProfitLossCalculatorPanel.Controls controls)
	{
		this.controls = controls;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.NORTH);
		render(Collections.emptyList());
	}

	void render(List<DeathReviews.Review> reviews)
	{
		content.removeAll();
		content.add(PanelUi.sectionLabel("Deaths to review"));
		final JLabel intro = new JLabel("<html>These deaths were charged as a full loss because the plugin "
			+ "never saw the items come back. Approve if they really were lost; Deny if you got them back "
			+ "from your grave, and only the fee is charged.</html>");
		intro.setFont(FontManager.getRunescapeSmallFont());
		intro.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		intro.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(intro);
		content.add(PanelUi.vgap(8));
		if (reviews.isEmpty())
		{
			final JLabel none = new JLabel("Nothing needs review.");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(none);
		}
		for (DeathReviews.Review r : reviews)
		{
			content.add(reviewBox(r));
			content.add(PanelUi.vgap(6));
		}
		content.revalidate();
		content.repaint();
	}

	private JPanel reviewBox(DeathReviews.Review r)
	{
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(PanelUi.DEATH_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		p.add(left(new JLabel("Death - " + when(r.getAt()) + " - " + mobLabel(r.getMob()))));

		final JLabel charged = left(new JLabel("Charged as lost: " + PanelUi.gp(r.getCharged())));
		charged.setForeground(PanelUi.LOSS_COLOR);
		p.add(charged);

		p.add(left(small("Items the log says were lost:")));
		if (r.getItems().isEmpty())
		{
			p.add(left(small("  (not itemised in the log)")));
		}
		final StringBuilder sb = new StringBuilder("<html>");
		for (DeathReviews.Item it : r.getItems())
		{
			sb.append(PanelUi.gpPlain(it.getQty())).append(" x ").append(PanelUi.escape(it.getName())).append("<br>");
		}
		if (!r.getItems().isEmpty())
		{
			final JLabel items = left(new JLabel(sb.append("</html>").toString()));
			items.setFont(FontManager.getRunescapeSmallFont());
			p.add(items);
		}

		final JPanel feeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		feeRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JTextField fee = new JTextField(String.valueOf(r.getFee()), 7);
		fee.setToolTipText("The grave fee you paid - charged instead of the full value on Deny");
		feeRow.add(small("Grave fee:"));
		feeRow.add(fee);
		p.add(feeRow);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JButton approve = new JButton("Approve");
		approve.setToolTipText("The items really were lost - keep the " + PanelUi.gp(r.getCharged()) + " charge");
		approve.addActionListener(e -> controls.onReviewDeath(r, true, r.getCharged()));
		final JButton deny = new JButton("Deny");
		deny.setToolTipText("I got them back from my grave - charge only the fee");
		deny.addActionListener(e ->
		{
			try
			{
				controls.onReviewDeath(r, false, Long.parseLong(fee.getText().trim().replace(",", "")));
			}
			catch (NumberFormatException ignored)
			{
				// leave the field for the user to fix
			}
		});
		buttons.add(approve);
		buttons.add(deny);
		p.add(buttons);
		return p;
	}

	private static String when(String iso)
	{
		try
		{
			return WHEN.format(Instant.parse(iso));
		}
		catch (RuntimeException ex)
		{
			return "unknown time";
		}
	}

	private static String mobLabel(String mob)
	{
		if (mob == null)
		{
			return "unknown mob";
		}
		return mob.isEmpty() ? "not in combat" : mob;
	}

	private static JLabel small(String text)
	{
		final JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private static JLabel left(JLabel l)
	{
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
}
