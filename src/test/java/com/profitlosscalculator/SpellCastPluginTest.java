/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the plugin's real (private) spell handling end to end, against java.lang.reflect.Proxy
 * fakes of Client / MenuEntry / Widget / ItemContainer / EnumComposition: the click goes through
 * {@code handleSpell} (what {@code onMenuOptionClicked} hands every non-eat/drink click to), and
 * each game tick does what {@code onGameTick} does for spells - read inventory + worn + rune
 * pouch through the plugin's own {@code trackedItems()}, diff against last tick, then
 * {@code confirmCasts} and {@code expireCasts}. The only stand-in on the costing side is the
 * GE price. Covers spells that can't be cast on an account that hasn't unlocked them.
 */
public class SpellCastPluginTest
{
	private static final int[] POUCH_TYPE = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};
	private static final int[] POUCH_QTY = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};
	/** Divine rune pouch: four slots. */
	private static final int POUCH_SLOTS = 4;

	private ProfitLossCalculatorPlugin plugin;
	private Session session;
	private int tick;
	private Widget selectedWidget;
	private ItemContainer worn;
	/** rune pouch slots: pouch "type" (our own numbering, see {@link #pouchEnum}) and quantity */
	private final int[] pouchType = new int[POUCH_TYPE.length];
	private final int[] pouchQty = new int[POUCH_QTY.length];
	/** inventory, item id -&gt; qty */
	private final Map<Integer, Integer> inv = new HashMap<>();
	private Map<Integer, Integer> prevItems;
	/** rune -&gt; gp; distinct per rune so a wrong rune in a recipe changes the total */
	private final Map<Rune, Integer> prices = new EnumMap<>(Rune.class);

	// ------------------------------------------------------------------ fakes

	private static Object defaultFor(Class<?> t)
	{
		if (t == boolean.class)
		{
			return false;
		}
		if (t == int.class)
		{
			return 0;
		}
		if (t == long.class)
		{
			return 0L;
		}
		if (t == double.class)
		{
			return 0d;
		}
		if (t == float.class)
		{
			return 0f;
		}
		if (t == short.class)
		{
			return (short) 0;
		}
		if (t == byte.class)
		{
			return (byte) 0;
		}
		if (t == char.class)
		{
			return (char) 0;
		}
		if (t == String.class)
		{
			return "";
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> impl)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "hashCode":
					return System.identityHashCode(p);
				case "equals":
					return p == a[0];
				case "toString":
					return type.getSimpleName() + "@fake";
			}
			final Object r = impl.apply(m.getName(), a);
			return r != null ? r : defaultFor(m.getReturnType());
		});
	}

	/** RUNEPOUCH_RUNE: pouch type n is the n-th Rune (1-based), or a combination rune past them. */
	private static final List<Integer> POUCH_ITEMS = new ArrayList<>();

	static
	{
		for (Rune r : Rune.values())
		{
			POUCH_ITEMS.add(r.getItemId());
		}
		POUCH_ITEMS.addAll(Arrays.asList(ItemID.DUSTRUNE, ItemID.MISTRUNE, ItemID.SMOKERUNE,
			ItemID.MUDRUNE, ItemID.STEAMRUNE, ItemID.LAVARUNE, ItemID.AETHERRUNE));
	}

	private final EnumComposition pouchEnum = fake(EnumComposition.class, (m, a) ->
		"getIntValue".equals(m) ? POUCH_ITEMS.get((Integer) a[0] - 1) : null);

	@Before
	public void setUp() throws Exception
	{
		plugin = new ProfitLossCalculatorPlugin();
		inv.clear();
		Arrays.fill(pouchType, 0);
		Arrays.fill(pouchQty, 0);
		worn = null;
		selectedWidget = null;
		final Client client = fake(Client.class, (m, a) ->
		{
			switch (m)
			{
				case "getTickCount":
					return tick;
				case "getSelectedWidget":
					return selectedWidget;
				case "getItemContainer":
					return (Integer) a[0] == InventoryID.WORN ? worn : null;
				case "getEnum":
					return pouchEnum;
				case "getVarbitValue":
				{
					final int id = (Integer) a[0];
					for (int i = 0; i < POUCH_TYPE.length; i++)
					{
						if (POUCH_TYPE[i] == id)
						{
							return pouchType[i];
						}
						if (POUCH_QTY[i] == id)
						{
							return pouchQty[i];
						}
					}
					return 0;
				}
				default:
					return null;
			}
		});
		int gp = 10;
		for (Rune r : Rune.values())
		{
			prices.put(r, gp);
			gp = gp * 2 + 1;
		}
		final SpellCostService spells = new SpellCostService(client, null)
		{
			@Override
			Priced price(Spell spell)
			{
				return price(spell, equippedRuneSources(), id -> priceOf(id));
			}
		};

		set("client", client);
		set("config", fake(ProfitLossCalculatorConfig.class, (m, a) -> null));
		set("logger", new SessionLogger(new Gson()));
		set("spellCostService", spells);
		session = new Session(Instant.now());
		set("session", session);
		// refreshView() -> buildView() short-circuits to frozenView when sessionFinished; the
		// spell path under test doesn't look at sessionFinished, so this keeps the view out of it
		set("sessionFinished", true);
		set("frozenView", ProfitLossCalculatorPanel.View.builder().build());
		tick = 1000;
	}

	private int priceOf(int itemId)
	{
		for (Rune r : Rune.values())
		{
			if (r.getItemId() == itemId)
			{
				return prices.get(r);
			}
		}
		return 0;
	}

	private void set(String field, Object value) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(plugin, value);
	}

	private Object call(String method, Class<?>[] types, Object... args) throws Exception
	{
		final Method m = ProfitLossCalculatorPlugin.class.getDeclaredMethod(method, types);
		m.setAccessible(true);
		try
		{
			return m.invoke(plugin, args);
		}
		catch (InvocationTargetException e)
		{
			throw (Exception) e.getCause();
		}
	}

	// ------------------------------------------------------------------ rune holdings

	private int pouchTypeOf(int itemId)
	{
		return POUCH_ITEMS.indexOf(itemId) + 1;
	}

	/** Put {@code qty} of {@code itemId} in the rune pouch (a free or matching slot). */
	private void pouch(int itemId, int qty)
	{
		final int type = pouchTypeOf(itemId);
		for (int i = 0; i < POUCH_SLOTS; i++)
		{
			if (pouchType[i] == type || pouchType[i] == 0)
			{
				pouchType[i] = type;
				pouchQty[i] = qty;
				return;
			}
		}
		throw new IllegalStateException("pouch full");
	}

	/** Remove {@code qty} of {@code itemId} - from the pouch first, then the inventory. */
	private void spend(int itemId, int qty)
	{
		final int type = pouchTypeOf(itemId);
		for (int i = 0; i < POUCH_SLOTS && qty > 0; i++)
		{
			if (pouchType[i] == type)
			{
				final int take = Math.min(qty, pouchQty[i]);
				pouchQty[i] -= take;
				qty -= take;
			}
		}
		if (qty > 0)
		{
			inv.merge(itemId, -qty, Integer::sum);
		}
	}

	/** A full stock of every rune {@code s} needs: the first four types in the pouch, the rest loose. */
	private void stock(Spell s)
	{
		int slot = 0;
		for (Rune r : s.getRunes().keySet())
		{
			if (slot++ < POUCH_SLOTS)
			{
				pouch(r.getItemId(), 5000);
			}
			else
			{
				inv.put(r.getItemId(), 5000);
			}
		}
	}

	/** The game took this cast's runes (staff-provided ones excepted). */
	private void spendRunesOf(Spell s, Rune... free)
	{
		final List<Rune> staff = Arrays.asList(free);
		s.getRunes().forEach((r, q) ->
		{
			if (!staff.contains(r))
			{
				spend(r.getItemId(), q);
			}
		});
	}

	// ------------------------------------------------------------------ simulation steps

	/** Settle the baseline - like the tick that primes {@code prevTickItems}. */
	@SuppressWarnings("unchecked")
	private void prime() throws Exception
	{
		set("lastKnownInv", new HashMap<>(inv));
		prevItems = (Map<Integer, Integer>) call("trackedItems", new Class<?>[0]);
	}

	/** One game tick: whatever changed in inv / worn / pouch is this tick's loss. */
	@SuppressWarnings("unchecked")
	private void gameTick() throws Exception
	{
		tick++;
		set("lastKnownInv", new HashMap<>(inv));
		final Map<Integer, Integer> cur = (Map<Integer, Integer>) call("trackedItems", new Class<?>[0]);
		final Map<Integer, Integer> losses = ContainerSnapshot.lost(prevItems, cur);
		call("confirmCasts", new Class<?>[]{Map.class}, losses);
		prevItems = cur;
		call("expireCasts", new Class<?>[0]);
	}

	private void click(String option, MenuAction action, int param1, int itemId) throws Exception
	{
		final MenuEntry entry = fake(MenuEntry.class, (m, a) ->
		{
			switch (m)
			{
				case "getOption":
					return option;
				case "getType":
					return action;
				case "getParam1":
					return param1;
				case "getItemId":
					return itemId;
				default:
					return null;
			}
		});
		call("handleSpell", new Class<?>[]{MenuOptionClicked.class}, new MenuOptionClicked(entry));
	}

	/** A spellbook button's own "Cast" - thralls, teleports, Vengeance, offerings, ... */
	private void cast(int component) throws Exception
	{
		click("Cast", MenuAction.CC_OP, component, -1);
	}

	/** A selected spell used on an inventory item - enchants, reanimation, alchemy. */
	private void castOnItem(int component, int itemId) throws Exception
	{
		selectedWidget = fake(Widget.class, (m, a) -> "getId".equals(m) ? component : null);
		click("Cast", MenuAction.WIDGET_TARGET_ON_WIDGET, InterfaceID.Inventory.ITEMS, itemId);
	}

	/** How the player casts {@code s}: on an item for the targeted spells, else its button. */
	private void clickSpell(Spell s) throws Exception
	{
		switch (s)
		{
			case HIGH_ALCHEMY:
				castOnItem(s.getComponentId(), ItemID.RUNE_PLATEBODY);
				break;
			case ENCHANT_LVL_5:
				castOnItem(s.getComponentId(), ItemID.DRAGONSTONE_RING);
				break;
			case BASIC_REANIMATION:
				castOnItem(s.getComponentId(), ItemID.ARCEUUS_CORPSE_GOBLIN_INITIAL);
				break;
			default:
				cast(s.getComponentId());
		}
	}

	private void wield(int weaponId)
	{
		worn = fake(ItemContainer.class, (m, a) ->
			"getItem".equals(m) && (Integer) a[0] == EquipmentInventorySlot.WEAPON.getSlotIdx()
				? new Item(weaponId, 1) : null);
	}

	private long recipeGp(Spell s, Rune... free)
	{
		final List<Rune> staff = Arrays.asList(free);
		long gp = 0;
		for (Map.Entry<Rune, Integer> e : s.getRunes().entrySet())
		{
			if (!staff.contains(e.getKey()))
			{
				gp += (long) prices.get(e.getKey()) * e.getValue();
			}
		}
		return gp;
	}

	private List<CostEvent> spellEvents()
	{
		final List<CostEvent> out = new ArrayList<>();
		for (CostEvent e : session.getEvents())
		{
			if (e.getType() == CostEvent.Type.SPELL)
			{
				out.add(e);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ five per spellbook

	private static final List<Spell> STANDARD = Arrays.asList(Spell.HIGH_ALCHEMY, Spell.FIRE_WAVE,
		Spell.VARROCK_TELEPORT, Spell.ENCHANT_LVL_5, Spell.BONES_TO_PEACHES);
	private static final List<Spell> ANCIENT = Arrays.asList(Spell.ICE_BARRAGE, Spell.BLOOD_BLITZ,
		Spell.SMOKE_BURST, Spell.SHADOW_RUSH, Spell.GHORROCK_TELEPORT);
	private static final List<Spell> LUNAR = Arrays.asList(Spell.VENGEANCE, Spell.NPC_CONTACT,
		Spell.PLANK_MAKE, Spell.HEAL_GROUP, Spell.TELE_GROUP_ICE_PLATEAU);
	private static final List<Spell> ARCEUUS = Arrays.asList(Spell.RESURRECT_GREATER_GHOST,
		Spell.RESURRECT_LESSER_ZOMBIE, Spell.DEATH_CHARGE, Spell.BASIC_REANIMATION, Spell.RESURRECT_CROPS);

	/** Each spell, fresh state: click, the game takes the runes a tick later -&gt; charged once, at
	 *  the recipe price, with the runes in the used grid. */
	private void castsAreCharged(List<Spell> book) throws Exception
	{
		for (Spell s : book)
		{
			setUp();
			stock(s);
			prime();
			clickSpell(s);
			gameTick(); // the click is processed - nothing has left yet
			assertTrue(s + " charged before its runes left", spellEvents().isEmpty());
			spendRunesOf(s);
			gameTick();
			final List<CostEvent> events = spellEvents();
			assertEquals(s + " charge count", 1, events.size());
			assertEquals(s + " label", s.getDisplayName(), events.get(0).getLabel());
			assertEquals(s + " gp", recipeGp(s), events.get(0).getGp());
			s.getRunes().forEach((r, q) ->
				assertEquals(s + " " + r + " used", q, session.getRunesUsed().get(r.getItemId())));
		}
	}

	/** Each spell, fresh state: click, but the game refuses - no rune leaves -&gt; never charged. */
	private void refusedCastsAreFree(List<Spell> book) throws Exception
	{
		for (Spell s : book)
		{
			setUp();
			stock(s);
			prime();
			clickSpell(s);
			for (int i = 0; i < 15; i++)
			{
				gameTick();
			}
			assertTrue(s + " charged with no runes spent", spellEvents().isEmpty());
			assertTrue(s + " still armed after the window", ((List<?>) field("pendingCasts")).isEmpty());
		}
	}

	private Object field(String name) throws Exception
	{
		final Field f = ProfitLossCalculatorPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(plugin);
	}

	@Test
	public void standardSpellsAreChargedWhenTheirRunesLeave() throws Exception
	{
		castsAreCharged(STANDARD);
	}

	@Test
	public void ancientSpellsAreChargedWhenTheirRunesLeave() throws Exception
	{
		castsAreCharged(ANCIENT);
	}

	@Test
	public void lunarSpellsAreChargedWhenTheirRunesLeave() throws Exception
	{
		castsAreCharged(LUNAR);
	}

	@Test
	public void arceuusSpellsAreChargedWhenTheirRunesLeave() throws Exception
	{
		castsAreCharged(ARCEUUS);
	}

	@Test
	public void refusedStandardSpellsAreFree() throws Exception
	{
		refusedCastsAreFree(STANDARD);
	}

	@Test
	public void refusedAncientSpellsAreFree() throws Exception
	{
		refusedCastsAreFree(ANCIENT);
	}

	@Test
	public void refusedLunarSpellsAreFree() throws Exception
	{
		refusedCastsAreFree(LUNAR);
	}

	@Test
	public void refusedArceuusSpellsAreFree() throws Exception
	{
		refusedCastsAreFree(ARCEUUS);
	}

	// ------------------------------------------------------------------ scenarios

	@Test
	public void thrallFromRunePouchIsCharged() throws Exception
	{
		// issue #2: "I am casting summon thralls and it's not showing as an expense" - all
		// three of a greater thrall's runes in the pouch, none in the inventory
		pouch(ItemID.FIRERUNE, 1000);
		pouch(ItemID.BLOODRUNE, 1000);
		pouch(ItemID.COSMICRUNE, 1000);
		prime();
		cast(InterfaceID.MagicSpellbook.RESURRECT_GREATER_GHOST);
		gameTick();
		spendRunesOf(Spell.RESURRECT_GREATER_GHOST);
		gameTick();
		assertEquals(1, spellEvents().size());
		assertEquals(recipeGp(Spell.RESURRECT_GREATER_GHOST), session.spellTotal());
		assertEquals(Integer.valueOf(5), session.getRunesUsed().get(ItemID.BLOODRUNE));
	}

	@Test
	public void spamClickingVengeanceOnCooldownChargesOnlyTheRealCast() throws Exception
	{
		stock(Spell.VENGEANCE);
		prime();
		cast(InterfaceID.MagicSpellbook.VENGEANCE);
		gameTick();
		spendRunesOf(Spell.VENGEANCE); // the real one
		gameTick();
		for (int i = 0; i < 8; i++) // "You can only cast vengeance every 30 seconds."
		{
			cast(InterfaceID.MagicSpellbook.VENGEANCE);
			gameTick();
		}
		for (int i = 0; i < 15; i++)
		{
			gameTick();
		}
		assertEquals(1, spellEvents().size());
		assertEquals(recipeGp(Spell.VENGEANCE), session.spellTotal());
	}

	@Test
	public void refusedVengeanceIsNotConfirmedByABarrageTheSameTick() throws Exception
	{
		// both use death runes; only Vengeance uses astrals, which never leave
		stock(Spell.VENGEANCE);
		inv.put(ItemID.WATERRUNE, 5000);
		inv.put(ItemID.BLOODRUNE, 5000);
		prime();
		cast(InterfaceID.MagicSpellbook.VENGEANCE); // on cooldown - the game will refuse it
		selectedWidget = fake(Widget.class, (m, a) -> "getId".equals(m) ? InterfaceID.MagicSpellbook.ICE_BARRAGE : null);
		click("Cast", MenuAction.WIDGET_TARGET_ON_NPC, 0, -1);
		gameTick();
		spendRunesOf(Spell.ICE_BARRAGE);
		gameTick();
		for (int i = 0; i < 15; i++)
		{
			gameTick();
		}
		final List<CostEvent> events = spellEvents();
		assertEquals(1, events.size());
		assertEquals("Ice Barrage", events.get(0).getLabel());
	}

	@Test
	public void vengeanceAndBarrageTheSameTickAreBothCharged() throws Exception
	{
		stock(Spell.VENGEANCE);
		inv.put(ItemID.WATERRUNE, 5000);
		inv.put(ItemID.BLOODRUNE, 5000);
		prime();
		cast(InterfaceID.MagicSpellbook.VENGEANCE);
		selectedWidget = fake(Widget.class, (m, a) -> "getId".equals(m) ? InterfaceID.MagicSpellbook.ICE_BARRAGE : null);
		click("Cast", MenuAction.WIDGET_TARGET_ON_NPC, 0, -1);
		gameTick();
		spendRunesOf(Spell.VENGEANCE);
		spendRunesOf(Spell.ICE_BARRAGE);
		gameTick();
		assertEquals(2, spellEvents().size());
		assertEquals(recipeGp(Spell.VENGEANCE) + recipeGp(Spell.ICE_BARRAGE), session.spellTotal());
	}

	@Test
	public void combinationRunesConfirmTheCast() throws Exception
	{
		// Earth Blast paid with dust runes (air + earth) from the pouch, death from the inventory
		pouch(ItemID.DUSTRUNE, 1000);
		inv.put(ItemID.DEATHRUNE, 1000);
		prime();
		cast(InterfaceID.MagicSpellbook.EARTH_BLAST);
		gameTick();
		spend(ItemID.DUSTRUNE, 4);
		spend(ItemID.DEATHRUNE, 1);
		gameTick();
		assertEquals(1, spellEvents().size());
		assertEquals(recipeGp(Spell.EARTH_BLAST), session.spellTotal());
	}

	@Test
	public void staffProvidedRunesAreNotWaitedOn() throws Exception
	{
		// fire staff: a greater thrall's fire is free, so only blood + cosmic leave
		wield(Staff.FIRE.getItemIds()[0]);
		pouch(ItemID.BLOODRUNE, 1000);
		pouch(ItemID.COSMICRUNE, 1000);
		prime();
		cast(InterfaceID.MagicSpellbook.RESURRECT_GREATER_SKELETON);
		gameTick();
		spendRunesOf(Spell.RESURRECT_GREATER_SKELETON, Rune.FIRE);
		gameTick();
		assertEquals(recipeGp(Spell.RESURRECT_GREATER_SKELETON, Rune.FIRE), session.spellTotal());
		assertNull(session.getRunesUsed().get(ItemID.FIRERUNE));
	}

	@Test
	public void kodaiSavedCastIsFree() throws Exception
	{
		stock(Spell.ICE_BARRAGE);
		prime();
		selectedWidget = fake(Widget.class, (m, a) -> "getId".equals(m) ? InterfaceID.MagicSpellbook.ICE_BARRAGE : null);
		click("Cast", MenuAction.WIDGET_TARGET_ON_NPC, 0, -1);
		for (int i = 0; i < 15; i++) // the wand saved every rune
		{
			gameTick();
		}
		assertTrue(spellEvents().isEmpty());
	}

	@Test
	public void castOnAFarTargetWaitsForTheWalk() throws Exception
	{
		stock(Spell.FIRE_WAVE);
		prime();
		selectedWidget = fake(Widget.class, (m, a) -> "getId".equals(m) ? InterfaceID.MagicSpellbook.FIRE_WAVE : null);
		click("Cast", MenuAction.WIDGET_TARGET_ON_NPC, 0, -1);
		for (int i = 0; i < 9; i++) // walking into range
		{
			gameTick();
		}
		spendRunesOf(Spell.FIRE_WAVE);
		gameTick(); // 10 ticks after the click - still inside the window
		assertEquals(1, spellEvents().size());
	}

	@Test
	public void runesLeavingAfterTheWindowAreNotThatCast() throws Exception
	{
		stock(Spell.FIRE_WAVE);
		prime();
		cast(InterfaceID.MagicSpellbook.FIRE_WAVE);
		for (int i = 0; i < 11; i++)
		{
			gameTick();
		}
		spendRunesOf(Spell.FIRE_WAVE);
		gameTick();
		assertTrue(spellEvents().isEmpty());
	}

	@Test
	public void runesLeavingWithNoClickChargeNothing() throws Exception
	{
		// e.g. dropping runes, or charging a trident - not a spellbook cast
		stock(Spell.VENGEANCE);
		prime();
		spendRunesOf(Spell.VENGEANCE);
		gameTick();
		assertTrue(spellEvents().isEmpty());
	}

	@Test
	public void everySpellInEveryBookIsChargedWhenItsRunesLeave() throws Exception
	{
		long expected = 0;
		for (Spell s : Spell.values())
		{
			s.getRunes().keySet().forEach(r -> inv.put(r.getItemId(), inv.getOrDefault(r.getItemId(), 0) + 5000));
		}
		prime();
		for (Spell s : Spell.values())
		{
			clickSpell(s);
			gameTick();
			spendRunesOf(s);
			gameTick();
			expected += recipeGp(s);
		}
		assertEquals(Spell.values().length, spellEvents().size());
		assertEquals(expected, session.spellTotal());
	}

	@Test
	public void selectingATargetedSpellIsNotACast() throws Exception
	{
		// clicking "Cast" on Lvl-1 Enchant only arms it; the cast is the later use on an item
		click("Cast", MenuAction.WIDGET_TARGET, InterfaceID.MagicSpellbook.ENCHANT_1, -1);
		assertTrue(((List<?>) field("pendingCasts")).isEmpty());
	}

	@Test
	public void sameButtonTwiceInOneTickArmsOnce() throws Exception
	{
		cast(InterfaceID.MagicSpellbook.DEATH_CHARGE);
		cast(InterfaceID.MagicSpellbook.DEATH_CHARGE);
		assertEquals(1, ((List<?>) field("pendingCasts")).size());
	}

	@Test
	public void freeAndUnknownButtonsArmNothing() throws Exception
	{
		cast(InterfaceID.MagicSpellbook.TELEPORT_HOME_ARCEUUS);
		cast(InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_LUNAR);
		cast(InterfaceID.MagicSpellbook.ENCHANT_JEWELLERY); // opens the enchant submenu
		cast(InterfaceID.MagicSpellbook.XBOWS_ENCHANT);
		assertTrue(((List<?>) field("pendingCasts")).isEmpty());
	}
}
