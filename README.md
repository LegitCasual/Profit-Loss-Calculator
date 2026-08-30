# Session Cost Tracker

A RuneLite plugin that measures how much gp a play session burns, broken down into
**trips** (the span between bank/Grand Exchange visits), with a stop-time summary and a
JSON Lines event log.

## What it tracks (v1)

- **Consumables** – every `Eat` / `Drink` click, priced at GE value (dosed potions are
  charged per sip when *Dose-aware potion cost* is on).
- **Spells** – standard-spellbook casts, priced as the GE value of the runes needed minus
  any rune supplied by an equipped staff/tome. Detected from the spellbook click and from
  applying a selected spell to a target.
- **Deaths** – held as a *pending* entry against the trip you died on. When the lost items
  come back the plugin estimates the Death's Office reclaim fee (≈1% of value, min
  1,000gp per stack – both configurable) and you confirm or zero it (gravestone) in the
  panel. Never reclaimed by the end of the session ⇒ counted as a full loss.

## Trips

A new trip is cut when you open a bank (any bank) or walk into the Grand Exchange, but
only once you have actually left the previous bank/GE (see *Trip debounce distance*) – so
re-opening the bank in one visit or wandering the GE doesn't spam new trips. Trips with no
spending and no deaths are collapsed out of the summary.

## Session log

With *Write session log file* enabled, every event (trip open/close, consumable, spell,
death pending/returned/resolved, session start/stop/summary) is appended as one JSON
object per line to `.runelite/session-cost-tracker/session-<timestamp>.jsonl`.

## Known limitations

- Ancient / Lunar / Arceuus spellbooks, Enchant (jewellery and crossbow bolt) and
  minigame/home teleports are not priced. Ammo and items picked up from drops are out of
  scope for v1.
- The rune *pouch* is not inspected – pouch runes are consumed and still cost gp; only
  equipped staves remove a rune from the bill.
- Combat spells left "selected" and spam-cast without re-clicking the target may
  under-count. Potion sip cost and the death reclaim fee are estimates.
- Spell rune quantities are from the OSRS Wiki – spot-check `Spell.java` if a number
  looks off.

## Build

```
./gradlew build      # compile + unit tests
./gradlew run        # launch a dev client with the plugin loaded
```
