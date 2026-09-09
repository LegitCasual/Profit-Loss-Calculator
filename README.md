# Profit Loss Calculator

A RuneLite plugin that measures **profit and loss** - loot and ground pickups coming in,
supplies / spells / teleports / ammo / deaths going out - for a play session, per kill of one
mob you name, or for your current Slayer task, with a lifetime per-mob history, a per-item loot
ledger, and a JSON Lines event log. An opt-in **always-on** mode tracks the whole time you're
logged in.

**Always-on tracking** (opt-in, **off by default**): turn it on and loot, ground pickups, cost
and skilling accrue in a background run the whole time you're logged in - no Start needed. It
begins only once your inventory and equipment have finished loading, so your worn kit is never
counted as loot. Pressing Start on a Target Farm or Slayer run takes the foreground; the
background run checkpoints itself to History and resumes once you Stop. Turn the toggle back
off and the background run stops immediately (an explicit Start-Stop run keeps going).

Income is tracked in tiers, because **no profit is real until a sale happens**:

- **Potential** - GE snapshot value of the loot you collected. This is what the live tabs
  show as **Potential net** (`potential − cost`). Best case, if you sell everything now.
- **Banked** - the slice of that loot you've secured in a bank / deposit box. Not sold, but
  safe. *(History / Items tabs.)*
- **Realised** - what the loot **actually** made: Grand Exchange sale proceeds **minus the
  2% GE tax**, plus High Alch coins. *(History / Items tabs.)*
- **Actual net** - `realised (what's sold / alched) + snapshot value of everything still
  unsold − cost`. Converges on the truth as you sell. Shown next to Potential net in History,
  and on the live tabs once you've cashed something out this run.

Realised / banked are matched **globally by item id** - sell 500 dragon bones and it doesn't
matter which trip or which mob they came from, only that you never credit more sold quantity
than you have ever looted. Sell *more* than you looted (bank stock sold alongside) and the
extra is ignored, its proceeds pro-rated down to the looted quantity.

Behind the scenes there's still a **Dropped** figure (everything that hit the floor, collected
or not) and a *Count uncollected drops* toggle to fold the uncollected part into potential.

## Modes

The dropdown has four entries. **Session**, **Target Farm** and **Slayer** are runs you Start;
**Items** is a read-only view. With *Always-on tracking* on, the Session run also starts on its
own once you're logged in.

- **Session** - a flat "record my profit / loss for this stretch of time" run. Loot in,
  supplies out, one net number on the tab. This is also the only mode that tracks **skilling**
  (materials consumed out, product made in). Behind the scenes it still splits per mob / skill
  and feeds that into the History tab. With *Always-on tracking* on it runs from login and
  **Stop** just splits the run (checkpoints the stretch into History, opens a fresh one).
- **Target Farm** - you type a mob name and every kill of it is tracked individually,
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
- **Items** - a read-only **loot ledger**: one row per item you have ever looted (any run,
  any mob), showing how many you looted and their snapshot value, how many you have sold /
  alched and for what (net of tax), how many are banked, and how many are still unsold. The
  header carries lifetime **Potential** and **Actual net**. This is where the global-by-item
  cap is visible: an item sold beyond its looted quantity is flagged *capped at looted*.

### Panel layout

A **History** button sits above a dropdown that switches between the four modes.

**Session**

- **Start / Pause / Resume** - one button that flips with state. Pausing stops accrual
  (banking / afk); resuming re-bases the trackers. **Stop** finalises, **Restart** stops and
  starts fresh. With *Always-on tracking* on there is always a background run, so the button is
  **Pause / Resume** and **Stop** just splits the run (checkpoints the stretch into History,
  opens a fresh one).
- **Summary block** - Net, gp/hr, Gains, Losses, at-risk, then two item grids:
  - **green** - everything picked up, biggest value first
  - **red** - everything consumed (supplies, teleports, ammo, runes)
- **Income** / **Costs** - extra detail lists, **off by default** (*Show income list* /
  *Show cost list* in the config).
- **Deaths** - a row per death awaiting a fee / gravestone decision.

**Target Farm**

- Type a mob name, **Start farm**. While it runs, the same field relabels to **Add mob** -
  keep typing names and adding them to grow the farm; **Pause / Resume**, **Stop**, **Restart**
  (brings back every target the farm had). The search field and its button are stacked full
  width, one above the other.
- The search box knows every Slayer creature plus the bosses that have no task (Nex, the
  Nightmare, Corporeal Beast, Scurrius, Yama, the Hueycoatl, ...). The **collective labels**
  *Moons of Peril*, *Dagannoth Kings* and *The Royal Titans* drop every boss of that
  encounter into the farm in one go.
