/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.client.util.QuantityFormatter;

/**
 * Rolls a finished (or in-progress) {@link Session} into per-trip lines plus a confirmed
 * session total and a separate "at risk, unresolved" figure. Empty trips are already
 * absent from {@link Session#getTrips()}; this just reports the collapsed count.
 */
@Value
class SessionSummary
{
	@Value
	static class TripLine
	{
		int tripId;
		long consumables;
		long spells;
		long teleports;
		long ammo;
		long deathConfirmed;
		long deathAtRisk;

		long total()
		{
			return consumables + spells + teleports + ammo + deathConfirmed;
		}
	}

	List<TripLine> trips;
	long sessionTotal;
	long atRisk;
	int collapsedEmptyTrips;

	static SessionSummary of(Session session)
	{
		final List<TripLine> lines = new ArrayList<>();
		for (Trip t : session.getTrips())
		{
			lines.add(new TripLine(t.getId(), t.consumableTotal(), t.spellTotal(),
				t.teleportTotal(), t.ammoTotal(), t.confirmedDeathTotal(), t.atRiskTotal()));
		}
		return new SessionSummary(lines, session.confirmedTotal(), session.atRiskTotal(),
			session.getCollapsedEmptyTrips());
	}

	Map<String, Object> toJsonFields()
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		final List<Map<String, Object>> tripMaps = new ArrayList<>();
		for (TripLine tl : trips)
		{
			final Map<String, Object> tm = new LinkedHashMap<>();
			tm.put("tripId", tl.getTripId());
			tm.put("consumables", tl.getConsumables());
			tm.put("spells", tl.getSpells());
			tm.put("teleports", tl.getTeleports());
			tm.put("ammo", tl.getAmmo());
			tm.put("deathConfirmed", tl.getDeathConfirmed());
			tm.put("deathAtRisk", tl.getDeathAtRisk());
			tm.put("total", tl.total());
			tripMaps.add(tm);
		}
		m.put("trips", tripMaps);
		m.put("sessionTotal", sessionTotal);
		m.put("atRiskUnresolved", atRisk);
		m.put("collapsedEmptyTrips", collapsedEmptyTrips);
		return m;
	}

	String toPlainText()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Session cost summary\n");
		if (trips.isEmpty())
		{
			sb.append("  (no trips with spending)\n");
		}
		for (TripLine tl : trips)
		{
			sb.append(String.format("  Trip #%d: %s", tl.getTripId(), gp(tl.total())));
			sb.append(String.format("  [supplies %s, spells %s, teleports %s, ammo %s, death %s]%n",
				gp(tl.getConsumables()), gp(tl.getSpells()), gp(tl.getTeleports()),
				gp(tl.getAmmo()), gp(tl.getDeathConfirmed())));
		}
		sb.append(String.format("  Session total: %s%n", gp(sessionTotal)));
		if (atRisk > 0)
		{
			sb.append(String.format("  At risk, unresolved: %s%n", gp(atRisk)));
		}
		if (collapsedEmptyTrips > 0)
		{
			sb.append(String.format("  (%d empty trip(s) collapsed)%n", collapsedEmptyTrips));
		}
		return sb.toString();
	}

	private static String gp(long v)
	{
		return QuantityFormatter.formatNumber(v) + " gp";
	}
}
