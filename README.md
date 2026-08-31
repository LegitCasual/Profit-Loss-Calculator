# Session Cost Tracker

A RuneLite plugin that measures a play session's **profit and loss** - loot and ground
pickups coming in, supplies / spells / teleports / ammo / deaths going out - with a boss
kill tally and a JSON Lines event log.

Income is split two ways:

- **Potential** - the value of everything that dropped for you.
- **Collected** - the part that actually made it into your bag. **This is what counts
  toward net.** Nothing is double-counted: a collected item is drawn down from its drop.

`Net = collected − cost`. The gap between potential and collected is what you left on the
floor. (Turn on *Count uncollected drops* to make the full drop count instead.)

## Controls (side panel)

- **Start / Pause / Resume** - one button that flips with the session state. Pausing stops
  all accrual (for banking / afk); resuming re-bases the trackers so the gap isn't counted.
- **Stop** - finalise the session and leave the summary on screen.
- **Restart** - finalise the current session (writing its summary to the log) and start a
  fresh one in one click.
- **Boss** field + **+1 kill** - set a boss name and every matching NPC death bumps the
  kill tally automatically; the button adds one by hand. The panel and overlay show the
  running **net per kill**.

### Panel layout

- **Summary block** - titled with the boss name (or "Session"). Shows the **Net**, a
  `+gains  -losses` line, `gp/hr` and `net/kill`, then two item grids:
  - **green** - everything picked up, biggest first
  - **red** - everything consumed (supplies, teleports, ammo, runes)

  Hover any icon for its name, value and count.
- **Kill log** - one row per boss kill (`#12  Vorkath  ·  +720k`), coloured by whether
  that fight made or lost money. Hover a row for the full breakdown: what dropped and
  what was spent during that fight.
- **Income** / **Costs** - flat time-ordered lists, **off by default**. Turn on *Show
  income list* / *Show cost list* in the config if you want them.
- **Unresolved deaths** - a row per death awaiting a fee / gravestone decision.

## What it tracks

### Income

- **Loot** - monster and boss kills, PvP kills, reward chests / caskets / minigame
  rewards, and pickpockets, taken straight from RuneLite's own loot tracker (so it needs
  the built-in **Loot Tracker** plugin enabled - it is by default). Monster and PvP loot
  starts as *potential* and moves to *collected* as the plugin sees each item enter your
  inventory (walking over the pile is enough - no click needed). A drop you haven't
  touched in ~5 minutes stays potential-only. Rewards and pickpockets go straight to the
  bag, so they're collected immediately.
- **Ground pickups** - items you take off the ground (or telekinetic-grab) that aren't
  already credited to one of your kills. Picking your own dropped items back up will count.

Loot is valued at the **GE price**, the **High Alchemy value**, or **whichever is higher** -
your choice in the config. Coins are always face value. It is a snapshot at the moment of
receipt, not what you eventually sell for.

### Cost

- **Consumables** - every `Eat` / `Drink` click at GE value; a dosed potion is charged the
  drop in value from one dose to the next (an empty vial for the last sip) when
  *Dose-aware potion cost* is on.
- **Spells** - standard-spellbook casts, priced as the GE value of the runes needed minus
  any rune supplied by an equipped staff/tome.
- **Teleports** - charged jewellery (glory, ring of dueling, games necklace, ...) priced
  per charge, and teleport tablets/scrolls at GE value.
- **Ammo** - arrows, bolts, darts, thrown weapons, chinchompas and cannonballs that leave
  your possession, split into fired / recovered / charged. Ava's recovery and cannon
  pickup are not charged.
- **Deaths** - a pending entry the moment you die. When the lost items come back the
  plugin reads the Death's Office fee from chat (or estimates it from the modern
  Item Retrieval tiers) and you confirm or zero it (gravestone) in the panel. Never
  reclaimed by the end of the session -> counted as a full loss.

All cost prices are live `ItemManager` GE lookups - only item identity and game-rule
quantities (rune counts, doses, charge tiers) are in code.

## Session log

With *Write session log file* enabled, every event (session start/pause/resume/stop,
consumable, spell, teleport, boss_kill, loot, death pending/returned/resolved) is appended
as one JSON object per line to
`.runelite/session-cost-tracker/session-<timestamp>.jsonl`. A `loot` line carries `kill`
when it was attributed to a boss kill; the `session_stop` line carries the full profit /
loss summary plus a per-kill loot breakdown.

## Known limitations

- Skilling gathering (fishing, mining, woodcutting, farming, hunter) is **not** counted as
  income - none of it raises a loot event and there is no heuristic for it yet.
- Income value is a GE/alch snapshot at receipt, not realised sale proceeds.
- "Collected" leans on watching items enter the inventory shortly after they drop. Loot
  picked up much later (full inventory, came back for it) may stay counted as potential
  only unless you click Take on it. Alching during a fight can briefly inflate collected
  coins up to - but never past - what actually dropped.
- Ancient / Lunar / Arceuus spellbooks and Enchant spells are not priced.
- Charged weapons (blowpipe darts + scales, trident, sanguinesti, shadow) are not tracked.
- The rune *pouch* is not inspected - pouch runes are consumed and still cost gp; only
  equipped staves/tomes remove a rune from the bill.
- Ammo "fired" is what left the quiver - Ava's-recovered shots never register.
- Boss matching is a case-insensitive *contains* on the NPC name; multi-part bosses
  (Great Olm's claws, Hydra phases) can over-count despite the short debounce.

## Build

```
./gradlew build      # compile + unit tests
./gradlew run        # launch a dev client with the plugin loaded
```
