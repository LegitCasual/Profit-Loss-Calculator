/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.sessioncosttracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Session Cost Tracker",
	description = "Tracks the gp a play session costs - supplies, spells, teleports, ammo and deaths - with a boss kill tally and a JSON log",
	tags = {"cost", "gp", "profit", "loss", "session", "boss", "supplies", "death"}
)
public class SessionCostTrackerPlugin extends Plugin implements SessionCostTrackerPanel.Controls
{
	/** Ticks after death during which we watch the containers for the item loss. Items can
	 *  be reclaimed almost instantly, so we track the *worst* dip, not a fixed snapshot. */
	private static final int DEATH_WINDOW_TICKS = 10;

	/** Per-tick inventory+equipment snapshots kept so a death can diff against the past. */
	private static final int STATE_HISTORY_TICKS = 6;

	/** How far back in that history the pre-death snapshot is taken (items are already
	 *  gone by the time ActorDeath fires, so "now" is useless). */
	private static final int PRE_DEATH_LOOKBACK_TICKS = 2;

	/** "Payment has been taken from your bank: 22,000 x Coins" and friends. */
	private static final Pattern RECLAIM_PAYMENT = Pattern.compile(
		"Payment has been taken from your (?:bank|inventory|Death'?s? Coffer)[:.]?\\s*([\\d,]+)\\s*(?:x\\s*)?[Cc]oins?");

	/** Confirmation that items came back from a grave / Death's Office. */
	private static final Pattern RECLAIM_SUCCESS = Pattern.compile(
		"retrieved .*(?:gravestone|Death'?s Office)|items have been returned to you|retrieved (?:everything|all) ");

	/** "Some of your dropped items are being held in a gravestone ...". */
	private static final Pattern GRAVE_CREATED = Pattern.compile(
		"held in a grave|gravestone.*near where you died");

	/** Ignore a second boss "death" within this many ticks - multi-part bosses (Olm, Hydra). */
	private static final int BOSS_KILL_DEBOUNCE_TICKS = 8;

	private static final int AMMO_SLOT = EquipmentInventorySlot.AMMO.getSlotIdx();
	private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int[] CANNONBALL_IDS = {ItemID.MCANNONBALL, ItemID.GRANITE_CANNONBALL};

	/** parenthesised charge count at the end of an item name: "Ring of dueling(8)",
	 *  "Amulet of glory(t4)". */
	private static final Pattern CHARGE_SUFFIX = Pattern.compile("\\(t?(\\d+)\\)\\s*$");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SessionCostTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SessionLogger logger;

	@Inject
	private ConsumableCostService consumableCostService;

	@Inject
	private SpellCostService spellCostService;

	@Inject
	private DeathCostService deathCostService;

	@Inject
	private SessionCostTrackerOverlay overlay;

	private SessionCostTrackerPanel panel;
	private NavigationButton navButton;

	/** The session being tracked (running or paused), or the last finished one for the panel. */
	private Session session;
	/** True once {@link #stopSession} has finalised {@link #session} - it is now read-only. */
	private boolean sessionFinished;

	private Map<Integer, Integer> lastKnownInv = Collections.emptyMap();
	private Map<Integer, Integer> lastKnownWorn = Collections.emptyMap();

	// spell-cast de-dup within a game tick
	private int lastCastTick = -1;
	private int lastCastComponent = -1;

	// consumable de-dup: one physical click can register twice in a tick, but you can't
	// use the same item twice in a tick (combo-eating a different item is still allowed)
	private int lastConsumeTick = -1;
	private int lastConsumeItemId = -1;

	// ammo + teleport tracking
	private final AmmoTracker ammoTracker = new AmmoTracker();
	private boolean bankOpen;
	/** teleport-jewellery variant id -&gt; charges it represents; built once, first session. */
	private Map<Integer, Integer> teleChargesById = Collections.emptyMap();
	/** teleport-jewellery group base -&gt; gp value of one charge (best tradeable variant / its charges). */
	private Map<Integer, Long> teleCostPerCharge = Collections.emptyMap();
	/** union(inv, worn) at the end of last tick - teleport charge drops are read once per tick. */
	private Map<Integer, Integer> prevTickItems = Collections.emptyMap();
	/** item id -&gt; is it a teleport tablet/scroll (stackable, name mentions "teleport"). */
	private final Map<Integer, Boolean> teleTabCache = new HashMap<>();
	/** set by ammo reconciliation, flushed to the panel once per tick to avoid churn */
	private boolean viewDirty;
	/** tick the tracked boss last died - debounces multi-part boss deaths */
	private int lastBossKillTick = -100;

	// pending death tracking
	private Map<Integer, Integer> preDeathState = Collections.emptyMap();
	private DeathEntry pendingDeath;
	private int deathWindowTicks;
	private boolean deathGraveConfirmed;
	/** itemId -&gt; the largest quantity ever seen missing vs pre-death during the window. */
	private final Map<Integer, Integer> deathLossAcc = new HashMap<>();

