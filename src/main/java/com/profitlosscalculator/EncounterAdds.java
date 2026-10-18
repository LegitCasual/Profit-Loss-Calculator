/*
 * Copyright (c) 2026, LegitCasual
 * BSD 2-Clause License. See LICENSE.
 */
package com.profitlosscalculator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Folds a boss fight's adds back into the boss. Phosani's Nightmare spends whole phases making
 * the player hit Husks, Parasites, Sleepwalkers and Totems - charging each supply to whatever
 * NPC is being attacked that tick (see the plugin's {@code costMob()}) then books half the
 * fight's cost, and a pile of "kills", against "Husk" instead of the boss actually being farmed.
 *
 * <p>An add only resolves to its boss while that boss is actually in the scene, so a generic
 * name ("Spawn", "Giant rat") met anywhere else stays itself. An add shared by two bosses
 * (the Nightmare and Phosani's) resolves to whichever one is present.
 *
 * <p>Pure and non-injected, like {@link BlueMoonWeaponFreeze}.
 */
final class EncounterAdds
{
	/** Add name (lower-case) -&gt; the boss(es) it spawns for, as the boss NPC names itself. */
	private static final Map<String, List<String>> BOSSES_BY_ADD = new HashMap<>();
	/** Boss -&gt; other names its NPC goes by mid-fight that still mean "the boss is here". */
	private static final Map<String, List<String>> PRESENCE_ALIASES = new HashMap<>();

	static
	{
		final List<String> nightmares = Arrays.asList("Phosani's Nightmare", "The Nightmare");
		adds(nightmares, "Husk", "Parasite", "Sleepwalker", "Totem");
		adds(Collections.singletonList("Nex"), "Fumus", "Umbra", "Cruor", "Glacies");
		adds(Collections.singletonList("Abyssal Sire"), "Spawn", "Scion", "Respiratory system");
		adds(Collections.singletonList("Kraken"), "Enormous Tentacle");
		// until it surfaces the Kraken itself is an NPC named "Whirlpool" - and its tentacles are
		// fought first, so the boss has to count as present under that name too
		PRESENCE_ALIASES.put("Kraken", Collections.singletonList("Whirlpool"));
		adds(Collections.singletonList("Zulrah"), "Snakeling");
		adds(Collections.singletonList("Vorkath"), "Zombified Spawn");
		adds(Collections.singletonList("Corporeal Beast"), "Dark energy core");
		adds(Collections.singletonList("Sarachnis"), "Spawn of Sarachnis");
		adds(Collections.singletonList("Scurrius"), "Giant rat");
		adds(Arrays.asList("Vet'ion", "Calvar'ion"), "Skeleton Hellhound", "Greater Skeleton Hellhound");
		adds(Collections.singletonList("Araxxor"), "Acidic Araxyte", "Mirrorback Araxyte", "Ruptura Araxyte");
		adds(Collections.singletonList("Venenatis"), "Venenatis' Spiderling");
		adds(Collections.singletonList("Spindel"), "Spindel's Spiderling");
		adds(Collections.singletonList("Scorpia"), "Scorpia's guardian", "Scorpia's offspring");
		adds(Collections.singletonList("Bryophyta"), "Growthling");
		adds(Collections.singletonList("Skotizo"), "Awakened Altar", "Reanimated demon spawn", "Dark Ankou");
		adds(Collections.singletonList("Hespori"), "Flower");
		adds(Collections.singletonList("Zalcano"), "Golem");
		adds(Collections.singletonList("The Mimic"), "Third Age Warrior", "Third Age Ranger", "Third Age Mage");
		// Grotesque Guardians: one shared loot table, Dusk's death is the kill
		adds(Collections.singletonList("Dusk"), "Dawn");
		// the titans themselves stay separate - KnownMobNames' "The Royal Titans" group covers both
		adds(Arrays.asList("Branda the Fire Queen", "Eldric the Ice King"), "Fire elemental");
		adds(Arrays.asList("Eldric the Ice King", "Branda the Fire Queen"), "Ice elemental");
		// the Judge is fought on its own platform, so Yama may not be loaded - then it stays itself
		adds(Collections.singletonList("Yama"), "Void Flare", "Judge of Yama");
		adds(Collections.singletonList("Doom of Mokhaiotl"), "Demonic larva", "Demonic range larva",
			"Demonic magic larva", "Demonic melee larva", "Giant demonic range larva", "Giant demonic magic larva");
		adds(Collections.singletonList("Amoxliatl"), "Unstable ice");
		adds(Collections.singletonList("The Hueycoatl"), "Hueycoatl body", "Hueycoatl tail");
		adds(Collections.singletonList("Maggot King"), "Ur-maggot larvae");
		adds(Collections.singletonList("The Whisperer"), "Lost Soul");
		// only the boss wave - earlier Inferno / Fight Caves waves have no boss present
		adds(Collections.singletonList("TzKal-Zuk"), "Jal-MejJak", "Jal-Xil", "Jal-Zek", "JalTok-Jad");
		adds(Arrays.asList("TzKal-Zuk", "TzTok-Jad"), "Yt-HurKot");
	}

	private static void adds(List<String> bosses, String... names)
	{
		for (String n : names)
		{
			BOSSES_BY_ADD.put(n.toLowerCase(Locale.ROOT), bosses);
		}
	}

	private EncounterAdds()
	{
	}

	/** True when {@code npcName} is some boss's add - worth a scene check before charging it. */
	static boolean isAdd(String npcName)
	{
		return npcName != null && BOSSES_BY_ADD.containsKey(npcName.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * The boss {@code npcName} is an add of, if that boss is present according to
	 * {@code bossPresent} (handed a boss name, exactly as listed); otherwise {@code npcName}
	 * unchanged. Null in, null out.
	 */
	static String resolve(String npcName, Predicate<String> bossPresent)
	{
		if (npcName == null)
		{
			return null;
		}
		final List<String> bosses = BOSSES_BY_ADD.get(npcName.trim().toLowerCase(Locale.ROOT));
		if (bosses != null)
		{
			for (String boss : bosses)
			{
				if (bossPresent.test(boss)
					|| PRESENCE_ALIASES.getOrDefault(boss, Collections.emptyList()).stream().anyMatch(bossPresent))
				{
					return boss;
				}
			}
		}
		return npcName;
	}

	/** {@link #resolve(String, Predicate)} against a set of NPC names currently in the scene. */
	static String resolve(String npcName, Collection<String> sceneNpcNames)
	{
		return resolve(npcName, boss -> sceneNpcNames.stream().anyMatch(boss::equalsIgnoreCase));
	}
}
