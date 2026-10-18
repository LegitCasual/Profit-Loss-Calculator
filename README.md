# Profit Loss Calculator

A RuneLite plugin that tells you whether your time in-game is actually making money.

It adds up what comes **in** (loot, ground pickups, skilling products) and what goes **out**
(food, potions, spells, teleports, ammo, charged-weapon charges, deaths), and shows the net -
for a play session, per kill of a boss you name, or for your current Slayer task.

## Quick start

1. Open the plugin's side panel.
2. Pick a mode from the dropdown and press **Start**.
3. Play. The panel shows your net, gp/hr and what you've gained and spent.
4. Press **Stop** when you're done. The run is saved to **History**.

Prefer not to press Start at all? Turn on **Always-on tracking** in the config and it tracks
the whole time you're logged in.

## Modes

| Mode | Use it for | You get |
|---|---|---|
| **Session** | "What did I make in this stretch of play?" | One net figure, gp/hr, gained and spent item grids. The only mode that tracks **skilling**. |
| **Target Farm** | Bossing or farming specific mobs | A real **GP per kill**, a per-kill list (hover for drops), and one block per mob if you farm several. |
| **Slayer** | Your current task | Auto-detects the task (name, location, kills left) and tracks every mob that counts toward it. |
| **Items** | Checking what your loot turned into | A read-only ledger: one row per item you've ever looted, how many you sold, banked or still hold. |

**Pause / Resume** stops tracking while you bank or go AFK. **Restart** stops and starts fresh.

### Target Farm tips

- Type a mob name and press **Start farm**. While it runs, keep typing names and pressing
  **Add mob** to grow the farm without stopping.
- The search box knows every Slayer creature plus the bosses without a task (Nex, the
  Nightmare, Corporeal Beast, Scurrius, Yama, the Hueycoatl, ...).
- **Group labels** add a whole encounter at once: *Dagannoth Kings*, *The Royal Titans*.
- **Chest encounters** are farmed under one name: *Barrows*, *Moons of Peril*, *Chambers of
  Xeric*, *Theatre of Blood*, *Tombs of Amascut*. A run counts when the chest is looted, and
  the chest's loot is that run's gain.
- Names match exactly but ignore case, and a leading "The " is optional - so *The Kalphite
  Queen* and *Kalphite Queen* both work.
- Loot from anything you didn't add shows under **Other income** and isn't part of the net.

### Slayer tips

- Needs RuneLite's built-in **Slayer** plugin (on by default).
- Aliases, superiors and boss tasks are included, matched the same way RuneLite's Slayer
  plugin does.
- Getting a new task mid-run doesn't stop tracking - the new task's mobs are folded in.

## How profit is counted

Loot isn't really money until you sell it, so the plugin tracks it in stages:

| Stage | Meaning | Where you see it |
|---|---|---|
| **Potential** | GE value of the loot you collected. Best case if you sold everything now. | Live tabs (*Potential net*) |
| **Banked** | Loot you've put in a bank or deposit box. Not sold, but safe. | History, Items |
| **Realised** | What your loot **actually** made: GE sales minus the 2% tax, plus High Alch coins. | History, Items |
| **Actual net** | Realised + value of what you still hold − cost. Gets closer to the truth as you sell. | History, and live tabs once you sell or alch something |

- **Loot value** uses the GE price, High Alch value, or whichever is higher (your choice).
  Coins are face value. A running session re-prices live; a stopped run keeps that day's prices.
- **Sales are matched by item, not by trip.** Sell 500 dragon bones and it doesn't matter
  which mob or run they came from. You can never be credited for more than you looted - sell
  extra bank stock alongside and the extra is ignored.
- **GE tax** is 2%, nothing on items under 50 gp, capped at 5M per item, bonds exempt.
- Buy offers are ignored - supplies are costed by what you **use**, not what you buy.

## What it tracks

### Income

- **Loot** - monster, boss and PvP kills, reward chests, caskets, minigame rewards and
  pickpockets, via RuneLite's built-in **Loot Tracker** (on by default). A drop counts as
  *collected* once it enters your inventory; one you leave on the ground for ~5 minutes stays
  potential only.
- **Ground pickups** - items you pick up or telegrab that didn't come from one of your kills.
  Picking your own dropped items back up isn't counted. Runes that go straight into your rune
  pouch are seen too.
