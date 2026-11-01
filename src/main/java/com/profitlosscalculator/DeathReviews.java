/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Deaths the plugin charged as a full loss that the player still has to approve or deny.
 *
 * <p>A death is charged its full GE value when the session stops before a reclaim was seen
 * ({@code death_resolved} with outcome {@code lost}). That's right for a real loss, but if the
 * grave's retrieval chat was missed it books hundreds of millions that never left the player.
 * The session log can't tell those two apart, so each one waits in the Needs review list:
 *
 * <ul>
 *     <li><b>Approve</b> - the items really were lost; the charge stands.</li>
 *     <li><b>Deny</b> - the items came back from the grave; the charge drops to the fee.</li>
 * </ul>
 *
 * The verdict is appended to the session log as a {@code death_reviewed} line (the original
 * lines are left as written), so a reviewed death never asks again.
 */
final class DeathReviews
{
	/** "Dragon claws (13652)" - how {@code death_pending} keys its items. */
	private static final Pattern NAMED_ITEM = Pattern.compile("^(.*) \\((\\d+)\\)$");

	private DeathReviews()
	{
	}

	@Value
	static class Review
	{
		/** File name of the session log the death is in. */
		String log;
		/** The log's {@code session_start.startedAt} - the key into {@code history.jsonl}. */
		String runStart;
		long deathId;
		/** ISO instant of the death (or of the charge, if the death line is missing). */
		String at;
		/** History bucket the charge landed in, "" for not in combat, null if it can't be found. */
		@Nullable
		String mob;
		/** gp charged as lost. */
		long charged;
		/** Formula grave fee at the time of death - the default charge on a Deny. */
		long fee;
		/** What the log says was lost, in logged order. */
		List<Item> items;

		Review withMob(@Nullable String m)
		{
			return new Review(log, runStart, deathId, at, m, charged, fee, items);
		}
	}

	@Value
	static class Item
	{
		int id;
		String name;
		int qty;
	}

	/** Unreviewed full-loss deaths in one session log, oldest first, with no mob filled in. */
	static List<Review> scan(String logName, List<String> lines)
	{
		String runStart = null;
		final Map<Long, String> deathAt = new HashMap<>();
		final Map<Long, JsonObject> pending = new HashMap<>();
		final Map<Long, JsonObject> lost = new LinkedHashMap<>();
		final Set<Long> reviewed = new HashSet<>();
		for (String line : lines)
		{
			final JsonObject o = SessionHistory.asObject(line);
			final String ev = SessionHistory.str(o, "event");
			if (ev == null)
			{
				continue;
			}
			final long id = SessionHistory.lng(o, "deathId");
			switch (ev)
			{
				case "session_start":
					if (runStart == null)
					{
						runStart = SessionHistory.str(o, "startedAt");
					}
					break;
				case "death":
					deathAt.put(id, SessionHistory.str(o, "ts"));
					break;
				case "death_pending":
					pending.put(id, o);
					break;
				case "death_resolved":
					if ("lost".equals(SessionHistory.str(o, "outcome")) && SessionHistory.lng(o, "gp") > 0)
					{
						lost.put(id, o);
					}
					else
					{
						lost.remove(id);
					}
					break;
				case "death_reviewed":
					reviewed.add(id);
					break;
				default:
					break;
			}
		}
		if (runStart == null)
		{
			return Collections.emptyList();
		}
		final List<Review> out = new ArrayList<>();
		for (Map.Entry<Long, JsonObject> e : lost.entrySet())
		{
			final long id = e.getKey();
			if (reviewed.contains(id))
			{
				continue;
			}
			final JsonObject p = pending.get(id);
			final String at = deathAt.getOrDefault(id, SessionHistory.str(e.getValue(), "ts"));
			out.add(new Review(logName, runStart, id, at, null, SessionHistory.lng(e.getValue(), "gp"),
				SessionHistory.lng(p, "estimatedFee"), items(p)));
		}
		return out;
	}

	/** {@code death_pending.items} back into id / name / qty rows. */
	static List<Item> items(@Nullable JsonObject pending)
	{
		if (pending == null || !pending.has("items") || !pending.get("items").isJsonObject())
		{
			return Collections.emptyList();
		}
		final List<Item> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : pending.getAsJsonObject("items").entrySet())
		{
			int qty;
			try
			{
				qty = e.getValue().getAsInt();
			}
			catch (RuntimeException ex)
			{
				continue;
			}
			final Matcher m = NAMED_ITEM.matcher(e.getKey());
			if (m.matches())
			{
				out.add(new Item(Integer.parseInt(m.group(2)), m.group(1), qty));
			}
			else
			{
				out.add(new Item(-1, e.getKey(), qty));
			}
		}
		return out;
	}

	/**
	 * The {@code perMob} bucket a full-loss charge was booked to. Death lines don't name the
	 * mob (and the rollup may rename it), so this picks the bucket that recorded a death and
	 * whose cost can hold the charge - the largest, if several - then the not-in-combat bucket.
	 * Null when nothing fits (the record was edited, or the mob was deleted from history).
	 */
	@Nullable
	static String attribute(@Nullable RunRecord run, long charged)
	{
		if (run == null || run.getPerMob() == null)
		{
			return null;
		}
		String best = null;
		long bestCost = -1;
		for (Map.Entry<String, RunRecord.MobRun> e : run.getPerMob().entrySet())
		{
			final RunRecord.MobRun mr = e.getValue();
			if (mr != null && mr.getDeaths() > 0 && mr.getCost() >= charged && mr.getCost() > bestCost)
			{
				best = e.getKey();
				bestCost = mr.getCost();
			}
		}
		if (best != null)
		{
			return best;
		}
		final RunRecord.MobRun none = run.getPerMob().get(SessionHistory.UNATTRIBUTED);
		return none != null && none.getCost() >= charged ? SessionHistory.UNATTRIBUTED : null;
	}

	/** {@code mob}'s cost in {@code run} lowered by {@code refund} (never below 0), in place. */
	static void refund(RunRecord run, String mob, long refund)
	{
		final RunRecord.MobRun mr = run.getPerMob().get(mob);
		if (mr == null || refund <= 0)
		{
			return;
		}
		run.getPerMob().put(mob, new RunRecord.MobRun(mr.getKills(), mr.getGained(), mr.getDropped(),
			Math.max(0, mr.getCost() - refund), mr.getDeaths(), mr.getItems()));
	}
}
