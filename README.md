# SacredCows

![SacredCows Icon](src/main/resources/assets/sacredcows/icon.png)
A configurable Fabric mod that gives cattle divine status as stewards of the
earth. Punishes cow-assaulters and gives cow-friends special buffs!

## Features

- (NEW!) Teleport to your favorite cow by naming the cow, and milking her!
  Drinking the milk will bring you back to your bovine companion's side.
- Configurable punishments: (instant) death, damage, or lightning-only
- Custom death messages with player name placeholders
- Cosmetic lightning effects (won't harm cows)
- Scoreboard tracking for cow assaults and kills
- Projectile protection (arrows, snowballs, etc.)
- OP-level bypass for admins

## Installation

1. Install a Fabric server with Fabric API.
2. Drop the JAR into your `mods/` folder.
3. Restart the server.
4. Optionally edit `config/sacredcows.properties` to customize behavior.

## Commands

| Command                             | Description                                          | Permission |
| ----------------------------------- | ---------------------------------------------------- | ---------- |
| `/sacredcows`                       | Show help                                            | Everyone   |
| `/sacredcows help`                  | Show help                                            | Everyone   |
| `/sacredcows toggle <setting>`      | Flip a boolean setting's current value               | Operator   |
| `/sacredcows enable <setting>`      | Set a boolean setting to true                        | Operator   |
| `/sacredcows disable <setting>`     | Set a boolean setting to false                       | Operator   |
| `/sacredcows set <setting> <value>` | Set any setting to a specific value                  | Operator   |
| `/sacredcows get <setting>`         | Show the current value of a setting                  | Operator   |
| `/sacredcows stats player <name>`   | Show stats for a player                              | Everyone   |
| `/sacredcows stats global`          | Show server-wide totals                              | Everyone   |
| `/sacredcows config print`          | Show current config with unsaved changes highlighted | Operator   |
| `/sacredcows config save`           | Write current settings to file                       | Operator   |
| `/sacredcows config reload`         | Reload settings from file                            | Operator   |

Admin commands require OP level 2 by default (configurable).

## Configuration

The mod creates `config/sacredcows.properties` on first run. Key settings:

```properties
settings.enabled=true
settings.punishment-type=death        # death, damage, or lightning_only
settings.damage-amount=10.0
settings.lightning-effect=true
settings.custom-death-messages=true
settings.allow-bypass=true
settings.bypass-op-level=2
settings.admin-op-level=2

scoreboard.enabled=true
scoreboard.track-assaults=true
scoreboard.track-kills=true

# Custom death messages — edit manually, %player% is replaced with the player's name
death-messages.0=%player% was moo-rdered for their bovine crimes
death-messages.1=%player% faced divine bovine retribution
```

Use `/sacredcows config save` to persist changes made via commands. Changes take effect immediately without saving.

## Compatibility

- **Minecraft**: 26.2
- **Fabric Loader**: 0.19.3
- **Java**: 25

## License

No formal license. Use it however you want.
