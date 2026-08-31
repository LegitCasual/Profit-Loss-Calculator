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
 * Rolls a finished (or in-progress) {@link Session} into per-category cost totals, the
 * income actually collected, the potential income that dropped, the net (collected - cost),
 * a separate "at risk, unresolved" figure, and the boss kill count.
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
	/** Value of loot that actually made it into the bag - this is what counts. */
	long collected;
	/** Value of everything that dropped, collected or not. */
	long potential;
	int bossKills;
	/** Number of deaths counted toward the cost total. */
	int deaths;

	/** gp spent - supplies, spells, teleports, ammo and confirmed death costs. */
	long total()
	{
		return consumables + spells + teleports + ammo + deathConfirmed;
	}

	/** Collected income minus cost. */
	long net()
	{
		return collected - total();
	}

	/** Potential income (everything that dropped) minus cost. */
	long potentialNet()
	{
		return potential - total();
	}

	/**
	 * @param collected value of loot that reached the inventory (already priced by the
	 *                  caller's valuation - GE / alch / highest)
	 * @param potential value of everything that dropped, priced the same way
	 */
	static SessionSummary of(Session session, long collected, long potential)
	{
		return new SessionSummary(
			session.consumableTotal(),
			session.spellTotal(),
			session.teleportTotal(),
			session.ammoTotal(),
			session.confirmedDeathTotal(),
			session.atRiskTotal(),
			collected,
			potential,
			session.getBossKills(),
			(int) session.getDeaths().stream().filter(DeathEntry::isCounted).count());
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
		m.put("cost", total());
		m.put("collectedIncome", collected);
		m.put("potentialIncome", potential);
		m.put("net", net());
		m.put("bossKills", bossKills);
		m.put("deaths", deaths);
		return m;
	}

	String toPlainText()
	{
		final StringBuilder sb = new StringBuilder("Session profit / loss\n");
		sb.append(String.format("  supplies   %s%n", gp(consumables)));
		sb.append(String.format("  spells     %s%n", gp(spells)));
		sb.append(String.format("  teleports  %s%n", gp(teleports)));
		sb.append(String.format("  ammo       %s%n", gp(ammo)));
		sb.append(String.format("  deaths     %s%n", gp(deathConfirmed)));
		sb.append(String.format("  Cost total:    %s%n", gp(total())));
		sb.append(String.format("  Collected:     %s%n", gp(collected)));
		sb.append(String.format("  Potential:     %s%n", gp(potential)));
		sb.append(String.format("  Net:           %s%n", gp(net())));
		if (atRisk > 0)
		{
			sb.append(String.format("  At risk, unresolved: %s%n", gp(atRisk)));
		}
		if (bossKills > 0)
		{
			sb.append(String.format("  Boss kills: %d  (net %s each)%n",
				bossKills, gp(net() / bossKills)));
		}
		return sb.toString();
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}
}
