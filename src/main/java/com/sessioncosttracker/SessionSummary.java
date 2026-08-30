/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;
import net.runelite.client.util.QuantityFormatter;

/**
 * Rolls a finished (or in-progress) {@link Session} into per-category totals, a confirmed
 * session total and a separate "at risk, unresolved" figure, plus the boss kill count.
 */
@Value
class SessionSummary
{
	long consumables;
	long spells;
	long teleports;
	long ammo;
	long deathConfirmed;
	long atRisk;
	int bossKills;

	long total()
	{
		return consumables + spells + teleports + ammo + deathConfirmed;
	}

	static SessionSummary of(Session session)
	{
		return new SessionSummary(
			session.consumableTotal(),
			session.spellTotal(),
			session.teleportTotal(),
			session.ammoTotal(),
			session.confirmedDeathTotal(),
			session.atRiskTotal(),
			session.getBossKills());
	}

	Map<String, Object> toJsonFields()
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("consumables", consumables);
		m.put("spells", spells);
		m.put("teleports", teleports);
		m.put("ammo", ammo);
		m.put("deathConfirmed", deathConfirmed);
		m.put("atRiskUnresolved", atRisk);
		m.put("bossKills", bossKills);
		m.put("total", total());
		return m;
	}

	String toPlainText()
	{
		final StringBuilder sb = new StringBuilder("Session cost summary\n");
		sb.append(String.format("  supplies   %s%n", gp(consumables)));
		sb.append(String.format("  spells     %s%n", gp(spells)));
		sb.append(String.format("  teleports  %s%n", gp(teleports)));
		sb.append(String.format("  ammo       %s%n", gp(ammo)));
		sb.append(String.format("  deaths     %s%n", gp(deathConfirmed)));
		sb.append(String.format("  Session total: %s%n", gp(total())));
		if (atRisk > 0)
		{
			sb.append(String.format("  At risk, unresolved: %s%n", gp(atRisk)));
		}
		if (bossKills > 0)
		{
			sb.append(String.format("  Boss kills: %d  (%s each)%n",
				bossKills, gp(total() / bossKills)));
		}
		return sb.toString();
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}
}