- **High Alchemy** - alching a looted item turns it into realised coins. The runes are
  charged as a cost as usual.
- **Skilling** *(Session mode only)* - while training a non-combat skill, materials used are
  a cost and the product is income, filed under that skill in History. Covers Mining,
  Fishing, Woodcutting, Hunter, Cooking, Firemaking, Smithing, Crafting, Fletching, Herblore,
  Runecraft, Construction and Prayer.

### Cost

All prices are live GE prices.

- **Food and potions** - charged per bite or dose that actually leaves your inventory, not per
  click. With *Dose-aware potion cost* on, each sip costs one dose's worth.
- **Spells** - the standard spellbook and Ancient combat spells, minus any runes your staff or
  tome supplies. Manual casts only.
- **Teleports** - jewellery charges (glory, ring of dueling, games necklace, ...) and
  teleport tablets or scrolls.
- **Ammo** - arrows, bolts, darts, thrown weapons, chinchompas and cannonballs. Ava's
  recovery and cannon pickup aren't charged.
- **Charged weapons** - Venator bow (ancient essence), Eye of Ayak (demon tears) and
  Tumeken's shadow (soul and chaos runes), counted per attack, so autocasting counts.
- **Skilling materials** - see *Income → Skilling*.
- **Deaths** - logged the moment you die. When you reclaim your items, the plugin reads the
  Death's Office fee from chat (or estimates it) and you confirm it, or zero it for a
  gravestone. Never reclaimed means a full loss.

### Which mob gets the cost

Each cost is charged to **the NPC you were fighting** at the time, and stays with it for a few
seconds after your last hit so eating right after a kill still counts. A potion drunk while
running between fights lands in **Not in combat**.

**Boss adds count as the boss.** Supplies used on Phosani's Husks, Parasites and Totems are
charged to Phosani's Nightmare, and those adds don't show up as kills of their own. The same
applies to most bosses with adds - Nex's generals, the Abyssal Sire's spawn, Kraken's
tentacles, Zulrah's snakelings, Vorkath's spawn, Scurrius's rats, Jad's healers, Zuk's wave,
Dawn (counted as Dusk) and more.

- An add away from its boss is just itself again (a Giant rat outside Scurrius's lair).
- A Target Farm or Slayer task aimed at the add itself still counts it.
- Bodyguards with their own loot (God Wars, Dagannoth Kings) stay separate kills.
- In a Target Farm, cost from fighting something that isn't a target goes to *Not in combat*.
  The farm's overall net still includes it.

## History

Every run feeds History, and mobs merge by name - a boss you fought across three sessions, a
farm and a Slayer task is one row. Two tabs:

**Money** - what you've actually made.

- **Net** = confirmed cash in (GE sales after tax + alch coins) − everything you spent.
- **Estimated value vs Money attained** - what your loot is worth next to how much of it has
  become real gp.
- **Profit / loss by mob** - one clickable row per mob.
- **GE Sales log** - every GE sale and High Alch, newest first, with a running total.

**Mobs** - the kill log. One box per mob with its kill count, net and merged drops. Click a
box to see its individual runs.

**Clear history** wipes your saved runs, sales and bank records (it asks first).

## Overlays

Both are off by default.

- **In-game overlay** - a compact net and net-per-hour badge while a session runs. Hover it
  for the full breakdown.
- **"No session" reminder** - a small red note at the top-right when nothing is being
  tracked. It hides near banks, at the Grand Exchange and in player-owned houses.

## Settings worth knowing

| Setting | Default | What it does |
|---|---|---|
| Always-on tracking | Off | Track the whole time you're logged in, no Start needed. Starting a Target Farm or Slayer run takes over, and the background run resumes after. |
| Value loot at | GE | GE price, High Alch value, or whichever is higher. |
| Count uncollected drops | Off | Count loot you left on the ground toward profit. |
| Hide loot under (gp) | 0 | Leave cheap drops out of the lists and totals. |
| Track skilling | On | Skilling materials and products in Session mode. |
| Track realised & banked | On | Follow your loot to the GE and bank (History and Items tabs). |
| Deduct GE tax | On | Off records the gross sale amount. |
| Dose-aware potion cost | On | Charge per dose instead of the full potion. |
| Show income list / Show cost list | Off | Extra detail lists on the live tabs. |
| Write session log file | On | A detailed event log per run (see below). |

## Known limitations

**Tracking**
- Only **manually cast** spells are counted - autocast spells show no rune cost.
- Not priced: Ancient teleports, the Lunar and Arceuus spellbooks, Enchant spells, and the
  Kodai wand's rune-saving chance.
- Runes in your rune pouch are charged in full for spells - only an equipped staff or tome
  removes runes from the bill.
- Charged weapons other than the Venator bow, Eye of Ayak and Tumeken's shadow (blowpipe,
  tridents, Sanguinesti staff, crystal and wilderness weapons) aren't priced.
