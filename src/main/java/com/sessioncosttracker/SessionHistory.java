/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Reads, aggregates and appends {@code .runelite/session-cost-tracker/history.jsonl} - one
 * {@link RunRecord} per finished run (session or farm). All disk IO runs on a private
 * single-thread executor.
 *
 * <p>The History tab merges every run's {@code perMob} by name, so one mob's rows from many
 * sessions and farms collapse into a single lifetime row. On load, older schema lines are
 * upgraded in place (the original file kept as {@value #BACKUP_NAME}).
 */
@Slf4j
@Singleton
class SessionHistory
{
	static final int SCHEMA = 3;
	/** Key used for cost that could not be tied to a mob. */
	static final String UNATTRIBUTED = "";
	private static final String DIR_NAME = "session-cost-tracker";
	private static final String FILE_NAME = "history.jsonl";
	private static final String BACKUP_NAME = "history-v1-backup.jsonl";

	private final Gson gson;
	private ExecutorService executor;

	/** Touched only on the executor thread. */
	private final List<RunRecord> entries = new ArrayList<>();
	private volatile Snapshot snapshot = Snapshot.EMPTY;

	@Inject
	SessionHistory(Gson gson)
	{
		this.gson = gson;
	}

	void start()
	{
		executor = Executors.newSingleThreadExecutor(r ->
		{
			final Thread t = new Thread(r, "session-cost-tracker-history");
			t.setDaemon(true);
			return t;
		});
	}

	void stop()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
	}

	Snapshot snapshot()
	{
		return snapshot;
	}

	/** Upgrade old schema lines, migrate legacy files, read history.jsonl, aggregate.
	 *  {@code cb} runs on the executor thread. */
	void load(Consumer<Snapshot> cb)
	{
		run(() ->
		{
			try
			{
				Files.createDirectories(dir());
				entries.clear();
				readHistoryFile();
				migrateLegacy();
			}
			catch (Exception e)
			{
				log.warn("could not load run history", e);
			}
			publish(cb);
		});
	}

	/** Drop one mob from every run's {@code perMob} and rewrite the file; a run left with no
	 *  mobs is deleted entirely. */
	void deleteMob(String mob, Consumer<Snapshot> cb)
	{
		run(() ->
		{
			entries.removeIf(rr ->
			{
				if (rr.getPerMob() != null)
				{
					rr.getPerMob().remove(mob);
				}
				return rr.getPerMob() == null || rr.getPerMob().isEmpty();
			});
			try
			{
				rewrite();
			}
			catch (IOException e)
			{
				log.warn("could not rewrite run history", e);
			}
			publish(cb);
		});
	}

	void record(RunRecord entry, Consumer<Snapshot> cb)
	{
		run(() ->
		{
			try
			{
				Files.createDirectories(dir());
				Files.write(file(), (gson.toJson(entry) + "\n").getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			catch (IOException e)
			{
				log.warn("could not append to run history", e);
			}
			entries.add(entry);
			publish(cb);
		});
	}

	/** Delete history.jsonl (and its backup) and every {@code session-*.jsonl} except
	 *  {@code keepSessionId}. */
	void clear(String keepSessionId, Consumer<Snapshot> cb)
	{
		run(() ->
		{
			try
			{
				Files.deleteIfExists(file());
				Files.deleteIfExists(dir().resolve(BACKUP_NAME));
				final String keep = keepSessionId == null ? "" : "session-" + keepSessionId + ".jsonl";
				try (Stream<Path> s = Files.list(dir()))
				{
					s.filter(p ->
					{
						final String n = p.getFileName().toString();
						return n.startsWith("session-") && n.endsWith(".jsonl") && !n.equals(keep);
					}).forEach(p ->
					{
						try
						{
							Files.delete(p);
						}
						catch (IOException ignored)
						{
							// best effort
						}
					});
				}
			}
			catch (IOException e)
			{
				log.warn("could not clear run history", e);
			}
			entries.clear();
			publish(cb);
		});
	}

	// ------------------------------------------------------------------ internals

	private void run(Runnable r)
	{
		final ExecutorService ex = executor;
		if (ex != null && !ex.isShutdown())
		{
			ex.execute(r);
		}
	}

	private void publish(Consumer<Snapshot> cb)
	{
		snapshot = aggregate(entries);
		if (cb != null)
		{
			cb.accept(snapshot);
		}
	}

	private Path dir()
	{
		return RuneLite.RUNELITE_DIR.toPath().resolve(DIR_NAME);
	}

	private Path file()
	{
		return dir().resolve(FILE_NAME);
	}

	/** Read history.jsonl into {@link #entries}. Older schema lines are upgraded; if any were
	 *  found the file is rewritten as the current schema with a one-time {@value #BACKUP_NAME}. */
	private void readHistoryFile() throws IOException
	{
		if (!Files.exists(file()))
		{
			return;
		}
		boolean sawOld = false;
		for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8))
		{
			if (line == null || line.trim().isEmpty())
			{
				continue;
			}
			final JsonObject o = asObject(line);
			final int schema = o != null && o.has("schema") ? (int) lng(o, "schema") : 1;
			RunRecord rr = null;
			if (schema >= SCHEMA)
			{
				rr = parseRun(line);
			}
			else if (schema == 2)
			{
				sawOld = true;
				rr = migrateV2(o);
			}
			else
			{
				sawOld = true;
				rr = migrateV1(o);
			}
			if (rr != null)
			{
				entries.add(rr);
			}
		}
		if (sawOld)
		{
			final Path backup = dir().resolve(BACKUP_NAME);
			if (!Files.exists(backup))
			{
				Files.copy(file(), backup, StandardCopyOption.COPY_ATTRIBUTES);
			}
			rewrite();
		}
	}

	/** Overwrite history.jsonl with the current {@link #entries}. */
	private void rewrite() throws IOException
	{
		final StringBuilder sb = new StringBuilder();
		for (RunRecord rr : entries)
		{
			sb.append(gson.toJson(rr)).append('\n');
		}
		Files.write(file(), sb.toString().getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private RunRecord parseRun(String line)
	{
		try
		{
			return gson.fromJson(line, RunRecord.class);
		}
		catch (RuntimeException ex)
		{
			log.debug("skipping malformed history line", ex);
			return null;
		}
	}

	/** schema-2 {@code FarmRecord} -&gt; a one-mob farm run. */
	static RunRecord migrateV2(JsonObject o)
	{
		final String mob = str(o, "mob");
		if (mob == null || mob.isEmpty())
		{
			return null;
		}
		final RunRecord.MobRun mr = new RunRecord.MobRun(
			(int) lng(o, "kills"), lng(o, "collected"), lng(o, "dropped"),
			lng(o, "cost"), (int) lng(o, "deaths"), itemArrays(o));
		final Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		perMob.put(mob, mr);
		return new RunRecord(SCHEMA, "farm", str(o, "start"), str(o, "end"),
			lng(o, "durationSec"), str(o, "valuation"), perMob);
	}

	/** schema-1 history line -&gt; a farm run, but only if it was effectively one mob (the
	 *  mixed sessions the old timestamp-sliced model tracked are dropped). */
	static RunRecord migrateV1(JsonObject o)
	{
		if (o == null || !o.has("perMob") || !o.get("perMob").isJsonObject())
		{
			return null;
		}
		final JsonObject perMob = o.getAsJsonObject("perMob");
		String mob = null;
		JsonObject roll = null;
		for (Map.Entry<String, JsonElement> e : perMob.entrySet())
		{
			if (!e.getValue().isJsonObject() || lng(e.getValue().getAsJsonObject(), "kills") <= 0)
			{
				continue;
			}
			if (mob != null)
			{
				return null; // more than one mob with kills
			}
			mob = e.getKey();
			roll = e.getValue().getAsJsonObject();
		}
		if (mob == null)
		{
			return null;
		}
		final RunRecord.MobRun mr = new RunRecord.MobRun(
			(int) lng(roll, "kills"), lng(roll, "collected"), lng(roll, "dropped"),
			lng(o, "cost"), (int) lng(o, "deaths"), itemArrays(roll));
		final Map<String, RunRecord.MobRun> pm = new LinkedHashMap<>();
		pm.put(mob, mr);
		return new RunRecord(SCHEMA, "farm", str(o, "start"), str(o, "end"),
			lng(o, "durationSec"), str(o, "valuation"), pm);
	}

	private void migrateLegacy() throws IOException
	{
		final Set<String> known = new HashSet<>();
		for (RunRecord rr : entries)
		{
			if (rr.getStart() != null)
			{
				known.add(rr.getStart());
			}
		}

		final List<Path> legacy;
		try (Stream<Path> s = Files.list(dir()))
		{
			legacy = s.filter(p ->
			{
				final String n = p.getFileName().toString();
				return n.startsWith("session-") && n.endsWith(".jsonl");
			}).sorted().collect(Collectors.toList());
		}

		for (Path p : legacy)
		{
			final RunRecord rr = readSessionFile(p);
			if (rr == null || rr.getStart() == null || known.contains(rr.getStart()))
			{
				continue;
			}
			Files.write(file(), (gson.toJson(rr) + "\n").getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			entries.add(rr);
			known.add(rr.getStart());
		}
	}

	/** Build a run record from a session-*.jsonl file. Uses {@code session_stop.perMob} when
	 *  present (runs written by the current code); otherwise falls back to the single-mob rule. */
	private RunRecord readSessionFile(Path p)
	{
		String start = null;
		String target = null;
		JsonObject stop = null;
		try
		{
			for (String line : Files.readAllLines(p, StandardCharsets.UTF_8))
			{
				final JsonObject o = asObject(line);
				if (o == null || !o.has("event"))
				{
					continue;
				}
				final String ev = o.get("event").getAsString();
				if ("session_start".equals(ev))
				{
					start = str(o, "startedAt");
					target = str(o, "target");
				}
				else if ("session_stop".equals(ev))
				{
					stop = o;
				}
			}
		}
		catch (IOException e)
		{
			return null;
		}
		if (stop == null || start == null)
		{
			return null;
		}

		Map<String, RunRecord.MobRun> perMob = new LinkedHashMap<>();
		if (stop.has("perMob") && stop.get("perMob").isJsonObject())
		{
			perMob = gson.fromJson(stop.get("perMob"),
				new TypeToken<Map<String, RunRecord.MobRun>>()
				{
				}.getType());
		}
		if (perMob == null || perMob.isEmpty())
		{
			return null; // pre-perMob session file - nothing reliable to reconstruct
		}
		final String kind = target != null && !target.isEmpty() ? "farm" : "session";
		return new RunRecord(SCHEMA, kind, start, str(stop, "ts"),
			lng(stop, "durationSeconds"), str(stop, "valuation"), perMob);
	}

	/** An {@code items} array -&gt; {@code List<long[]>}, tolerating the old two-element shape. */
	private static List<long[]> itemArrays(JsonObject roll)
	{
		final List<long[]> out = new ArrayList<>();
		if (roll == null || !roll.has("items") || !roll.get("items").isJsonArray())
		{
			return out;
		}
		for (JsonElement el : roll.getAsJsonArray("items"))
		{
			if (!el.isJsonArray())
			{
				continue;
			}
			final JsonArray a = el.getAsJsonArray();
			final long[] t = new long[3];
			for (int i = 0; i < 3 && i < a.size(); i++)
			{
				try
				{
					t[i] = a.get(i).getAsLong();
				}
				catch (RuntimeException ignored)
				{
					// leave 0
				}
			}
			out.add(t);
		}
		return out;
	}

	private static JsonObject asObject(String line)
	{
		try
		{
			final JsonElement el = new JsonParser().parse(line);
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static String str(JsonObject o, String k)
	{
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}

	private static long lng(JsonObject o, String k)
	{
		try
		{
			return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : 0L;
		}
		catch (NumberFormatException ex)
		{
			return 0L;
		}
	}

	// ------------------------------------------------------------------ aggregation (pure)

	static Snapshot aggregate(List<RunRecord> entries)
	{
		long gained = 0;
		long cost = 0;
		int kills = 0;
		final Map<String, MobAcc> mobs = new LinkedHashMap<>();

		for (RunRecord e : entries)
		{
			if (e == null || e.getPerMob() == null)
			{
				continue;
			}
			e.getPerMob().forEach((name, mr) ->
			{
				if (mr == null)
				{
					return;
				}
				final MobAcc a = mobs.computeIfAbsent(name == null ? UNATTRIBUTED : name, n -> new MobAcc());
				a.runs++;
				a.kills += mr.getKills();
				a.gained += mr.getGained();
				a.dropped += mr.getDropped();
				a.cost += mr.getCost();
				a.deaths += mr.getDeaths();
				if (mr.getItems() != null)
				{
					for (long[] it : mr.getItems())
					{
						if (it == null || it.length < 2)
						{
							continue;
						}
						final long[] agg = a.items.computeIfAbsent((int) it[0], id -> new long[]{it[0], 0, 0});
						agg[1] += it[1];
						agg[2] += it.length >= 3 ? it[2] : 0;
					}
				}
				// only farm runs (one mob) can attribute their wall-clock time to a kill rate
				if ("farm".equals(e.getKind()) && mr.getKills() > 0)
				{
					a.farmSec += e.getDurationSec();
					a.farmKills += mr.getKills();
				}
				a.runList.add(new RunRow(e.getStart(), e.getDurationSec(), e.getKind(),
					mr.getKills(), mr.getGained(), mr.getCost(), mr.getGained() - mr.getCost()));
			});
		}

		for (MobAcc a : mobs.values())
		{
			gained += a.gained;
			cost += a.cost;
			kills += a.kills;
		}

		final List<MobStats> list = new ArrayList<>();
		mobs.forEach((name, a) -> list.add(new MobStats(
			name, a.runs, a.kills, a.deaths, a.gained - a.cost, a.cost, a.gained, a.dropped,
			a.kills > 0 ? (a.gained - a.cost) / a.kills : 0,
			a.farmKills > 0 ? a.farmSec / a.farmKills : 0,
			triples(a.items), a.runList)));
		// biggest net first, but the "not in combat" bucket always sinks to the bottom
		list.sort(Comparator.comparing((MobStats m) -> m.getName().isEmpty())
			.thenComparing(Comparator.comparingLong(MobStats::getNet).reversed()));

		return new Snapshot(entries.size(), gained - cost, gained, cost, kills, list);
	}

	private static List<long[]> triples(Map<Integer, long[]> m)
	{
		final List<long[]> out = new ArrayList<>(m.values());
		out.sort((x, y) -> Long.compare(y[2], x[2]));
		return out;
	}

	private static final class MobAcc
	{
		private int runs;
		private int kills;
		private long gained;
		private long dropped;
		private long cost;
		private int deaths;
		/** wall-clock seconds and kills from this mob's farm runs (for the kill-rate average). */
		private long farmSec;
		private int farmKills;
		/** id -&gt; {id, totalQty, totalGp} */
		private final Map<Integer, long[]> items = new HashMap<>();
		private final List<RunRow> runList = new ArrayList<>();
	}

	@Value
	static class Snapshot
	{
		static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, new ArrayList<>());

		/** Number of runs recorded. */
		int runs;
		long net;
		long gained;
		long cost;
		int kills;
		List<MobStats> mobs;
	}

	@Value
	static class MobStats
	{
		/** NPC name, or "" for the "not in combat" cost bucket. */
		String name;
		int runs;
		int kills;
		int deaths;
		long net;
		long cost;
		long gained;
		long dropped;
		long gpPerKill;
		/** average wall-clock seconds per kill across this mob's farm runs (0 if never farmed). */
		long secPerKill;
		/** merged collected items, {@code [ [id, qty, gp], ... ]}, gp-descending. */
		List<long[]> items;
		/** one row per run that touched this mob, for the drill-down. */
		List<RunRow> runList;
	}

	@Value
	static class RunRow
	{
		/** ISO instant the run started. */
		String start;
		long durationSec;
		/** "session" or "farm". */
		String kind;
		int kills;
		long gained;
		long cost;
		long net;
	}
}
