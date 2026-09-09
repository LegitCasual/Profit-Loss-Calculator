/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

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
import java.time.Instant;
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
 * Reads, aggregates and appends {@code .runelite/profit-loss-calculator/history.jsonl} - one
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
	/** {@code realised.jsonl} / {@code banked.jsonl} line schema. */
	static final int REALISED_SCHEMA = 1;
	/** Key used for cost that could not be tied to a mob. */
	static final String UNATTRIBUTED = "";
	private static final String DIR_NAME = "profit-loss-calculator";
	/** Data directory before the plugin was renamed from "Session Cost Tracker". */
	private static final String LEGACY_DIR_NAME = "session-cost-tracker";
	private static final String FILE_NAME = "history.jsonl";
	private static final String BACKUP_NAME = "history-v1-backup.jsonl";
	/** One line per GE sale / High Alch chunk attributed back to a run's loot. */
	private static final String REALISED_NAME = "realised.jsonl";
	/** One line per bank / deposit-box move of a run's loot (signed qty). */
	private static final String BANKED_NAME = "banked.jsonl";
	/** Last-seen GE offer state per slot, so an offline-completed sale isn't double-counted. */
	private static final String GE_SLOTS_NAME = "ge-slots.json";

	private static final int COINS = net.runelite.api.gameval.ItemID.COINS;
	private static final int PLATINUM = net.runelite.api.gameval.ItemID.PLATINUM;

	private final Gson gson;
	private ExecutorService executor;

	/** Touched only on the executor thread. */
	private final List<RunRecord> entries = new ArrayList<>();
	/** GE sales + High Alchs attributed to runs. Touched only on the executor thread. */
	private final List<RealisedEntry> realised = new ArrayList<>();
	/** Bank / deposit-box moves of run loot (signed qty). Touched only on the executor thread. */
	private final List<BankEntry> banked = new ArrayList<>();
	/** Last-loaded GE slot state, for the plugin to seed its tracker once. */
	private volatile Map<Integer, GeSaleTracker.SlotState> geSlots = new HashMap<>();
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
			final Thread t = new Thread(r, "profit-loss-calculator-history");
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
				migrateDataDir();
				Files.createDirectories(dir());
				entries.clear();
				readHistoryFile();
				migrateLegacy();
				realised.clear();
				readRealisedFile();
				banked.clear();
				readBankedFile();
				geSlots = readGeSlots();
			}
			catch (Exception e)
			{
				log.warn("could not load run history", e);
			}
			publish(cb);
		});
	}

	/**
	 * One-time move of the on-disk data (history + session logs) from the pre-rename
	 * {@code .runelite/session-cost-tracker/} directory. Runs on the history executor thread
	 * before anything reads or writes, so a rename never loses a user's recorded history.
	 */
	private void migrateDataDir()
	{
		final Path legacy = RuneLite.RUNELITE_DIR.toPath().resolve(LEGACY_DIR_NAME);
		final Path current = dir();
		if (!Files.isDirectory(legacy))
		{
			return;
		}
		try
		{
			if (!Files.exists(current))
			{
				Files.move(legacy, current);
				log.info("migrated plugin data {} -> {}", legacy, current);
				return;
			}
			// new directory already exists (a session started before this ran) - move any
			// files that aren't already there, then drop the empty legacy directory
			try (Stream<Path> s = Files.list(legacy))
			{
				s.forEach(p ->
				{
					try
					{
						final Path dest = current.resolve(p.getFileName().toString());
						if (!Files.exists(dest))
						{
							Files.move(p, dest);
						}
					}
					catch (IOException ignored)
					{
						// best effort
					}
				});
			}
			Files.deleteIfExists(legacy);
		}
		catch (IOException e)
		{
			log.warn("could not migrate legacy plugin data dir {}", legacy, e);
		}
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
			// realised / banked are attributed globally by item now, not per mob - deleting a
			// mob just shrinks the loot ledger, which auto-caps realised for those items
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

	/**
	 * Upsert a run by its {@code start} - replace the matching {@code history.jsonl} line, else
	 * append. Every run lands here: an explicit Start/Stop has a unique start so it just appends,
	 * and the always-on ambient run is checkpointed periodically (to make its loot sale-eligible
	 * before it Stops) then replaced in place on Stop rather than duplicated. A full rewrite; the
	 * file is small.
	 */
	void checkpoint(RunRecord entry, Consumer<Snapshot> cb)
	{
		run(() ->
		{
			int idx = -1;
			for (int i = 0; i < entries.size(); i++)
			{
				if (java.util.Objects.equals(entries.get(i).getStart(), entry.getStart()))
				{
					idx = i;
					break;
				}
			}
			if (idx >= 0)
			{
				entries.set(idx, entry);
			}
			else
			{
				entries.add(entry);
			}
			try
			{
				Files.createDirectories(dir());
				rewrite();
			}
			catch (IOException e)
			{
				log.warn("could not checkpoint run", e);
			}
			publish(cb);
		});
	}

	// ------------------------------------------------------------------ realised GP

	/** The GE slot state as it stood at the last {@link #load()} - the plugin seeds its
	 *  {@link GeSaleTracker} from this once, so an offline-completed sale isn't double-counted. */
	Map<Integer, GeSaleTracker.SlotState> geSlots()
	{
		return geSlots;
	}

	/** Persist the current GE slot state (called from the client thread with a fresh copy). */
	void saveGeSlots(Map<Integer, GeSaleTracker.SlotState> slots)
	{
		run(() ->
		{
			try
			{
				Files.createDirectories(dir());
				Files.write(geSlotsFile(), gson.toJson(slots).getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
			catch (IOException e)
			{
				log.warn("could not write GE slot state", e);
			}
		});
	}

	/**
	 * A completed GE sell fill. Attribution is <b>global by item</b>: the loot ledger knows
	 * you have ever looted {@code L} of this item (summed across every run, any mob); a sale
	 * counts toward realised only up to {@code L − alreadyRealised}. Selling more than that
	 * (bank stock) is dropped and the fill's proceeds pro-rated to the counted quantity.
	 */
	void applySale(GeSaleTracker.Sale sale, Consumer<Snapshot> cb)
	{
		run(() ->
		{
			if (sale == null || sale.getQty() <= 0)
			{
				return;
			}
			appendRealised(matchRealised("ge", sale.getItemId(), sale.getQty(),
				sale.getGross(), sale.getTax(), Instant.now().toString()), cb);
		});
	}

	/** A High Alchemy of a looted item - a realisation at the HA coin value, one unit, no tax. */
	void applyAlch(int itemId, long haCoins, String mob, String preferRunStart, String at, Consumer<Snapshot> cb)
	{
		run(() -> appendRealised(matchRealised("alch", itemId, 1, haCoins, 0, at), cb));
	}

	/**
	 * Global-by-item realisation match. {@code want} units sold for {@code gross} (before
	 * {@code tax}); count up to what the loot ledger still has unrealised for {@code itemId},
	 * pro-rate the proceeds to that, return a {@link RealisedEntry} (or null - never looted,
	 * or already fully realised).
	 */
	private RealisedEntry matchRealised(String kind, int itemId, long want, long gross, long tax, String at)
	{
		if (want <= 0 || itemId <= 0 || itemId == COINS || itemId == PLATINUM)
		{
			return null;
		}
		final long[] looted = lootedOf(entries, itemId);   // {qty, gp}
		final long avail = looted[0] - realisedQtyOf(realised, itemId);
		if (looted[0] <= 0 || avail <= 0)
		{
			return null;
		}
		final long take = Math.min(avail, want);
		final long unit = looted[1] / looted[0];
		final long g = split(gross, want, take);
		final long t = split(tax, want, take);
		return new RealisedEntry(REALISED_SCHEMA, kind, null, mobHint(entries, itemId),
			itemId, take, g, t, g - t, unit * take, at);
	}

	private void appendRealised(RealisedEntry re, Consumer<Snapshot> cb)
	{
		if (re == null)
		{
			return;
		}
		try
		{
			Files.createDirectories(dir());
			Files.write(realisedFile(), (gson.toJson(re) + "\n").getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e)
		{
			log.warn("could not append realised GP", e);
		}
		realised.add(re);
		publish(cb);
	}

	/**
	 * A bank / deposit-box move of {@code signedQty} of {@code itemId} ({@code +} = deposited,
	 * {@code -} = withdrawn). A deposit is FIFO-attributed to run loot not already realised or
	 * banked; a withdrawal reduces the banked quantity FIFO. {@code preferRunStart} (nullable)
	 * is tried before the oldest-first walk.
	 */
	void applyBankMove(int itemId, long signedQty, String preferRunStart, Consumer<Snapshot> cb)
	{
		run(() ->
		{
			if (signedQty == 0 || itemId <= 0 || itemId == COINS || itemId == PLATINUM)
			{
				return;
			}
			final long[] looted = lootedOf(entries, itemId);
			if (looted[0] <= 0)
			{
				return;
			}
			final long unit = looted[1] / looted[0];
			final long take;
			if (signedQty > 0)
			{
				final long avail = looted[0] - realisedQtyOf(realised, itemId)
					- Math.max(0, bankedNetOf(banked, itemId));
				take = Math.min(avail, signedQty);
			}
			else
			{
				take = -Math.min(Math.max(0, bankedNetOf(banked, itemId)), -signedQty);
			}
			if (take == 0)
			{
				return;
			}
			final BankEntry be = new BankEntry(REALISED_SCHEMA, null, mobHint(entries, itemId),
				itemId, take, unit, Instant.now().toString());
			try
			{
				Files.createDirectories(dir());
				Files.write(bankedFile(), (gson.toJson(be) + "\n").getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			catch (IOException e)
			{
				log.warn("could not append banked move", e);
			}
			banked.add(be);
			publish(cb);
		});
	}

	/** {@code {qty, gp}} of {@code itemId} looted across every recorded run (any mob). */
	static long[] lootedOf(List<RunRecord> entries, int itemId)
	{
		long qty = 0;
		long gp = 0;
		for (RunRecord r : entries)
		{
			if (r == null || r.getPerMob() == null)
			{
				continue;
			}
			for (RunRecord.MobRun mr : r.getPerMob().values())
			{
				if (mr == null || mr.getItems() == null)
				{
					continue;
				}
				for (long[] it : mr.getItems())
				{
					if (it != null && it.length >= 2 && (int) it[0] == itemId && it[1] > 0)
					{
						qty += it[1];
						gp += it.length >= 3 ? it[2] : 0;
					}
				}
			}
		}
		return new long[]{qty, gp};
	}

	/** Total quantity of {@code itemId} already matched to a realisation (GE sale or alch). */
	private static long realisedQtyOf(List<RealisedEntry> realised, int itemId)
	{
		long q = 0;
		for (RealisedEntry re : realised)
		{
			if (re != null && re.getItemId() == itemId)
			{
				q += re.getQty();
			}
		}
		return q;
	}

	/** Net banked quantity of {@code itemId} (deposits minus withdrawals); can be negative transiently. */
	private static long bankedNetOf(List<BankEntry> banked, int itemId)
	{
		long q = 0;
		for (BankEntry be : banked)
		{
			if (be != null && be.getItemId() == itemId)
			{
				q += be.getQty();
			}
		}
		return q;
	}

	/** Best-effort mob label for the GE Sales log - the run holding the most looted qty of the
	 *  item. Display only, never used in a total. */
	private static String mobHint(List<RunRecord> entries, int itemId)
	{
		String best = null;
		long bestQty = 0;
		for (RunRecord r : entries)
		{
			if (r == null || r.getPerMob() == null)
			{
				continue;
			}
			for (Map.Entry<String, RunRecord.MobRun> me : r.getPerMob().entrySet())
			{
				final RunRecord.MobRun mr = me.getValue();
				if (me.getKey() == null || me.getKey().isEmpty() || mr == null || mr.getItems() == null)
				{
					continue;
				}
				for (long[] it : mr.getItems())
				{
					if (it != null && it.length >= 2 && (int) it[0] == itemId && it[1] > bestQty)
					{
						bestQty = it[1];
						best = me.getKey();
					}
				}
			}
		}
		return best;
	}

	/** {@code total * part / whole}, done as integer math that cannot overflow for realistic
	 *  coin amounts ({@code part <= whole}). */
	private static long split(long total, long whole, long part)
	{
		if (whole <= 0)
		{
			return 0;
		}
		return (total / whole) * part + ((total % whole) * part) / whole;
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
				Files.deleteIfExists(realisedFile());
				Files.deleteIfExists(bankedFile());
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
			realised.clear();
			banked.clear();
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
		snapshot = aggregate(entries, realised, banked);
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

	private Path realisedFile()
	{
		return dir().resolve(REALISED_NAME);
	}

	private Path bankedFile()
	{
		return dir().resolve(BANKED_NAME);
	}

	private Path geSlotsFile()
	{
		return dir().resolve(GE_SLOTS_NAME);
	}

	/** Read {@code realised.jsonl} into {@link #realised}. Malformed lines are skipped. */
	private void readRealisedFile() throws IOException
	{
		if (!Files.exists(realisedFile()))
		{
			return;
		}
		for (String line : Files.readAllLines(realisedFile(), StandardCharsets.UTF_8))
		{
			if (line == null || line.trim().isEmpty())
			{
				continue;
			}
			try
			{
				final RealisedEntry re = gson.fromJson(line, RealisedEntry.class);
				if (re != null && re.getItemId() > 0 && re.getQty() > 0)
				{
					realised.add(re);
				}
			}
			catch (RuntimeException ex)
			{
				log.debug("skipping malformed realised line", ex);
			}
		}
	}

	/** Read {@code banked.jsonl} into {@link #banked}. Malformed lines are skipped. */
	private void readBankedFile() throws IOException
	{
		if (!Files.exists(bankedFile()))
		{
			return;
		}
		for (String line : Files.readAllLines(bankedFile(), StandardCharsets.UTF_8))
		{
			if (line == null || line.trim().isEmpty())
			{
				continue;
			}
			try
			{
				final BankEntry be = gson.fromJson(line, BankEntry.class);
				if (be != null && be.getItemId() > 0 && be.getQty() != 0)
				{
					banked.add(be);
				}
			}
			catch (RuntimeException ex)
			{
				log.debug("skipping malformed banked line", ex);
			}
		}
	}

	private Map<Integer, GeSaleTracker.SlotState> readGeSlots()
	{
		try
		{
			if (Files.exists(geSlotsFile()))
			{
				final Map<Integer, GeSaleTracker.SlotState> m = gson.fromJson(
					new String(Files.readAllBytes(geSlotsFile()), StandardCharsets.UTF_8),
					new TypeToken<Map<Integer, GeSaleTracker.SlotState>>()
					{
					}.getType());
				if (m != null)
				{
					return m;
				}
			}
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("could not read GE slot state", ex);
		}
		return new HashMap<>();
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
		return aggregate(entries, java.util.Collections.emptyList(), java.util.Collections.emptyList());
	}

	static Snapshot aggregate(List<RunRecord> entries, List<RealisedEntry> realised)
	{
		return aggregate(entries, realised, java.util.Collections.emptyList());
	}

	static Snapshot aggregate(List<RunRecord> entries, List<RealisedEntry> realised, List<BankEntry> banked)
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
				// farm and slayer runs can attribute their wall-clock time to a kill rate; a
				// plain session mixes in too much unrelated downtime to be a fair per-kill time
				if (("farm".equals(e.getKind()) || "slayer".equals(e.getKind())) && mr.getKills() > 0)
				{
					a.farmSec += e.getDurationSec();
					a.farmKills += mr.getKills();
				}
				a.runList.add(new RunRow(e.getStart(), e.getDurationSec(), e.getKind(),
					mr.getKills(), mr.getGained(), mr.getCost(), mr.getGained() - mr.getCost()));
			});
		}

		// GE Sales log rows (per entry, newest first) + realised totals grouped by item id
		final List<SaleRow> sales = new ArrayList<>();
		final Map<Integer, long[]> realisedByItem = new HashMap<>();   // id -> {qty, net, tax, gross}
		if (realised != null)
		{
			for (RealisedEntry re : realised)
			{
				if (re == null)
				{
					continue;
				}
				sales.add(new SaleRow(re.getSoldAt(), re.getItemId(), re.getQty(), re.getGross(),
					re.getTax(), re.getNet(), re.kindOrGe(),
					re.getMob() == null ? UNATTRIBUTED : re.getMob()));
				final long[] r = realisedByItem.computeIfAbsent(re.getItemId(), k -> new long[4]);
				r[0] += re.getQty();
				r[1] += re.getNet();
				r[2] += re.getTax();
				r[3] += re.getGross();
			}
		}
		sales.sort(Comparator.comparing((SaleRow s) -> s.getAt() == null ? "" : s.getAt()).reversed());

		final Map<Integer, Long> bankedByItem = new HashMap<>();
		if (banked != null)
		{
			for (BankEntry be : banked)
			{
				if (be != null)
				{
					bankedByItem.merge(be.getItemId(), be.getQty(), Long::sum);
				}
			}
		}

		// global-by-item loot ledger: total looted (any mob), then how much has been sold / banked
		final Map<Integer, long[]> lootedByItem = new LinkedHashMap<>();   // id -> {qty, gp}
		for (MobAcc a : mobs.values())
		{
			gained += a.gained;
			cost += a.cost;
			kills += a.kills;
			for (long[] it : a.items.values())
			{
				final long[] agg = lootedByItem.computeIfAbsent((int) it[0], k -> new long[2]);
				agg[0] += it[1];
				agg[1] += it[2];
			}
		}

		final List<ItemStat> itemStats = new ArrayList<>();
		long realisedNet = 0;
		long realisedTax = 0;
		long soldValue = 0;
		long bankedValue = 0;
		long unsoldValue = 0;
		for (Map.Entry<Integer, long[]> e : lootedByItem.entrySet())
		{
			final int id = e.getKey();
			final long looted = e.getValue()[0];
			if (looted <= 0)
			{
				continue;
			}
			final long unit = e.getValue()[1] / looted;
			final long[] r = realisedByItem.getOrDefault(id, new long[4]);
			final long soldQty = r[0];
			final long effSold = Math.min(soldQty, looted);          // the cap
			final long net = soldQty > 0 ? split(r[1], soldQty, effSold) : 0;   // pro-rate over-sells
			final long taxP = soldQty > 0 ? split(r[2], soldQty, effSold) : 0;
			final long unsoldQty = looted - effSold;
			final long uVal = unsoldQty * unit;
			final long bankedNet = bankedByItem.getOrDefault(id, 0L);
			final long bankedQty = Math.max(0, Math.min(bankedNet, unsoldQty));
			realisedNet += net;
			realisedTax += taxP;
			soldValue += effSold * unit;
			unsoldValue += uVal;
			bankedValue += bankedQty * unit;
			itemStats.add(new ItemStat(id, looted, looted * unit, soldQty, net, taxP, bankedNet,
				effSold, unsoldQty, uVal));
		}
		itemStats.sort((x, y) -> Long.compare(y.getLootedValue(), x.getLootedValue()));
		final long actualNet = realisedNet + unsoldValue - cost;

		final List<MobStats> list = new ArrayList<>();
		mobs.forEach((name, a) -> list.add(new MobStats(
			name, a.runs, a.kills, a.deaths, a.gained - a.cost, a.cost, a.gained, a.dropped,
			a.kills > 0 ? (a.gained - a.cost) / a.kills : 0,
			a.farmKills > 0 ? a.farmSec / a.farmKills : 0,
			triples(a.items), a.runList)));
		// biggest net first, but the "not in combat" bucket always sinks to the bottom
		list.sort(Comparator.comparing((MobStats m) -> m.getName().isEmpty())
			.thenComparing(Comparator.comparingLong(MobStats::getNet).reversed()));

		return new Snapshot(entries.size(), gained - cost, gained, cost, kills, list,
			realisedNet, realisedTax, soldValue, bankedValue, sales, actualNet, itemStats);
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
		static final Snapshot EMPTY =
			new Snapshot(0, 0, 0, 0, 0, new ArrayList<>(), 0, 0, 0, 0, new ArrayList<>(), 0, new ArrayList<>());

		/** Number of runs recorded. */
		int runs;
		/** lifetime **potential** net = Σ (looted at snapshot) − cost. */
		long net;
		long gained;
		long cost;
		int kills;
		List<MobStats> mobs;
		/** lifetime realised proceeds net of tax (GE sales + High Alch), capped per item at what
		 *  was ever looted. */
		long realisedNet;
		/** lifetime GE tax paid on that loot. */
		long realisedTax;
		/** lifetime frozen projected value of the loot that has been sold / alched. */
		long soldValue;
		/** lifetime frozen projected value of unsold loot currently sitting in a bank. */
		long bankedValue;
		/** every GE sale / High Alch, newest first, for the History "GE Sales" view. */
		List<SaleRow> sales;
		/** lifetime **actual** net = realised (sold) + snapshot value of everything unsold − cost. */
		long actualNet;
		/** the global loot ledger, one row per item, looted-value descending. */
		List<ItemStat> items;
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

	/** One row of the global loot ledger: how much of an item you looted, and what became of it.
	 *  {@code soldQty} may exceed {@code lootedQty} (bank stock sold alongside); {@code effectiveSold}
	 *  is the capped figure and {@code soldNet} is already pro-rated to it. */
	@Value
	static class ItemStat
	{
		int itemId;
		long lootedQty;
		long lootedValue;
		long soldQty;
		long soldNet;
		long soldTax;
		long bankedNet;
		long effectiveSold;
		long unsoldQty;
		long unsoldValue;
	}

	/** One line of {@code realised.jsonl}: a GE sale / High Alch chunk matched to a run's loot. */
	@Value
	static class RealisedEntry
	{
		int schema;
		/** "ge" or "alch" - absent on round-18 lines, treated as "ge". */
		String kind;
		/** ISO instant the run started - the key into {@code history.jsonl}. */
		String runStart;
		String mob;
		int itemId;
		long qty;
		/** gross coins received for this chunk (before tax). */
		long gross;
		long tax;
		/** {@code gross - tax}. */
		long net;
		/** frozen projected value of {@code qty} of this item from that run. */
		long projected;
		String soldAt;

		String kindOrGe()
		{
			return kind == null || kind.isEmpty() ? "ge" : kind;
		}
	}

	/** One line of {@code banked.jsonl}: a bank / deposit-box move of a run's loot. */
	@Value
	static class BankEntry
	{
		int schema;
		String runStart;
		String mob;
		int itemId;
		/** signed: {@code +} deposited into a bank, {@code -} withdrawn back out. */
		long qty;
		/** frozen projected unit value of the item in that run. */
		long unit;
		String at;
	}

	/** One GE sale / alch, for the History "GE Sales" view. */
	@Value
	static class SaleRow
	{
		String at;
		int itemId;
		long qty;
		long gross;
		long tax;
		long net;
		/** "ge" or "alch". */
		String kind;
		String mob;
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