- **Summary block** - the combined total across every target: Net, **GP/kill**, Gains, Losses,
  gp/hr.
- **Per mob** - only shown once you've added more than one target: one block per mob, each
  with its own net, kills, GP/kill and its own gain icon grid. Losses stay a farm-wide figure
  here - cost isn't tracked at the item level per mob, only as a total.
- **Per kill** - a row per kill (`#37  14:32   +12,400`), hover for that kill's drops; shows
  the mob's name per row once you're farming more than one target.
- **Other income** - loot from anything you killed that was never added to the farm, grouped
  by source. Shown for context; never part of the net.
- **Costs** (with *Show cost list*) and **Deaths**.

The name match is exact and case-insensitive, with a leading "The " treated as optional
(so *The Kalphite Queen* - the Slayer assignment name - and *Kalphite Queen* - the NPC -
both count). A mob that never fires a death event still counts - its loot is taken as the
kill signal. Raid bosses (CoX / ToB / ToA) are not suggested: their loot comes from a chest
rather than the boss, so a farm of one would count nothing.

**Slayer**

- Shows the currently detected task (name, location, kills left) as soon as you have one -
  no typing required. **Start tracking**, then **Pause / Resume**, **Stop**, **Restart**
  while it runs.
- **Summary block** - Net, **GP/kill**, Gains, Losses, gp/hr, average kill time.
- **Per kill** - like the Target Farm, but each row also carries the mob's name, since a task
  can span several species.
- **Other income**, **Costs**, **Deaths** - same as the Target Farm. A stray kill that never
  matched the task shows under *Other income* only.

**History**

Every run - the always-on Session, targeted farms **and** Slayer tasks - feeds this, and its
mobs merge by name: a mob you fought across three sessions, one farm and a Slayer task is one
tidy row. Two seamless sub-tabs, toggled by a button pair at the top:

***Money*** - what you've actually made.

- **Net** - `confirmed cash in − cost to attain it`. Confirmed = realised proceeds (GE sales
  net of the 2% tax + High Alch coins); cost = supplies / spells / teleports / ammo / deaths /
  skilling materials.
- **Estimated item value vs Money attained** - the GE-snapshot worth of everything you've
  looted, next to how much of it has become real gp (with the % converted, and what's still to
  sell / sitting in a bank).
- **Profit / loss by mob** - one clickable row per mob, `loot GE value − cost` (realised is a
  global figure now, not per mob - the **Items** tab has the per-item split).
- **GE Sales log** - a flat, newest-first list of every GE sale and High Alch (date, item,
  net, −tax) with a running total.

***Mobs*** - the kill log.

- Total kills / runs / estimated loot value, then one **box per mob**, sorted by potential net:
  a header (`Brutus ×426`, coloured net) over an icon grid of that mob's merged drops, with a
  `+loot −cost` sub-line. **Click a box** to drill into the individual runs and the potential /
  GE-value / cost / deaths breakdown.
- Cost that couldn't be tied to a mob (a teleport home while not in combat, etc.) collects in
  a **"Not in combat"** row at the bottom.

**Clear history** (either tab) wipes `history.jsonl`, `realised.jsonl`, `banked.jsonl` and the
per-session logs (with a confirmation). `ge-slots.json` is kept - it is live GE state.

**Items**

The **loot ledger**, read-only, one row per item id you have ever looted across every run:

- **Header** - lifetime **Potential** (`Σ looted at snapshot`), **Actual net**, and a
  `realised … / −tax / banked …` line, plus item count and total cost.
- **One row per item**, looted-value first: icon, name, and
  `looted 500 (1.9M) · sold 300 → 1.2M (−24k tax) · 150 unsold (630k)`.
- **The cap**: `sold` counts at most what you looted. Sell 20 of an item you only looted 15
  of and the row shows `20 sold, capped at looted` - 15 count as realised, the proceeds
  pro-rated down, the extra 5 (bank stock) ignored. Delete a mob from History and the looted
  quantity shrinks, so realised auto-re-caps to match.

### In-game overlays

Both are off by default and toggled in the config:

- **In-game overlay** - the running session's net, pickups, spend and kill tally as a panel
  while a session is active.
- **"No session" reminder** - a small red *"No profit loss session started"* note pinned to
  the top-right whenever nothing is being tracked and you're out in the world. It stays
  hidden at a bank / deposit box (and for a few seconds after closing one), within ~12 tiles
  of a bank booth, chest or banker, at the Grand Exchange, and inside a player-owned house.
  Turning it off removes it from the screen immediately.

