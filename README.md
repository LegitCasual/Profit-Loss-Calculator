# Profit Loss Calculator

A RuneLite plugin that measures **profit and loss** - loot and ground pickups coming in,
supplies / spells / teleports / ammo / deaths going out - either for a whole play session
or per kill of one mob you name, with a lifetime per-mob history and a JSON Lines event log.

Income is split two ways:

- **Potential** - the value of everything that dropped for you.
- **Collected** - the part that actually made it into your bag. **This is what counts
  toward net.** Nothing is double-counted: a collected item is drawn down from its drop.

`Net = collected − cost`. The gap between potential and collected is what you left on the
floor. (Turn on *Count uncollected drops* to make the full drop count instead.)

## Two modes

There are two ways to track, one at a time:

- **Session** - a flat "record my profit / loss for this stretch of time" run. Loot in,
  supplies out, one net number on the tab. Behind the scenes it still splits per mob and
  feeds that into the History tab.
- **Targeted farm** - you type **one** mob name and every kill of it is tracked
  individually. Every cost you incur while the farm runs is charged to that mob, so you get
  a real **GP per kill**. Anything else you kill shows under *Other income* and is not part
  of the net.

### Panel layout

Three tabs: **Session**, **Targeted**, **History**.

**Session tab**

- **Start / Pause / Resume** - one button that flips with the state. Pausing stops all
  accrual (banking / afk); resuming re-bases the trackers. **Stop** finalises,
  **Restart** stops and starts fresh.
- **Summary block** - Net, gp/hr, Gains, Losses, at-risk, then two item grids:
  - **green** - everything picked up, biggest value first
  - **red** - everything consumed (supplies, teleports, ammo, runes)
- **Income** / **Costs** - extra detail lists, **off by default** (*Show income list* /
  *Show cost list* in the config).
- **Deaths** - a row per death awaiting a fee / gravestone decision.

**Targeted tab**

- Type a mob name, **Start farm**. While it runs: **Pause / Resume**, **Stop**, **Restart**
  (restarts the same mob).
- **Summary block** - Net, **GP/kill**, Gains, Losses, gp/hr.
- **Per kill** - a row per kill (`#37  14:32   +12,400`), hover for that kill's drops.
- **Other income** - loot from anything else you killed during the farm, grouped by source.
  Shown for context; never part of the net.
- **Costs** (with *Show cost list*) and **Deaths**.

The name match is exact and case-insensitive. A mob that never fires a death event still
counts - its loot is taken as the kill signal.

**History tab**

Every run - plain session **and** targeted farm - feeds this, and its mobs merge by name:
a mob you fought across three sessions and one farm is one tidy row.

- **Lifetime** net, total gained vs total cost, kills, run count.
- One **box per mob**, sorted by net (biggest earner first): a header (`Brutus ×426`,
  gained / cost / GP-kill, and the coloured **net** - green for profit, red for loss) over
  an icon grid of that mob's merged drops. **Click a box** to drill into the individual
  runs, the money-gained-vs-cost breakdown, deaths, and full drop list.
- Cost that couldn't be tied to a mob (a teleport home while not in combat, etc.) collects
  in a **"Not in combat"** row at the bottom.
- **Clear history** wipes `history.jsonl` and the per-session logs (with a confirmation).

### Per-mob cost attribution

In a plain session, each supply, spell, teleport, ammo charge and death is charged to
**whichever NPC you were fighting** at that moment (kept sticky for a few seconds after the
last hit, so tank-eating right after a kill still counts). In a targeted farm everything
goes to the target regardless. It's best-effort - drink a potion while running between packs
and it lands in "Not in combat".

Sections with nothing in them are hidden.

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
  already credited to one of your kills. Picking up something you dropped yourself is not
  counted - the plugin remembers what you drop and matches it back. Runes that go straight
  into your rune pouch on pickup are seen too (the pouch is read alongside inventory and
  worn gear).
- **High Alchemy** - the coins a High Alch cast produces are booked as income (the rune
  cost is charged separately, so the row nets out to the real alch profit). Alching while
  fighting counts toward that mob; otherwise it collects in a "High alch" line.

Loot is valued at the **GE price**, the **High Alchemy value**, or **whichever is higher** -
your choice in the config. Coins are always face value. It is a snapshot at the moment of
receipt, not what you eventually sell for.

A **running** session re-prices live as GE prices move. The moment a session **stops**, its
numbers are frozen - both on the Session tab and in the History tab - so a run recorded at
last week's prices keeps last week's values.

### Cost