- Ammo recovered by Ava's never registers as fired.
- Loot picked up much later (full inventory, came back for it) may stay *potential* unless you
  click Take on it.

**Skilling** *(best-effort)*
- Only counts item changes on the same tick as the XP drop.
- Not covered: Farming, Agility, Thieving, and Magic-only methods (plank make, tan leather, ...).
- Ammo you fletch and then fire is counted on both sides.
- Off during Target Farm and Slayer runs.

**Sales and banking**
- Trades with players and other non-GE sales are invisible.
- Depositing worn gear isn't seen - only inventory deposits.
- The always-on run saves every ~2.5 minutes (and when you open a bank or Stop), so a sale in
  the first few minutes may not be matched until the next save.
- The live tabs may still count an item you've alched until that next save.

**Farms and Slayer**
- Bosses whose name changes between phases, or two kills landing together with one loot
  drop, can miscount a kill.
- Boss adds only count as the boss while the boss is nearby. Waves before the boss (Fight
  Caves and Inferno before Jad or Zuk, the Colosseum before Sol Heredit) and the individual
  bosses inside Barrows, Moons of Peril and the raids are still charged to themselves - in a
  farm of one of those, that cost lands in *Not in combat*.
- A farm's per-mob blocks only show gained items - spent items stay a farm-wide total.
- Slayer needs RuneLite's built-in Slayer plugin. A task mob is only recognised once one has
  spawned near you during the run.
- A Slayer run over several tasks shows them merged per mob in History.

## For developers

```
./gradlew build      # compile + unit tests
./gradlew run        # launch a dev client with the plugin loaded
```

<details>
<summary>Log files</summary>

All under `.runelite/profit-loss-calculator/`:

- **`history.jsonl`** - one line per run: `{schema:3, kind, start, end, durationSec,
  valuation, perMob}`, where `perMob` maps a mob name to `{kills, gained, dropped, cost,
  deaths, items}`. The History tab reads and merges this. The always-on run is checkpointed
  here (upserted by `start`, never duplicated). Older schema lines are upgraded in place on
  first run, with the original kept as `history-v1-backup.jsonl`.
- **`session-<timestamp>.jsonl`** *(Write session log file)* - the detailed event stream:
  start / pause / resume / stop, consumable, spell, teleport, skilling, kill, loot, and death
  events. The `session_stop` line carries the full summary, a per-kill breakdown and the
  per-mob rollup.
- **`realised.jsonl`** - one line per GE sale / High Alch: `{schema:1, kind, runStart, mob,
  itemId, qty, gross, tax, net, projected, soldAt}`. `kind` is `ge` or `alch` (absent = `ge`);
  `runStart` is unused and `mob` is a display hint. Folded by `itemId`.
- **`banked.jsonl`** - one line per bank move of looted stock: `{schema:1, runStart, mob,
  itemId, qty, unit, at}`, `qty` signed (+ deposited, − withdrawn). Folded by `itemId`.
- **`ge-slots.json`** - last-seen state of your 8 GE slots, so a sale that completes while
  you're logged out is counted once. Not touched by *Clear history*.

`history.jsonl` stays schema 3 - the realised / banked data lives in the sidecar files, so
older versions of the plugin still read your history.

</details>

## License

Free and open source under the [BSD 2-Clause License](LICENSE). Please also read
[NOTICE.md](NOTICE.md) — the licence lets you fork and modify freely; the notice is my
ask that you send changes back as pull requests rather than publishing a separate
modified copy, and keep the copyright line on anything derived from this. Fixes and
ideas are very welcome as issues / PRs.