### Per-mob cost attribution

In every mode, each supply, spell, teleport, ammo charge and death is charged to **whichever
NPC you were fighting** at that moment (kept sticky for a few seconds after the last hit, so
tank-eating right after a kill still counts). It's best-effort - drink a potion while running
between packs and it lands in "Not in combat". In a Target Farm, cost incurred fighting
something that isn't one of the farm's targets also lands in "Not in combat" rather than being
credited to any one mob - the farm's overall Net still includes it either way.

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
- **High Alchemy** - alching a looted item is a **realisation**: the item drops out of the
  live *potential*, and its HA coin value goes into *realised* (History / Items tabs). The
  rune cost is charged separately as always.
- **Skilling** *(Session mode, `Track skilling` on)* - while a plain Session runs, any tick
  that gives XP in a tracked non-combat skill (Mining, Fishing, Woodcutting, Hunter, Cooking,
  Firemaking, Smithing, Crafting, Fletching, Herblore, Runecraft, Construction, Prayer) has
  its inventory / worn / rune-pouch change booked to that skill: what left is a **cost**
  (logs, ores, bars, secondaries, bones, ...), what arrived is **income** (the product) -
  each attributed to the skill name, so History keeps one lifetime row per skill. Cost is GE
  priced; the product uses your chosen loot valuation. Runes, teleport charges, and ammo
  you're also firing this session are excluded (priced by their own paths) - but ammo you
  fletch/smith and *don't* fire counts as a product. Not tracked during a Target Farm or
  Slayer run.

*Potential* is valued at the **GE price**, the **High Alchemy value**, or **whichever is
higher** - your choice in the config. Coins are always face value. It is a snapshot at the
moment of receipt; a **running** session re-prices live as GE prices move, and the moment a
run **stops** its potential is frozen at that day's prices.

### Realised & banked *(History / Items tabs)*

*(`Track realised & banked` on - it is by default)* Potential is a snapshot; what you actually
bank is lower (prices drift, you undercut to move stock, the GE takes a 2% tax). The plugin
follows your loot the rest of the way and matches it back **globally by item id**:

- **Realised** - watching your **GE sell offers** (net of the 2% tax) and **High Alchs**, each
  is credited against the loot ledger for that item. The credited quantity is capped at the
  total you have ever looted of that item; sell more (bank stock alongside) and the extra is
  ignored, its proceeds pro-rated down to the looted quantity.
- **Banked** - watching the inventory while a bank / deposit box is open, each deposit (net of
  withdrawals) of a looted item is tracked. A item's *banked* figure is the GE-snapshot value
  of the unsold loot currently sitting in a bank (clamped to what's actually unsold).
- **Not per mob.** It does not matter which mob or which run an item came from - only the
  item id and the never-exceed-looted cap. A display-only mob hint (the mob you looted the
  most of that item from) is kept for the Sales log, nothing more.
- Loot from the **always-on Session** is eligible as soon as it checkpoints (every ~2.5 min,
  and on opening a bank / on Stop), so a sale right after a trip is caught without a Stop.
- **GE tax**: 2%, floored per item (nothing under 50 gp), capped 5M/item; Old School bonds
  exempt. Turn off *Deduct GE tax* to record the gross. `ge-slots.json` persists per-slot
  state so a sale that finishes while you're logged out is counted once, on next login.
- Buy offers are ignored - supplies are costed by what you use, not what you buy.

The **History → Money** tab is the lifetime reconciliation (`Net`, `Estimated value vs Money
attained`, per-mob P/L, GE Sales log); **History → Mobs** is the kill log; the **Items** tab
is the per-item breakdown.

The live Session / Target Farm / Slayer tabs headline **Potential net** until you cash
something out this run; from then on **Actual net** (`realised proceeds + loot still held at
GE − cost`) leads in bold with Potential net as a sub-line, so a run you've alched or sold
from doesn't read as a pure loss. The rate / per-kill figure follows whichever is leading.
These are live hints - the History tab is the source of truth once the run has stopped.

### Cost

- **Consumables** - food and potions at GE value, charged on the dose / bite that **actually
  leaves your inventory**, not per `Eat` / `Drink` click (holding the button or spam-clicking
  a potion fires many clicks but the game only consumes one per ~3 ticks). A dosed potion is
  charged the drop in value from one dose to the next (an empty vial for the last sip) when
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
- **Skilling materials** - in a plain Session with *Track skilling* on, anything a skilling
  action consumes (see *Income → Skilling*), GE priced.
- **Deaths** - a pending entry the moment you die. When the lost items come back the
  plugin reads the Death's Office fee from chat (or estimates it from the modern
  Item Retrieval tiers) and you confirm or zero it (gravestone) in the panel. Never
  reclaimed by the end of the session -> counted as a full loss.