- **Consumables** - every `Eat` / `Drink` click at GE value; a dosed potion is charged the
  drop in value from one dose to the next (an empty vial for the last sip) when
  *Dose-aware potion cost* is on.
- **Spells** - standard-spellbook casts and the Ancient Magicks combat spells (Ice / Blood
  / Smoke / Shadow, all tiers), priced as the GE value of the runes needed minus any rune
  supplied by an equipped staff/tome (a Kodai frees the water on Ice spells, etc.). Only
  **manual** casts are counted - autocasting is not detected.
- **Teleports** - charged jewellery (glory, ring of dueling, games necklace, ...) priced
  per charge, and teleport tablets/scrolls at GE value.
- **Ammo** - arrows, bolts, darts, thrown weapons, chinchompas and cannonballs that leave
  your possession, split into fired / recovered / charged. Ava's recovery and cannon
  pickup are not charged.
- **Charged weapons** - the recharge material spent per attack, priced live: **Venator bow**
  (ancient essence), **Eye of Ayak** (demon tears), **Tumeken's shadow** (soul + chaos
  runes). Counted from the attack animation - one attack, one charge - so autocasting counts
  and recharging never looks like use.
- **Deaths** - a pending entry the moment you die. When the lost items come back the
  plugin reads the Death's Office fee from chat (or estimates it from the modern
  Item Retrieval tiers) and you confirm or zero it (gravestone) in the panel. Never
  reclaimed by the end of the session -> counted as a full loss.

All cost prices are live `ItemManager` GE lookups - only item identity and game-rule
quantities (rune counts, doses, charge tiers) are in code.

## Logs

Files under `.runelite/profit-loss-calculator/`:

- **`history.jsonl`** - one line per finished run
  (`{schema:3, kind, start, end, durationSec, valuation, perMob}`) where `perMob` maps a
  mob name to `{kills, gained, dropped, cost, deaths, items}`. This is what the History tab
  reads and merges. On first run older schema lines are upgraded in place and the original
  is kept as `history-v1-backup.jsonl`.
- **`session-<timestamp>.jsonl`** (with *Write session log file* enabled) - the detailed
  event stream for both modes: session start/pause/resume/stop, consumable, spell,
  teleport, kill, loot, death pending/returned/resolved. The `session_stop` line carries
  the full summary, a per-kill breakdown and the per-mob rollup.

## Known limitations

- Skilling gathering (fishing, mining, woodcutting, farming, hunter) is **not** counted as
  income - none of it raises a loot event and there is no heuristic for it yet.
- Income value is a GE/alch snapshot at receipt, not realised sale proceeds.
- "Collected" leans on watching items enter the inventory shortly after they drop. Loot
  picked up much later (full inventory, came back for it) may stay counted as potential
  only unless you click Take on it. Alching during a fight can briefly inflate collected
  coins up to - but never past - what actually dropped.
- Only **manually cast** spells are counted - autocasting (standard or Ancient) is not
  detected, so an autocast barrage/blitz task shows no spell cost.
- Ancient teleports, the Lunar and Arceuus spellbooks, and Enchant spells are not priced.
- A Kodai wand's 15% "save a rune" proc is not modelled - Ice-spell water is simply free.
- Charged weapons: only the Venator bow, Eye of Ayak and Tumeken's shadow are priced. The
  blowpipe (darts + scales), tridents, Sanguinesti staff, crystal weapons (untradeable
  shards) and the wilderness weapons are not - the recharge economy for several is unclear
  in the current wiki data.
- For **spell cost**, runes in the pouch are still charged in full - only equipped
  staves/tomes remove a rune from the bill. (The pouch *is* read for income, so runes
  picked up into it count.)
- Ammo "fired" is what left the quiver - Ava's-recovered shots never register.
- A targeted farm matches the mob name **exactly** (case-insensitive). Multi-part bosses
  whose name changes between phases, or where two die close together with a single loot
  event, can miscount a kill despite the short debounce.
- A targeted farm's net is the target only. Stray loot shows under *Other income* but is
  not netted, and skilling / clue steps done mid-farm are not counted.

## Build

```
./gradlew build      # compile + unit tests
./gradlew run        # launch a dev client with the plugin loaded
```

## License

Free and open source under the [BSD 2-Clause License](LICENSE). Please also read
[NOTICE.md](NOTICE.md) — the licence lets you fork and modify freely; the notice is my
ask that you send changes back as pull requests rather than publishing a separate
modified copy, and keep the copyright line on anything derived from this. Fixes and
ideas are very welcome as issues / PRs.
