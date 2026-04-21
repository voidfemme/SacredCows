# SacredCows Fabric Mod

![SacredCows Icon](src/main/resources/assets/sacredcows/icon.png)
SacredCows is a configurable Minecraft Fabric mod that adds a humorous twist to cow interactions. It punishes players for harming cows with customizable consequences and keeps detailed statistics of cow-related offenses.

## Features

### Core Functionality

- **Configurable Punishment**: Choose between instant death, damage, or lightning-only effects
- **Custom Death Messages**: Fully customizable humorous death messages with player placeholders
- **Lightning Effects**: Dramatic visual lightning strikes (cosmetic only - won't harm cows)
- **Scoreboard Tracking**: Comprehensive tracking of cow assaults and kills
- **Projectile Protection**: Prevents cow damage from arrows, snowballs, and other player projectiles

### Administrative Features

- **Permission System**: Bypass protection for server operators
- **Admin Commands**: Reload configuration and view player statistics
- **Comprehensive Logging**: Debug mode for troubleshooting
- **Error Handling**: Robust error handling with informative logging

### Customization Options

- **Multiple Punishment Types**: Death, damage amounts, or visual effects only
- **Configurable Messages**: Edit all death messages to match your server's style
- **Scoreboard Control**: Enable/disable tracking components independently
- **Flexible Settings**: Toggle lightning effects, debug mode, and more

## Installation

1. Ensure you have a Fabric Minecraft server (version 1.21.11) set up with Fabric API installed.
2. Download the latest `sacredcows-2.1.0.jar` from the [releases section](https://github.com/voidfemme/sacredcows/releases).
3. Place the JAR file in your server's `mods` folder.
4. Restart your server.
5. Edit `config/sacredcows.properties` to customize settings (optional).
6. Use `/sacredcows reload` to apply configuration changes without restarting.

## Building from Source

To build the mod from source:

1. Ensure you have Java 21 and Gradle installed.
2. Clone this repository.
3. Run `./gradlew clean build` in the project root directory.
4. The compiled JAR will be in the `build/libs` folder.

## Usage

The mod works automatically once installed. Players will be punished for harming cows according to your configuration settings, and their offenses will be tracked on the scoreboard.

### Default Behavior

- Players who hit cows (directly or with projectiles) are instantly killed
- Lightning effect appears at punishment location (cosmetic only)
- Custom death messages are broadcast
- Statistics are tracked on server scoreboard

## Commands

| Command                                                         | Description                                 | Permission      |
| --------------------------------------------------------------- | ------------------------------------------- | --------------- |
| `/sacredcows`                                                   | Show mod info and help                      | All Players     |
| `/sacredcows reload_config`                                     | Reload configuration file                   | Server operator |
| `/sacredcows save_config`                                       | Save current settings to configuration file | Server operator |
| `/sacredcows enabled <true\|false>`                             | Enable/Disable mod                          | Server operator |
| `/sacredcows bypass <true\|false>`                              | Toggle admin bypass                         | Server operator |
| `/sacredcows lightning_effect <true\|false>`                    | Toggle lightning effect                     | Server operator |
| `/sacredcows punishment_type <death\|damage\|lightning_effect>` | Set punishment type file                    | Server operator |
| `/sacredcows stats player <name>`                               | Per-player cow stats                        | All Players     |
| `/sacredcows stats global`                                      | Global total cow stats                      | Server operator |

_Admin Commands require OP level 2 by default. (configurable)_

## Permissions

Since Fabric doesn't have a built-in permission system, SacredCows uses operator status:

| Permission | Description                          | Default              |
| ---------- | ------------------------------------ | -------------------- |
| Level 2+   | Bypass cow protection (configurable) | Manual OP assignment |
| Level 3+   | Admin command access                 | Manual OP assignment |

## Configuration

The mod creates a `config/sacredcows.properties` file with the following options:

### General Settings

```
# == General Settings ==
settings.enabled=true
settings.punishment-type=DEATH # DEATH, DAMAGE, or LIGHTNING_ONLY
settings.damage-amount=10.0
settings.lightning-effect=true
settings.custom-death-messages=true
settings.allow-bypass=true
settings.bypass-op-level=2
settings.admin-op-level=2

# == Scoreboard Settings ==
scoreboard.enabled=true
scoreboard.track-assaults=true
scoreboard.track-kills=true
scoreboard.assault-objective=cowAssaults
scoreboard.kill-objective=cowKills
scoreboard.assault-display=Cow Assaults
scoreboard.kill-display=Cow Kills

# == Death Messages ==
# Custom death messages (use %player% as placeholder)
death-messages.0=%player% was moo-rdered for their bovine crimes
death-messages.1=%player% faced divine bovine retribution
death-messages.2=The cows fought back, and %player% lost
death-messages.3=%player% learned the hard way not to mess with cows
death-messages.4=A mysterious force struck down %player% for harming a cow

# == Permissions ==
# Permission strings (used with operator status)
permissions.bypass-permission=sacredcows.bypass
permissions.admin-permission=sacredcows.admin
```

## Compatibility

- **Minecraft Version**: 26.1
- **Server Software**: Fabric Server with Fabric API
- **Java Version**: 25

## Recent Updates

### Version 3.1.0 (Latest)

- Added a full command menu.
- Fixed a small bug with cleanup and tick-counting mechanics.

### Version 3.0.0

- Updated for Minecraft 26.1 and Fabric Loader 0.18.4
- No gameplay or configuration changes; purely an environment compatibility update

### License Change

- Dropped formal licensing - individuals can use freely, corporations can deal with the
  legal uncertainty.

### Version 2.2.0

- Updated for Minecraft 1.21.11 and Fabric Loader >= 0.18.2
- No gameplay or configuration changes; purely an environment compatibility update

### Version 2.1.0

- Updated for Minecraft 1.21.9 and Fabric Loader 0.17.2
- No gameplay or configuration changes; purely an environment compatibility update

### Version 2.0.1

- **Bypass Control**: Added `settings.allow-bypass` config option to disable admin bypass permissions
- **Permission Flexibility**: Admins can now be subject to cow protection regardless of OP status
- **Testing Enhancement**: Easier testing without needing to deop administrators

### Version 2.0.0

- **Complete Fabric Migration**: Converted from Bukkit plugin to Fabric mod
- **Event System Rewrite**: Replaced Mixins with Fabric's event API for better stability
- **Enhanced Projectile Detection**: Now protects cows from arrows and other player projectiles
- **Cosmetic Lightning**: Lightning effects no longer harm cows (good praxis!)
- **Properties Configuration**: Switched from YAML to Java Properties format
- **License Change**: Released into public domain (CC0-1.0)
- **Command Updates**: Changed from `/cowmurder` to `/sacredcows`

### Previous Versions

- **Version 1.2.0**: Minecraft 1.21.7 support with Paper API migration
- **Version 1.1.0**: Complete rewrite with configuration system and enhanced features
- **Version 1.0.3**: Updated for Minecraft 1.21.1 compatibility

## Technical Details

SacredCows uses Fabric's event system for reliable cow protection:

- `ServerLivingEntityEvents.ALLOW_DAMAGE` for preventing cow damage
- `ServerLivingEntityEvents.AFTER_DEATH` for tracking kills and custom death messages
- Proper projectile source detection for comprehensive protection
- Scoreboard integration using Minecraft's native scoreboard system

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

I don't believe in intellectual property. This code has no formal license - use it however
you want. I will never pursue legal action against anyone for any use of this code.

## Support

For support, please open an issue on the GitHub repository.

## Disclaimer

Use this mod at your own risk. The author is not responsible for any damage to your server, player frustration, or the inevitable cow uprising that may result from their newfound invincibility. Cows, on the other hand, may send thank-you notes.
