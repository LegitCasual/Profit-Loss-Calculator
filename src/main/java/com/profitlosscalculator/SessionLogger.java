/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Appends one JSON object per line to
 * {@code .runelite/profit-loss-calculator/session-<timestamp>.jsonl} for the life of a
 * session. All disk IO runs on a private single-thread executor so it never touches the
 * client thread; the executor also keeps writes ordered.
 */
@Slf4j
class SessionLogger
{
	private static final String DIR_NAME = "profit-loss-calculator";
	private static final DateTimeFormatter FILE_TS =
		DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

	private final Gson gson;
	private ExecutorService executor;
	private Writer writer;
	private Path path;
	private String sessionId;

	@Inject
	SessionLogger(Gson gson)
	{
		this.gson = gson;
	}

	/** Stable id for the session in progress - the file-name timestamp stem. */
	String sessionId()
	{
		return sessionId;
	}

	synchronized void open(Instant start)
	{
		sessionId = FILE_TS.format(start);
		executor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "profit-loss-calculator-log");
			t.setDaemon(true);
			return t;
		});
		final Path target = RuneLite.RUNELITE_DIR.toPath().resolve(DIR_NAME)
			.resolve("session-" + sessionId + ".jsonl");
		executor.execute(() ->
		{
			try
			{
				Files.createDirectories(target.getParent());
				writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				path = target;
			}
			catch (IOException e)
			{
				log.warn("could not open session log {}", target, e);
			}
		});
	}

	/** Start a line builder already stamped with the current time. */
	Line line(String event)
	{
		return new Line(event);
	}

	Path path()
	{
		return path;
	}

	synchronized void close()
	{
		if (executor == null)
		{
			return;
		}
		final ExecutorService ex = executor;
		executor = null;
		ex.execute(() ->
		{
			try
			{
				if (writer != null)
				{
					writer.flush();
					writer.close();
				}
			}
			catch (IOException e)
			{
				log.warn("could not close session log", e);
			}
			finally
			{
				writer = null;
			}
		});
		// graceful: let the queued close (and any trailing writes) drain, but never block
		ex.shutdown();
	}

	private void write(Map<String, Object> fields)
	{
		final ExecutorService ex = executor;
		if (ex == null)
		{
			return;
		}
		final String json = gson.toJson(fields);
		ex.execute(() ->
		{
			try
			{
				if (writer != null)
				{
					writer.write(json);
					writer.write('\n');
					writer.flush();
				}
			}
			catch (IOException e)
			{
				log.warn("session log write failed", e);
			}
		});
	}

	/** Fluent builder for one JSONL record. Call {@link #submit()} to queue it. */
	class Line
	{
		private final Map<String, Object> fields = new LinkedHashMap<>();

		private Line(String event)
		{
			fields.put("ts", Instant.now().toString());
			fields.put("event", event);
		}

		Line put(String key, Object value)
		{
			if (value != null)
			{
				fields.put(key, value);
			}
			return this;
		}

		void submit()
		{
			write(fields);
		}
	}
}
