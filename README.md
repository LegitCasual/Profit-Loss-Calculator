# Session Cost Tracker

A RuneLite plugin that measures how much gp a play session burns - supplies, spells,
teleports, ammo and deaths - with a boss kill tally and a JSON Lines event log.

## Controls (side panel)

- **Start / Pause / Resume** - one button that flips with the session state. Pausing stops
  cost accrual (for banking / afk); resuming re-bases the trackers so the gap isn't
  charged.
- **Stop** - finalise the session and leave the summary on screen.
- **Restart** - finalise the current session (writing its summary to the log) and start a
  fresh one in one click.
- **Boss** field + **+1 kill** - set a boss name and every matching NPC death bumps the
  kill tally automatically; the button adds one by hand. The panel and overlay show the
  running cost *per kill*.

## What it tracks

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

All prices are live `ItemManager` GE lookups - only item identity and game-rule quantities
(rune counts, doses, charge tiers) are in code.

## Session log

With *Write session log file* enabled, every event (session start/pause/resume/stop,
consumable, spell, teleport, boss_kill, death pending/returned/resolved) is appended as
one JSON object per line to `.runelite/session-cost-tracker/session-<timestamp>.jsonl`.

## Known limitations

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
