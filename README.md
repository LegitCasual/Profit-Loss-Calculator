# Profit Loss Calculator

A RuneLite plugin that measures **profit and loss** - loot and ground pickups coming in,
supplies / spells / teleports / ammo / deaths going out - for a whole play session, per kill
of one mob you name, or for your current Slayer task, with a lifetime per-mob history and a
JSON Lines event log.

Income is split two ways:

- **Potential** - the value of everything that dropped for you.
- **Collected** - the part that actually made it into your bag. **This is what counts
  toward net.** Nothing is double-counted: a collected item is drawn down from its drop.

`Net = collected − cost`. The gap between potential and collected is what you left on the
floor. (Turn on *Count uncollected drops* to make the full drop count instead.)

## Three modes

There are three ways to track, one at a time:

- **Session** - a flat "record my profit / loss for this stretch of time" run. Loot in,
  supplies out, one net number on the tab. Behind the scenes it still splits per mob and
  feeds that into the History tab.
- **Boss Target Farm** - you type a mob name and every kill of it is tracked individually,
  giving you a real **GP per kill**. You can add more names to the same farm at any point
  without stopping - each one gets its own block with its own net and gain icon grid, stacked
  under a combined farm total, with one combined per-kill list at the bottom once you're
  farming more than one. Anything you kill that was never added to the farm shows under
  *Other income* and is not part of the net.
- **Slayer** - auto-detects your current Slayer task (name, location, kills remaining) and
  tracks every kill of every mob that counts toward it - aliases, superiors and boss tasks
  included, the same matching RuneLite's own built-in Slayer plugin uses. Getting reassigned
  to a new task doesn't stop the run; it just folds the new task's mobs in, so one Slayer
  run can span several tasks back to back. Needs the built-in **Slayer** plugin enabled (it
  is by default).

### Panel layout

A **History** button sits above a dropdown that switches between the three tracking modes.

**Session**

- **Start / Pause / Resume** - one button that flips with the state. Pausing stops all
  accrual (banking / afk); resuming re-bases the trackers. **Stop** finalises,
  **Restart** stops and starts fresh.
- **Summary block** - Net, gp/hr, Gains, Losses, at-risk, then two item grids:
  - **green** - everything picked up, biggest value first
  - **red** - everything consumed (supplies, teleports, ammo, runes)
- **Income** / **Costs** - extra detail lists, **off by default** (*Show income list* /
  *Show cost list* in the config).
- **Deaths** - a row per death awaiting a fee / gravestone decision.

**Boss Target Farm**

- Type a mob name, **Start farm**. While it runs, the same field relabels to **Add mob** -
  keep typing names and adding them to grow the farm; **Pause / Resume**, **Stop**, **Restart**
  (brings back every target the farm had).
- **Summary block** - the combined total across every target: Net, **GP/kill**, Gains, Losses,
  gp/hr.
- **Per boss** - only shown once you've added more than one target: one block per mob, each
  with its own net, kills, GP/kill and its own gain icon grid. Losses stay a farm-wide figure
  here - cost isn't tracked at the item level per mob, only as a total.
- **Per kill** - a row per kill (`#37  14:32   +12,400`), hover for that kill's drops; shows
  the mob's name per row once you're farming more than one target.
- **Other income** - loot from anything you killed that was never added to the farm, grouped
  by source. Shown for context; never part of the net.
- **Costs** (with *Show cost list*) and **Deaths**.

The name match is exact and case-insensitive. A mob that never fires a death event still
counts - its loot is taken as the kill signal.

**Slayer**

- Shows the currently detected task (name, location, kills left) as soon as you have one -
  no typing required. **Start tracking**, then **Pause / Resume**, **Stop**, **Restart**
  while it runs.
- **Summary block** - Net, **GP/kill**, Gains, Losses, gp/hr, average kill time.
- **Per kill** - like Targeted, but each row also carries the mob's name, since a task can
  span several species.
- **Other income**, **Costs**, **Deaths** - same as Targeted. A stray kill that never
  matched the task shows under *Other income* only.

**History**

Every run - plain session, targeted farm **and** Slayer task - feeds this, and its mobs
merge by name: a mob you fought across three sessions, one farm and a Slayer task is one
tidy row.

- **Lifetime** net, total gained vs total cost, kills, run count.
- One **box per mob**, sorted by net (biggest earner first): a header (`Brutus ×426`,
  gained / cost / GP-kill, and the coloured **net** - green for profit, red for loss) over
  an icon grid of that mob's merged drops. **Click a box** to drill into the individual
  runs, the money-gained-vs-cost breakdown, deaths, and full drop list.
- Cost that couldn't be tied to a mob (a teleport home while not in combat, etc.) collects
  in a **"Not in combat"** row at the bottom.
- **Clear history** wipes `history.jsonl` and the per-session logs (with a confirmation).

### Per-mob cost attribution

In every mode, each supply, spell, teleport, ammo charge and death is charged to **whichever
NPC you were fighting** at that moment (kept sticky for a few seconds after the last hit, so
tank-eating right after a kill still counts). It's best-effort - drink a potion while running
between packs and it lands in "Not in combat". In a Boss Target Farm, cost incurred fighting
something that isn't one of the farm's targets also lands in "Not in combat" rather than being
credited to any one boss - the farm's overall Net still includes it either way.

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
  event stream for all three modes: session start/pause/resume/stop, consumable, spell,
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
- A Boss Target Farm matches each target's name **exactly** (case-insensitive). Multi-part
  bosses whose name changes between phases, or where two die close together with a single
  loot event, can miscount a kill despite the short debounce.
- A Boss Target Farm's net only includes its targets. Stray loot shows under *Other income*
  but is not netted, and skilling / clue steps done mid-farm are not counted.
- A farm's per-boss blocks only show a **gain** icon grid - the loss grid stays a farm-wide
  figure, since cost is tracked as a gp total per mob, not per item per mob.
- Slayer task detection relies entirely on RuneLite's own built-in **Slayer** plugin (task
  name/location/progress and which spawned NPCs count for it) - if that plugin is disabled,
  the Slayer tab just shows "No task detected". Task-mob matching is built up live from
  NPCs actually seen while the run is active, so a species belonging to your task that never
  spawns near you won't be recognised until it does.
- A Slayer run's net only includes mobs that have matched the task at some point during the
  run; a stray kill shows under *Other income* but is not netted, same as a targeted farm.
  Getting reassigned mid-run doesn't stop tracking - the new task's mobs are simply folded
  in, so History's per-mob rows can span more than one task assignment within a single run.

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
