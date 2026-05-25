# Sacred Cows Feature Backlog

- [ ] One-shot immunity
- [ ] Configurable cow health
- [ ] Posthumous Milk Teleport
- [ ] Heat System
- [ ] Burst Devotion
- [ ] Streak Devotion
- [ ] Cow-Spirit Altar

## One-Shot Immunity

**Mechanic:** If a player's hit kills the cow (drops it to <=0 HP), the player is immune to
the standard punishment. Strong enough players can avoid consequences.

- **Setting** `one_shot_immunity` toggles
- **Configurable Cow Health** becomes the difficulty dial for this mechanic

## Configurable Cow Health

**Mechanic**: Cow max health can be set arbitrarily. Cows do not magically heal
when increasing their health, only their max goes up

- When a cow spawns, set max health to `config.cowHealth.get()`
- When the setting changes, **Apply to existing cows**

## Posthumous Milk Teleport

**Mechanic**: If the cow attached to your teleportation milk dies, drinking that
milk teleports the player 1000+ blocks out to a random location with _Darkness_
and _Nausea_ for ~10 sec.

## Heat System

**Mechanic**: Per-player, cross-dimension heat value. Cow harm raises heat. Heat
tier governs cow behavior.

**Tiers**:

- 0-10: normal cow behavior
- 11-25: cows are nervous and flee at greater distance
- 26-50: cows actively flee on sight
- 51-100: cows become neutral-aggressive, won't attack first but won't flee
- 101+: cows hostile on sight and attack the player.

**Decay**:
Heat builds fast, decays slowly.

**Visual Feedback**:
Title/subtitle on threshold crossings ("THE COWS REMEMBER") + behavioral changes
across tiers.

**Interactions**:

- One-shot immunity still adds heat.
- Bypass ops do not accumulate heat (check that current code does not add bypass
  kills to the scoreboard)

## Burst Devotion

**Mechanic**:
Feed N cows in a short window and receive short-lived, intense buffs from the
cow gods.

Requires good standing (below heat threshold) for buffs to work. Otherwise the
feeding restores 'heat' but it doesn't count towards blessings

## Streak Devotion

**Mechanic**:
Daily feeding streak. One cow per day maintains the streak. Long streaks unlock
blessings.

**Day boundary**: In-game days only. While logged out, your streak freezes until
you log back in again. Forgetting to feed your cows while logged in counts
against you.

**Decay model**:
Incrementing subtraction during absence (-1 day one, -2 day two, -3 day three...)

**Reset Triggers**:

- Streak <=0 from decay
- Missed days > 10% of peak streak

**Recovery**:
Asymmetric _against_ the player. Missed days cost more than feeding restores.
Exact ratio TBD, but the current candidates:

- Linear (-1/day decay with sub -+1 feeding gains)
- Compounding (+1 feeding (where the geometry alone produces ~3x asymmetry))

## Cow-Spirit Altar

**Mechanic**:
Build an altar to the cow gods, offer them a diamond, and receive a cow warrior
loyal to you alone. Alternate version is that a giant golden calf structure made
out of gold blocks works like a super-beacon, with double or triple the radius,
depending on the amount of gold required.