All cost prices are live `ItemManager` GE lookups - only item identity and game-rule
quantities (rune counts, doses, charge tiers) are in code.

## Logs

Files under `.runelite/profit-loss-calculator/`:

- **`history.jsonl`** - one line per run
  (`{schema:3, kind, start, end, durationSec, valuation, perMob}`) where `perMob` maps a
  mob name to `{kills, gained, dropped, cost, deaths, items}`. This is what the History tab
  reads and merges. The always-on Session run is **checkpointed** here (upsert by `start` -
  replaced in place, never duplicated) so its loot is sale-eligible before you Stop. On first
  run older schema lines are upgraded in place and the original is kept as
  `history-v1-backup.jsonl`.
- **`session-<timestamp>.jsonl`** (with *Write session log file* enabled) - the detailed
  event stream for all modes: session start/pause/resume/stop, consumable, spell,
  teleport, skilling, kill, loot, death pending/returned/resolved. The `session_stop` line
  carries the full summary (cost split now including `skilling`), a per-kill breakdown and
  the per-mob rollup.
- **`realised.jsonl`** *(`Track realised & banked` on)* - one line per GE sale / High Alch
  chunk: `{schema:1, kind, runStart, mob, itemId, qty, gross, tax, net, projected, soldAt}`
  (`kind` is `ge` or `alch`; absent on pre-existing lines = `ge`; `runStart` is unused now,
  `mob` is a display hint). The Items / History tabs fold these by `itemId`. Round-18/19 lines
  still parse unchanged.
- **`banked.jsonl`** - one line per bank / deposit-box move of looted stock:
  `{schema:1, runStart, mob, itemId, qty, unit, at}` (`qty` signed: + deposited, − withdrawn),
  folded by `itemId`.
- **`ge-slots.json`** - the last-seen state of your 8 GE slots, so a sale that finishes
  while you're logged out is counted once rather than twice. Not touched by *Clear history*.
- `history.jsonl` is byte-compatible with older versions (schema 3) - all the tier data lives
  in the sidecars, so a copy of the plugin without this feature still reads your history fine.

## Known limitations

- **Skilling** (Session mode only) is caught by watching for a non-combat skill XP gain and
  booking that tick's inventory change as the skill's materials / product (see
  *Income → Skilling*). It is best-effort: an item change more than one tick from the XP tick
  is missed; Farming (XP lands long after the seed cost), Agility, Thieving and Magic-only
  methods (plank make, tan leather, string jewellery, ...) are not covered; a stack you
  fletch and later fire as ammo is counted on both sides. Off during a Target Farm / Slayer
  run.
- **Potential** is a GE/alch snapshot at receipt - the live tabs' headline. **Realised** /
  **banked** (History / Items tabs) follow the loot further, matched **globally by item id**
  and capped at the lifetime looted quantity, but: which mob / run an item came from is not
  distinguished (only a display hint); non-GE sale routes (trading a friend, staking) are
  invisible; a `deposit-worn` of looted gear isn't seen (inventory deposits only); a sale
  during the brief window before the always-on Session first checkpoints isn't caught until
  the next checkpoint or Stop. Turn it off with *Track realised & banked*.
- Between looting an item and selling it, the live tab's Potential still counts an item you've
  since alched; the Items / History tabs reconcile it (item → realised) at the next checkpoint.
- The always-on Session **checkpoints** every ~2.5 minutes (and on opening a bank / on Stop) -
  loot from the last few minutes may not be sale-eligible until the next checkpoint. With
  *Always-on tracking* off there is no background run and nothing accrues outside an explicit
  Start-Stop.
- "Collected" leans on watching items enter the inventory shortly after they drop. Loot
  picked up much later (full inventory, came back for it) may stay counted as potential
  only unless you click Take on it.
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
- A Target Farm matches each target's name **exactly** (case-insensitive, leading "The "
  optional). Multi-part bosses whose name changes between phases, or where two die close
  together with a single loot event, can miscount a kill despite the short debounce.
- A Target Farm's net only includes its targets. Stray loot shows under *Other income*
  but is not netted, and skilling (Session-mode only) / clue steps done mid-farm are not
  counted.
- A farm's per-mob blocks only show a **gain** icon grid - the loss grid stays a farm-wide
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