	// most recent Item Retrieval Service payment seen in chat
	private long recentReclaimFee = -1;
	private int recentReclaimFeeTick = -1;

	/** union(inv, worn) as it stood at the end of each of the last few ticks. */
	private final Deque<Map<Integer, Integer>> stateHistory = new ArrayDeque<>();

	private final Map<Integer, DeathEntry> pendings = new LinkedHashMap<>();

	private volatile SessionCostTrackerPanel.View currentView =
		new SessionCostTrackerPanel.View(false, false, false, 0, 0, "", 0,
			Collections.emptyList(), Collections.emptyList());

	@Provides
	SessionCostTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SessionCostTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new SessionCostTrackerPanel(this);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Session Cost Tracker")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		refreshView();
	}

	@Override
	protected void shutDown()
	{
		if (session != null && !sessionFinished)
		{
			stopSession();
		}
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		logger.close();
		panel = null;
		session = null;
		pendings.clear();
	}

	// ------------------------------------------------------------------ panel controls

	/** The flip button: start when idle, pause when running, resume when paused. */
	@Override
	public void onStartPauseResume()
	{
		clientThread.invoke(() ->
		{
			if (session == null || sessionFinished)
			{
				startSession();
			}
			else
			{
				setPaused(!session.isPaused());
			}
		});
	}

	@Override
	public void onStop()
	{
		clientThread.invoke(() ->
		{
			if (session != null && !sessionFinished)
			{
				stopSession();
			}
		});
	}

	@Override
	public void onRestart()
	{
		clientThread.invoke(() ->
		{
			if (session != null && !sessionFinished)
			{
				stopSession();
			}
			startSession();
		});
	}

	@Override
	public void onBossKill()
	{
		clientThread.invoke(() ->
		{
			if (session == null || sessionFinished)
			{
				return;
			}
			session.addBossKill();
			logger.line("boss_kill").put("source", "manual").put("count", session.getBossKills()).submit();
			refreshView();
		});
	}

	@Override
	public void onBossName(String name)
	{
		final String trimmed = name == null ? "" : name.trim();
		configManager.setConfiguration(SessionCostTrackerConfig.GROUP, "bossName", trimmed);
		clientThread.invoke(this::refreshView);
	}

	@Override
	public void onConfirmDeath(int deathId, long fee)
	{
		clientThread.invoke(() -> resolveDeath(deathId, Math.max(0, fee), false));
	}

	@Override
	public void onGravestone(int deathId)
	{
		clientThread.invoke(() -> resolveDeath(deathId, 0, true));
	}

	// ------------------------------------------------------------------ session lifecycle

	private void startSession()
	{
		final Instant now = Instant.now();

		lastKnownInv = ContainerSnapshot.of(client.getItemContainer(InventoryID.INV));
		lastKnownWorn = ContainerSnapshot.of(client.getItemContainer(InventoryID.WORN));
		pendings.clear();
		pendingDeath = null;
		deathWindowTicks = 0;
		deathGraveConfirmed = false;
		deathLossAcc.clear();
		recentReclaimFee = -1;
		recentReclaimFeeTick = -1;
		lastBossKillTick = -100;
		stateHistory.clear();
		pushStateHistory();

		teleTabCache.clear();
		bankOpen = client.getWidget(InterfaceID.Bankmain.ITEMS) != null;
		prevTickItems = ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		if (teleChargesById.isEmpty())
		{
			teleChargesById = buildTeleChargeMap();
			teleCostPerCharge = buildTelePerChargeCost();
		}
		ammoTracker.reset(ammoOwned());

		if (config.writeSessionFile())
		{
			logger.open(now);
		}
		logger.line("session_start")
			.put("startedAt", now.toString())
			.put("boss", config.bossName().isEmpty() ? null : config.bossName())
			.submit();

		session = new Session(now);
		sessionFinished = false;
		refreshView();
	}

	private void setPaused(boolean paused)
	{
		if (session == null || sessionFinished || session.isPaused() == paused)
		{
			return;
		}
		session.setPaused(paused);
		// re-base the trackers past the paused stretch so banking / afk doesn't accrue
		ammoTracker.reset(ammoOwned());
		prevTickItems = ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		logger.line(paused ? "session_pause" : "session_resume").submit();
		refreshView();
	}

	private void stopSession()
	{
		if (session == null || sessionFinished)
		{
			return;
		}
		if (deathWindowTicks > 0)
		{
			deathWindowTicks = 0;
			finalizeDeath();
		}

		for (DeathEntry e : pendings.values())
		{
			if (e.getLostItems().isEmpty())
			{
				continue;
			}
			if (e.getState() == DeathEntry.State.PENDING)
			{
				e.setState(DeathEntry.State.LOST);
				e.setResolvedCost(e.getFullValue());
				e.setUserConfirmed(true);
				logResolved(e, "lost");
			}
			else if (e.getState() == DeathEntry.State.RETURNED && !e.isUserConfirmed()
				&& config.autoConfirmDeathFeeOnStop())
			{
				e.setResolvedCost(e.getEstimatedFee());
				e.setUserConfirmed(true);
				logResolved(e, "auto-fee");
			}
		}

		session.setEndTime(Instant.now());
		sessionFinished = true;

		final SessionSummary summary = SessionSummary.of(session);
		logger.line("session_stop")
			.put("durationSeconds", java.time.Duration.between(session.getStartTime(), session.getEndTime()).getSeconds())
			.put("summary", summary.toJsonFields())
			.put("ammoItems", ammoItemsLog(session.getAmmoStats()))
			.submit();
		final Path file = logger.path();
		logger.close();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			final StringBuilder msg = new StringBuilder("[Session Cost Tracker] Session total: ")
				.append(gp(session.total()));
			if (session.getBossKills() > 0)
			{
				msg.append(" over ").append(session.getBossKills()).append(" kill(s)");
			}
			if (session.atRiskTotal() > 0)
			{
				msg.append(" (+").append(gp(session.atRiskTotal())).append(" at risk)");
			}
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg.toString(), null);
		}
		if (file != null)
		{
			log.debug("session log written to {}", file);
		}
		log.debug("session summary:\n{}", summary.toPlainText());
		refreshView();
	}

	private void resolveDeath(int deathId, long fee, boolean gravestone)
	{
		final DeathEntry e = pendings.get(deathId);
		if (e == null)
		{
			return;
		}
		e.setState(DeathEntry.State.RETURNED);
		e.setResolvedCost(fee);
		e.setEstimatedFee(fee);
		e.setUserConfirmed(true);
		logResolved(e, gravestone ? "gravestone" : "fee-confirmed");
		refreshView();
	}

	private void logResolved(DeathEntry e, String outcome)
	{
		logger.line("death_resolved")
			.put("deathId", e.getId())
			.put("outcome", outcome)
			.put("gp", e.getResolvedCost())
			.submit();
	}

	// ------------------------------------------------------------------ events

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			bankOpen = false;
		}
	}

	/** A session exists and has not been stopped (may be paused). */
	private boolean tracking()
	{
		return session != null && !sessionFinished;
	}

	/** A session is running and not paused - costs should accrue. */
	private boolean accruing()
	{
		return tracking() && !session.isPaused();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final int id = event.getContainerId();
		if (id != InventoryID.INV && id != InventoryID.WORN)
		{
			return;
		}

		if (id == InventoryID.INV)
		{
			lastKnownInv = ContainerSnapshot.of(event.getItemContainer());
		}
		else
		{
			lastKnownWorn = ContainerSnapshot.of(event.getItemContainer());
		}

		if (!tracking())
		{
			return;
		}

		if (deathWindowTicks > 0)
		{
			accumulateDeathLoss();
		}
		checkContainerReturn();

		reconcileAmmo(deathWindowTicks == 0 && !bankOpen && !session.isPaused());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!tracking())
		{
			return;
		}
		final int vp = event.getVarpId();
		if (vp == VarPlayerID.ROCKTHROWER
			|| vp == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
			|| vp == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)
		{
			reconcileAmmo(deathWindowTicks == 0 && !bankOpen && !session.isPaused());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!tracking())
		{
			return;
		}
		updateBankState();
		pushStateHistory();

		// teleport charge drops are read once per tick so equipping / unequipping jewellery
		// (item moves inv<->worn within a tick, net zero) never looks like a teleport.
		// Skip while banking / paused - a deposit would look like spending the last charge.
		final Map<Integer, Integer> curItems = ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		if (deathWindowTicks == 0 && !bankOpen && !session.isPaused() && !prevTickItems.isEmpty())
		{
			detectTeleports(prevTickItems, curItems);
		}
		prevTickItems = curItems;

		if (deathWindowTicks > 0)
		{
			accumulateDeathLoss();
			if (--deathWindowTicks <= 0)
			{
				finalizeDeath();
			}
		}

		if (viewDirty)
		{
			viewDirty = false;
			refreshView();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!tracking())
		{
			return;
		}
		final ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM
			&& type != ChatMessageType.MESBOX)
		{
			return;
		}
		final String msg = Text.removeTags(event.getMessage());

		final Matcher payment = RECLAIM_PAYMENT.matcher(msg);
		if (payment.find())
		{
			try
			{
				recentReclaimFee = Long.parseLong(payment.group(1).replace(",", ""));
				recentReclaimFeeTick = client.getTickCount();
			}
			catch (NumberFormatException ignored)
			{
				// leave it
			}
			return;
		}

		if (GRAVE_CREATED.matcher(msg).find())
		{
			deathGraveConfirmed = true;
			return;
		}

		if (RECLAIM_SUCCESS.matcher(msg).find())
		{
			resolveOldestPendingFromReclaim();
		}
	}

	private void pushStateHistory()
	{
		stateHistory.addLast(ContainerSnapshot.union(lastKnownInv, lastKnownWorn));
		while (stateHistory.size() > STATE_HISTORY_TICKS)
		{
			stateHistory.removeFirst();
		}
	}

	/** Inventory+equipment as it stood a couple of ticks before now - i.e. before the
	 *  death stripped it. Falls back to the current state only if there is no history. */
	private Map<Integer, Integer> preDeathSnapshot()
	{
		if (stateHistory.isEmpty())
		{
			return ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		}
		final List<Map<Integer, Integer>> h = new ArrayList<>(stateHistory);
		final int idx = Math.max(0, h.size() - 1 - PRE_DEATH_LOOKBACK_TICKS);
		return h.get(idx);
	}

	/**
	 * Bank open/close, read from the item-container widget each tick - {@code WidgetClosed}
	 * doesn't fire reliably for the bank. While open, ammo/teleport accrual pauses; on close
	 * the trackers re-base past whatever was deposited/withdrawn/swapped.
	 */
	private void updateBankState()
	{
		final boolean nowBank = client.getWidget(InterfaceID.Bankmain.ITEMS) != null;
		if (nowBank == bankOpen)
		{
			return;
		}
		bankOpen = nowBank;
		if (!nowBank)
		{
			ammoTracker.reset(ammoOwned());
			prevTickItems = ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!accruing())
		{
			return;
		}
		final String option = event.getMenuOption();
		if ("Eat".equals(option) || "Drink".equals(option))
		{
			handleConsumable(event);
			return;
		}
		handleSpell(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!tracking())
		{
			return;
		}
		final Actor actor = event.getActor();
		if (actor instanceof NPC)
		{
			if (accruing())
			{
				handleBossKill((NPC) actor);
			}
			return;
		}
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		preDeathState = preDeathSnapshot();
		deathLossAcc.clear();
		deathGraveConfirmed = false;
		deathWindowTicks = DEATH_WINDOW_TICKS;

		pendingDeath = new DeathEntry(session.nextDeathId(), Instant.now(), Collections.emptyMap(), 0);
		session.add(pendingDeath);
		pendings.put(pendingDeath.getId(), pendingDeath);
		accumulateDeathLoss();
		logger.line("death").put("deathId", pendingDeath.getId()).submit();
		refreshView();
	}

	/** A tracked boss died: bump the session kill tally. */
	private void handleBossKill(NPC npc)
	{
		final String boss = config.bossName().trim();
		if (boss.isEmpty())
		{
			return;
		}
		final String name = npc.getName();
		if (name == null || !name.toLowerCase().contains(boss.toLowerCase()))
		{
			return;
		}
		final int tick = client.getTickCount();
		if (tick - lastBossKillTick < BOSS_KILL_DEBOUNCE_TICKS)
		{
			return;
		}
		lastBossKillTick = tick;

		session.addBossKill();
		logger.line("boss_kill")
			.put("source", "auto")
			.put("boss", name)
			.put("count", session.getBossKills())
			.submit();
		refreshView();
	}

	/** Records, per item, the largest shortfall vs the pre-death kit seen so far. Runs on
	 *  every container change and every tick during the death window, so a near-instant
	 *  grave reclaim still leaves a trace. */
	private void accumulateDeathLoss()
	{
		final Map<Integer, Integer> current = ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		preDeathState.forEach((id, preQty) ->
		{
			final int missing = preQty - current.getOrDefault(id, 0);
			if (missing > deathLossAcc.getOrDefault(id, 0))
			{
				deathLossAcc.put(id, missing);
			}
		});
	}

	private void finalizeDeath()
	{
		final DeathEntry e = pendingDeath;
		pendingDeath = null;
		if (e == null || e.getState() != DeathEntry.State.PENDING)
		{
			return;
		}

		// ammo lost to the death is the death handler's to price - snap the ammo baseline
		// past it so a later reclaim doesn't read as a restock and the drop isn't double-charged
		ammoTracker.reconcile(ammoOwned(), false);

		final Map<Integer, Integer> lost = new HashMap<>();
		deathLossAcc.forEach((id, qty) ->
		{
			if (qty > 0)
			{
				lost.put(id, qty);
			}
		});

		// grave message but we never caught the container dip -> assume the whole kit went in
		if (lost.isEmpty() && deathGraveConfirmed)
		{
			lost.putAll(preDeathState);
		}

		if (lost.isEmpty())
		{
			logger.line("death_no_loss")
				.put("deathId", e.getId())
				.put("preDeathItems", preDeathState.size())
				.submit();
			refreshView();
			return;
		}

		final long full = deathCostService.geValue(lost);
		e.setLoss(lost, full);
		e.setEstimatedFee(deathCostService.graveFeeEstimate(lost, isIronman()));
		logger.line("death_pending")
			.put("deathId", e.getId())
			.put("fullValue", full)
			.put("estimatedFee", e.getEstimatedFee())
			.put("items", namedItems(lost))
			.submit();

		// a reclaim may already have happened while the window was open
		if (recentReclaimFeeTick >= 0 && client.getTickCount() - recentReclaimFeeTick <= DEATH_WINDOW_TICKS)
		{
			applyReclaim(e, recentReclaimFee);
		}
		refreshView();
	}

	/** A "retrieved from gravestone / Death's Office" line fired - resolve the oldest
	 *  still-pending death with whatever reclaim fee we last saw in chat. */
	private void resolveOldestPendingFromReclaim()
	{
		// a retrieval line means a grave existed - let finalizeDeath fall back to the full
		// kit if it never managed to catch the container dip
		deathGraveConfirmed = true;
		if (deathWindowTicks > 0)
		{
			deathWindowTicks = 0;
			finalizeDeath();
		}
		for (DeathEntry e : pendings.values())
		{
			if (e.getState() == DeathEntry.State.PENDING && !e.getLostItems().isEmpty())
			{
				final long fee = (recentReclaimFeeTick >= 0
					&& client.getTickCount() - recentReclaimFeeTick <= DEATH_WINDOW_TICKS)
					? recentReclaimFee : 0L;
				applyReclaim(e, fee);
				break;
			}
		}
		recentReclaimFee = -1;
		recentReclaimFeeTick = -1;
	}

	private void applyReclaim(DeathEntry e, long fee)
	{
		e.setState(DeathEntry.State.RETURNED);
		e.setResolvedCost(Math.max(0, fee));
		e.setEstimatedFee(Math.max(0, fee));
		e.setUserConfirmed(true);
		logResolved(e, "reclaimed");
		refreshView();
	}

	/** Container-diff fallback: items visibly back but no chat resolution seen. */
	private void checkContainerReturn()
	{
		if (pendings.isEmpty())
		{
			return;
		}
		final Map<Integer, Integer> current = ContainerSnapshot.union(lastKnownInv, lastKnownWorn);
		boolean changed = false;
		for (DeathEntry e : pendings.values())
		{
			if (e.getState() != DeathEntry.State.PENDING || e.getLostItems().isEmpty())
			{
				continue;
			}
			if (!ContainerSnapshot.covers(current, e.getLostItems()))
			{
				continue;
			}
			e.setState(DeathEntry.State.RETURNED);
			e.setEstimatedFee(deathCostService.graveFeeEstimate(e.getLostItems(), isIronman()));
			e.setUserConfirmed(false);
			logger.line("death_returned")
				.put("deathId", e.getId())
				.put("via", "container")
				.put("estimatedFee", e.getEstimatedFee())
				.submit();
			changed = true;
		}
		if (changed)
		{
			refreshView();
		}
	}

	private boolean isIronman()
	{
		// VarbitID.IRONMAN: 0 normal, 1 IM, 2 UIM, 3 HCIM, 4 GIM, 5 HC GIM - all get the fee discount
		return client.getVarbitValue(VarbitID.IRONMAN) > 0;
	}

	private void handleConsumable(MenuOptionClicked event)
	{
		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			final Widget widget = event.getWidget();
			if (widget != null)
			{
				itemId = widget.getItemId();
			}
		}
		final int tick = client.getTickCount();
		if (tick == lastConsumeTick && itemId == lastConsumeItemId)
		{
			return;
		}

		final ConsumableCostService.Consumed c = consumableCostService.price(itemId, config.potionDoseAware());
		if (c == null)
		{
			return;
		}
		lastConsumeTick = tick;
		lastConsumeItemId = itemId;
		session.add(new CostEvent(CostEvent.Type.CONSUMABLE, Instant.now(),
			c.getItemId(), 1, c.getGp(), c.getName(), null));
		logger.line("consumable")
			.put("itemId", c.getItemId())
			.put("item", c.getName())
			.put("qty", 1)
			.put("gp", c.getGp())
			.submit();
		refreshView();
	}

	// ------------------------------------------------------------------ ammo

	/** Every ammo item id the plugin can see a quantity for, and that quantity. */
	private Map<Integer, Long> ammoOwned()
	{
		final Map<Integer, Long> owned = new HashMap<>();

		final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			addSlotAmmo(owned, worn.getItem(AMMO_SLOT), false);
			addSlotAmmo(owned, worn.getItem(WEAPON_SLOT), true);
		}

		// inventory copies of anything already tracked, so unequipping ammo to swap types
		// nets to zero instead of reading as a full-stack loss
		for (int id : ammoTracker.tracked())
		{
			final long inv = lastKnownInv.getOrDefault(id, 0);
			if (inv > 0)
			{
				owned.merge(id, inv, Long::sum);
			}
		}

		// cannonballs: loose in the inventory plus whatever is loaded in the cannon
		final long loaded = Math.max(0, client.getVarpValue(VarPlayerID.ROCKTHROWER));
		for (int cb : CANNONBALL_IDS)
		{
			final long inv = lastKnownInv.getOrDefault(cb, 0);
			final long extra = cb == ItemID.MCANNONBALL ? loaded : 0;
			if (inv + extra > 0)
			{
				owned.merge(cb, inv + extra, Long::sum);
			}
		}

		// Dizana's quiver
		final int quiverId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		final int quiverQty = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		if (quiverId > 0 && quiverQty > 0)
		{
			owned.merge(quiverId, (long) quiverQty, Long::sum);
		}

		return owned;
	}

	private void addSlotAmmo(Map<Integer, Long> owned, Item item, boolean stackableOnly)
	{
		if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
		{
			return;
		}
		if (stackableOnly)
		{
			final ItemComposition comp = itemManager.getItemComposition(item.getId());
			if (comp == null || !comp.isStackable())
			{
				return;
			}
		}
		owned.merge(item.getId(), (long) item.getQuantity(), Long::sum);
	}

	private void reconcileAmmo(boolean accrue)
	{
		if (session == null)
		{
			return;
		}
		// always reconcile so the baseline stays current; only charge when tracking is on
		// and not paused (death window / bank open)
		final boolean moved = ammoTracker.reconcile(ammoOwned(), accrue && config.trackAmmo());
		if (!moved)
		{
			return;
		}
		final Map<Integer, long[]> stats = ammoTracker.stats();
		long gp = 0;
		for (Map.Entry<Integer, long[]> e : stats.entrySet())
		{
			gp += e.getValue()[2] * Math.max(0, itemManager.getItemPrice(e.getKey()));
		}
		session.setAmmo(stats, gp);
		viewDirty = true;
	}

	// ------------------------------------------------------------------ teleports

	private Map<Integer, Integer> buildTeleChargeMap()
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int id : TeleportCharges.variantIds())
		{
			final ItemComposition comp = itemManager.getItemComposition(id);
			m.put(id, comp != null ? parseCharges(comp.getName()) : 0);
		}
		return m;
	}

	/**
	 * group base -&gt; gp of one charge, taken from the highest-charge variant that actually
	 * has a GE price. Used when a specific charge tier is untradeable (glory(5)/(6)) so the
	 * marginal price would come out zero.
	 */
	private Map<Integer, Long> buildTelePerChargeCost()
	{
		final Map<Integer, Long> perCharge = new HashMap<>();
		TeleportCharges.groups().forEach((base, variants) ->
		{
			int bestCharges = 0;
			long best = 0;
			for (int id : variants)
			{
				final int ch = teleChargesById.getOrDefault(id, 0);
				final long price = Math.max(0, itemManager.getItemPrice(id));
				if (ch > 0 && price > 0 && ch >= bestCharges)
				{
					bestCharges = ch;
					best = price / ch;
				}
			}
			if (best > 0)
			{
				perCharge.put(base, best);
			}
		});
		return perCharge;
	}

	private static int parseCharges(String name)
	{
		final Matcher m = CHARGE_SUFFIX.matcher(name);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	private void detectTeleports(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		if (!config.trackTeleports() || session == null || before.equals(after))
		{
			return;
		}
		boolean changed = false;

		for (TeleportCharges.Charge c : TeleportCharges.detect(before, after, teleChargesById))
		{
			final long gp = teleportCost(c);
			final String label = teleportLabel(c);
			session.add(new CostEvent(CostEvent.Type.TELEPORT, Instant.now(),
				c.getFromId(), (int) c.getChargesUsed(), gp, label, null));
			logger.line("teleport")
				.put("item", label)
				.put("charges", c.getChargesUsed())
				.put("gp", gp)
				.submit();
			changed = true;
		}

		// teleport tablets / scrolls: a stackable "* teleport" item that left the inventory
		for (Map.Entry<Integer, Integer> e : before.entrySet())
		{
			final int id = e.getKey();
			final int used = e.getValue() - after.getOrDefault(id, 0);
			if (used <= 0 || used > 3 || !isTeleportTab(id))
			{
				continue;
			}
			final String name = itemName(id);
			final long gp = Math.max(0, itemManager.getItemPrice(id)) * used;
			session.add(new CostEvent(CostEvent.Type.TELEPORT, Instant.now(),
				id, used, gp, name, null));
			logger.line("teleport")
				.put("itemId", id)
				.put("item", name)
				.put("qty", used)
				.put("gp", gp)
				.submit();
			changed = true;
		}

		if (changed)
		{
			refreshView();
		}
	}

	private boolean isTeleportTab(int id)
	{
		return teleTabCache.computeIfAbsent(id, k ->
		{
			final ItemComposition comp = itemManager.getItemComposition(k);
			return comp != null
				&& comp.isStackable()
				&& comp.getName().toLowerCase().contains("teleport");
		});
	}

	/**
	 * Marginal GE price of the charges used - {@code price(from) - price(to)} - when both
	 * tiers are tradeable; otherwise the per-charge fallback (untradeable glory(5)/(6) etc).
	 */
	private long teleportCost(TeleportCharges.Charge c)
	{
		final long fromPrice = Math.max(0, itemManager.getItemPrice(c.getFromId()));
		final long toPrice = c.getToId() > 0 ? Math.max(0, itemManager.getItemPrice(c.getToId())) : 0;
		if (fromPrice > 0 && (c.getToId() < 0 || toPrice > 0) && fromPrice >= toPrice)
		{
			return fromPrice - toPrice;
		}
		return teleCostPerCharge.getOrDefault(c.getBase(), 0L) * c.getChargesUsed();
	}

	private String teleportLabel(TeleportCharges.Charge c)
	{
		final String base = itemName(c.getBase()).replaceAll("\\s*\\(\\d+\\)\\s*$", "");
		final int from = teleChargesById.getOrDefault(c.getFromId(), 0);
		final int to = c.getToId() > 0 ? teleChargesById.getOrDefault(c.getToId(), 0) : 0;
		return base + " (" + from + "→" + to + ")";
	}

	private void handleSpell(MenuOptionClicked event)
	{
		final MenuAction action = event.getMenuAction();
		if (action == null)
		{
			return;
		}
		final int param1 = event.getParam1();

		if ("Cast".equals(event.getMenuOption())
			&& action != MenuAction.WIDGET_TARGET
			&& WidgetUtil.componentToInterface(param1) == InterfaceID.MAGIC_SPELLBOOK)
		{
			recordCast(param1);
			return;
		}

		switch (action)
		{
			case WIDGET_TARGET_ON_NPC:
			case WIDGET_TARGET_ON_PLAYER:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_WIDGET:
				final Widget selected = client.getSelectedWidget();
				if (selected != null
					&& WidgetUtil.componentToInterface(selected.getId()) == InterfaceID.MAGIC_SPELLBOOK)
				{
					recordCast(selected.getId());
				}
				break;
			default:
				break;
		}
	}

	private void recordCast(int componentId)
	{
		final Spell spell = Spell.byComponent(componentId);
		if (spell == null)
		{
			return;
		}
		final int tick = client.getTickCount();
		if (tick == lastCastTick && componentId == lastCastComponent)
		{
			return;
		}
		lastCastTick = tick;
		lastCastComponent = componentId;

		final SpellCostService.Priced priced = spellCostService.price(spell);
		session.add(new CostEvent(CostEvent.Type.SPELL, Instant.now(),
			-1, 1, priced.getGp(), spell.getDisplayName(), priced.getBreakdown()));
		logger.line("spell_cast")
			.put("spell", spell.getDisplayName())
			.put("gp", priced.getGp())
			.put("runes", priced.getBreakdown())
			.submit();
		refreshView();
	}

	// ------------------------------------------------------------------ view / helpers

	boolean isSessionActive()
	{
		return session != null && !sessionFinished;
	}

	SessionCostTrackerPanel.View currentView()
	{
		return currentView;
	}

	private void refreshView()
	{
		final SessionCostTrackerPanel.View view = buildView();
		currentView = view;
		final SessionCostTrackerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.render(view));
		}
	}

	private static final DateTimeFormatter LINE_TIME =
		DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private SessionCostTrackerPanel.View buildView()
	{
		if (session == null)
		{
			return new SessionCostTrackerPanel.View(false, false, false, 0, 0,
				config.bossName(), 0, Collections.emptyList(), Collections.emptyList());
		}

		final List<SessionCostTrackerPanel.DeathRow> deaths = new ArrayList<>();
		for (DeathEntry e : session.getDeaths())
		{
			if (e.getLostItems().isEmpty() || e.isCounted())
			{
				continue;
			}
			deaths.add(new SessionCostTrackerPanel.DeathRow(e.getId(),
				namedItemsText(e.getLostItems()), e.getEstimatedFee(), e.getFullValue(),
				e.getState() != DeathEntry.State.PENDING));
		}

		return new SessionCostTrackerPanel.View(
			!sessionFinished, session.isPaused(), sessionFinished,
			session.total(), session.atRiskTotal(),
			config.bossName(), session.getBossKills(),
			eventLines(session), deaths);
	}

	private List<SessionCostTrackerPanel.EventLine> eventLines(Session s)
	{
		final List<SessionCostTrackerPanel.EventLine> lines = new ArrayList<>();
		for (CostEvent ev : s.getEvents())
		{
			final String kind;
			final String label;
			switch (ev.getType())
			{
				case SPELL:
					kind = "spell";
					label = ev.getLabel();
					break;
				case TELEPORT:
					kind = "teleport";
					label = ev.getLabel();
					break;
				default:
					kind = "supplies";
					label = consumableLabel(ev.getLabel());
					break;
			}
			lines.add(new SessionCostTrackerPanel.EventLine(
				kind, LINE_TIME.format(ev.getTime()), label, ev.getGp(), breakdownText(ev.getDetail())));
		}
		for (DeathEntry d : s.getDeaths())
		{
			if (!d.isCounted())
			{
				continue;
			}
			final String label = d.getState() == DeathEntry.State.LOST
				? "Death - lost (not reclaimed)"
				: "Death - reclaimed";
			lines.add(new SessionCostTrackerPanel.EventLine(
				"death",
				LINE_TIME.format(d.getDeathTime()),
				label,
				d.getResolvedCost(),
				namedItemsText(d.getLostItems())));
		}
		lines.sort(Comparator.comparing(SessionCostTrackerPanel.EventLine::getTime));

		if (s.ammoTotal() > 0)
		{
			lines.add(new SessionCostTrackerPanel.EventLine(
				"ammo", "", "Ammo used", s.ammoTotal(), ammoBreakdown(s.getAmmoStats())));
		}
		return lines;
	}

	/** "Adamant dart — 540 fired, 120 recovered, 420 charged" per line, most-charged first. */
	private String ammoBreakdown(Map<Integer, long[]> stats)
	{
		final List<Map.Entry<Integer, long[]>> rows = new ArrayList<>(stats.entrySet());
		rows.sort((a, b) -> Long.compare(b.getValue()[2], a.getValue()[2]));

		final StringBuilder sb = new StringBuilder("<html>");
		int shown = 0;
		for (Map.Entry<Integer, long[]> e : rows)
		{
			final long[] v = e.getValue();
			if (v[0] <= 0)
			{
				continue;
			}
			if (shown == 8)
			{
				sb.append("…<br>");
				break;
			}
			if (shown > 0)
			{
				sb.append("<br>");
			}
			sb.append(itemName(e.getKey())).append(" — ").append(v[0]).append(" fired");
			if (v[1] > 0)
			{
				sb.append(", ").append(v[1]).append(" recovered");
			}
			sb.append(", ").append(v[2]).append(" charged");
			shown++;
		}
		sb.append("</html>");
		return shown == 0 ? null : sb.toString();
	}

	private static final Pattern DOSE_SUFFIX = Pattern.compile("\\(\\d\\)\\s*$");

	/** "Prayer potion(3)" -> "Prayer potion (sip)"; leaves un-dosed food untouched. */
	private static String consumableLabel(String name)
	{
		if (name == null)
		{
			return "";
		}
		final Matcher m = DOSE_SUFFIX.matcher(name);
		return m.find() ? name.substring(0, m.start()).trim() + " (sip)" : name;
	}

	private static String breakdownText(Map<String, Long> detail)
	{
		if (detail == null || detail.isEmpty())
		{
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		detail.forEach((k, v) ->
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(k).append(" = ").append(v);
		});
		return sb.toString();
	}

	private Map<String, Object> namedItems(Map<Integer, Integer> items)
	{
		final Map<String, Object> out = new LinkedHashMap<>();
		items.forEach((id, qty) -> out.put(itemName(id) + " (" + id + ")", qty));
		return out;
	}

	private Map<String, Object> ammoItemsLog(Map<Integer, long[]> stats)
	{
		if (stats.isEmpty())
		{
			return null;
		}
		final Map<String, Object> out = new LinkedHashMap<>();
		stats.forEach((id, v) ->
		{
			final Map<String, Object> row = new LinkedHashMap<>();
			row.put("fired", v[0]);
			row.put("recovered", v[1]);
			row.put("charged", v[2]);
			out.put(itemName(id) + " (" + id + ")", row);
		});
		return out;
	}

	private String namedItemsText(Map<Integer, Integer> items)
	{
		final StringBuilder sb = new StringBuilder();
		int shown = 0;
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			if (shown == 4)
			{
				sb.append(", +").append(items.size() - shown).append(" more");
				break;
			}
			if (shown > 0)
			{
				sb.append(", ");
			}
			sb.append(e.getValue()).append("× ").append(itemName(e.getKey()));
			shown++;
		}
		return sb.toString();
	}

	private String itemName(int itemId)
	{
		try
		{
			return itemManager.getItemComposition(itemId).getName();
		}
		catch (RuntimeException ex)
		{
			return "Item " + itemId;
		}
	}

	private static String gp(long value)
	{
		return QuantityFormatter.formatNumber(value) + " gp";
	}
}
