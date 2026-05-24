# Changelog

All notable changes to CowMurder will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [5.0.0]

### Breaking Changes

- All per-setting commands have been removed in favor of a unified command
  interface. Existing scripts will break. Old → new:
  - `/sacredcows enabled <bool>` → `/sacredcows toggle mod_status` (or `enable`/`disable`)
  - `/sacredcows bypass <bool>` → `/sacredcows toggle bypass_status`
  - `/sacredcows debug <bool>` → `/sacredcows toggle debug_mode`
  - `/sacredcows lightning_effect <bool>` → `/sacredcows toggle lightning_effect`
  - `/sacredcows punishment_type <mode>` → `/sacredcows set punishment <mode>`
  - `/sacredcows save_config` → `/sacredcows config save`
  - `/sacredcows reload_config` → `/sacredcows config reload`
  - `/sacredcows print_config` → `/sacredcows config print`
- `PunishmentMode.fromString` no longer accepts the aliases `kill`, `hurt`. Use `death`, `damage`, `lightning_only` OR `lightning-only`.
- `/sacredcows config print` now requires operator permission (previously
  available to everyone).
- `CowPositionsData` is no longer a singleton. The static `getInstance()`
  factory has been removed; callers must now construct it with
  `new CowPositionsData(owner, config)` and call `load()` explicitly.
- `CowProtectionFeature` constructor parameter order changed from
  `(SacredCows, CowConfig, TickCounter)` to `(SacredCows, TickCounter, CowConfig)`.
- `CowChunkLoaderFeature` constructor now takes
  `(SacredCows, TickCounter, CowPositionsData, CowConfig)` instead of
  `(SacredCows, TickCounter)`.

### Added

- New unified command surface:
  - `/sacredcows toggle <setting>` — flip a boolean setting
  - `/sacredcows enable <setting>` / `disable <setting>` — set a boolean explicitly
  - `/sacredcows set <setting> <value>` — set any setting to a value
  - `/sacredcows get <setting>` — show a setting's current value
  - `/sacredcows config <print|save|reload>` — config file operations
- Tab completion for setting names on all of the above.
- New `Setting` sealed interface hierarchy (`BoolSetting`, `IntSetting`,
  `DoubleSetting`, `StringSetting`, `EnumSetting<E>`) that owns its own
  parsing, serialization, and chat-display formatting.
- `/sacredcows stats` commands now report when the scoreboard or
  individual assault/kill tracking is disabled, rather than silently
  printing nothing.
- Build now compiles with `-Xlint:all -Werror`.
- Shadowed `error_prone_annotations` 2.49.0 to silence the older version
  pulled in transitively by Gson.

### Changed

- Configuration is now driven by a single registry in `CowConfig` rather
  than scattered fields and three parallel enums.
- `/sacredcows config print` output now iterates settings generically and
  marks unsaved changes uniformly, instead of hand-listing each one.
  Changed values are now highlighted in bold aqua with a `<- (changed)`
  marker.
- Verbose cow position/chunk tracking logs in `CowChunkLoaderFeature` are
  now gated behind the `debug_mode` setting.
- Log messages standardized with a `sacredcows:` prefix; exceptions are
  now passed as `Throwable` arguments to the logger rather than
  `.getMessage()`, preserving full stack traces.
- Tab completion now uses `SharedSuggestionProvider.suggest` for proper
  prefix filtering instead of suggesting every option unconditionally.

### Removed

- `SettingsEnum`, `ScoreboardEnum`, `PermissionsEnum`, and the
  `CowConfigKeys` interface they implemented.
- Dozens of per-setting getter/setter pairs on `CowConfig`. Callers now
  access settings directly as fields (`config.modStatus.get()`, etc.).
- Dead `MilkTeleportFeature` stub file.

### Fixed

- `EnumSetting` was using `StringSetting.class` for its SLF4J logger,
  causing its log messages to be misattributed.

## [4.1.0]

### Changed

- Chunk loading for named cows migrated from `setChunkForced` to a custom `TicketType` (`sacred_cows`) with `addTicketWithRadius` / `removeTicketWithRadius`, which is the API-appropriate path for forced chunk loading in 1.21.x and doesn't persist into the world's forced-chunks list
- Permission level handling now uses the `PermissionLevel` enum throughout instead of raw `int`s, with parsing centralized in `CowConfig.getPermissionLevel()`. The reader accepts either an int id or an enum name and falls back to `GAMEMASTERS` on invalid input
- `executeSetPunishmentMode` now returns a failure (status 0) with a user-facing error on invalid input instead of silently defaulting to `DEATH`
- `/sacredcows config` now styles the current punishment mode in yellow to match the visual treatment of other config values, and normalizes case when comparing the in-memory value to the saved property so the "changed" indicator behaves correctly
- Cow names in log messages and the death broadcast now use `getCustomName().getString()` instead of letting the raw `Component` stringify itself (which produced `literal{Bessie}` instead of `Bessie`)
- Logging cleaned up across the mod: `System.err.println` calls in `CowConfig` replaced with `LOGGER.error`, hardcoded logger name in `CowProtectionFeature` replaced with the class name, and concatenated log strings converted to SLF4J parameterized format

### Fixed

- 3x3 chunk-loading radius around named cows actually covers a 3x3 area now; the previous implementation looped over `dx`/`dz` offsets but constructed `new ChunkPos(center.x(), center.z())` inside the loop, so it only ever added the center chunk nine times
- `CowChunkLoaderFeature.onCowDeath` no longer registers a fresh `AFTER_DEATH` listener every time a cow dies — the listener is now registered once in `registerEventHandlers` and the cleanup logic is called directly. (This was a slow-growing listener leak: every named-cow death added another listener, and every existing listener fired on every subsequent death.)
- Saved cow positions are now actually restored on server start. `CowChunkLoaderFeature` reads from the `CowPositionsData` singleton on `SERVER_STARTED` and re-adds chunk tickets for each persisted position before scanning live entities. The save path (`SERVER_STOPPING`) was already wired up; the load path had been missing
- Named cows no longer leak chunk tickets as they wander. When a cow moves to a new chunk, the ticket on the previous chunk is now removed (this was dropped silently during the `setChunkForced` → ticket-API migration earlier in 4.0.3)
- `CowConfig.save()` now writes permission levels as integer ids rather than `String.valueOf(enum)`, which was producing strings like `GAMEMASTERS` that the loader couldn't parse back. Configs with non-default `bypass-op-level` or `admin-op-level` were silently resetting to default on restart
- `CowPositionsData.getInstance()` is now `static`, so it can actually be used as a singleton accessor
- `CowConfig` constructor is now called with the real `configFile` path instead of `null` in `SacredCows.loadConfig`, so `save()`, `load()`, and `createDefaultConfig()` are no longer no-ops
- Eliminated several redundant `AFTER_DEATH` registrations in `SacredCows.onInitialize` that duplicated work already done inside feature classes

### Removed

- `CowConfig.setDefaultProperties()` — unreachable; `createDefaultConfig()` writes defaults directly via the enum iterators
- `SacredCows.getVersion()` and the `/version.properties` read it depended on — unused
- `cleanupTickCounter`, `cowTickCounter`, and the public `serverTickCounter` field on `SacredCows`, along with the inline tick-dispatch loop in `onInitialize`

### Technical

- Tick-driven work centralized in a new `util.TickCounter` class that exposes `registerIntervalCallback(intervalTicks, Runnable)`; `CowProtectionFeature` and `CowChunkLoaderFeature` now register their periodic work through it instead of `SacredCows` owning the tick loop
- `CowProtectionFeature` and `CowChunkLoaderFeature` take a `TickCounter` via constructor injection
- `ScoreboardFeature` now owns its own `SERVER_STARTED` registration via `registerEventHandlers()` instead of being called inline from `SacredCows`
- `CowChunkLoaderFeature` owns its own `SERVER_STARTED`, `ENTITY_LOAD`, `AFTER_DEATH`, and `SERVER_STOPPING` registrations in one place
- `instanceof` pattern variables adopted in `CowProtectionFeature.getPlayerFromDamageSource` (`projectile instanceof Projectile proj`, `projectile instanceof TraceableEntity ownable`)
- Build now compiles with `-Xlint:deprecation`
- Bumped `mod_version` to `4.1.0`

### Upgrade notes

- **Chunk loading mechanism changed.** 4.0.2 and earlier used the world's
  forced-chunks list (the one populated by `/forceload`) to keep named cows
  loaded. 4.1.0 uses Fabric's ticket system instead, which doesn't persist to
  disk. After upgrading, existing worlds may have leftover entries from
  4.0.2 in their `/forceload query` output. These are harmless but can be
  cleared with `/forceload remove <x> <z>` for each, or `/forceload remove all`
  if you have no other manually-set forced chunks.

## [4.0.2]

### Added

- Named cows now keep a 3x3 area of chunks around them force-loaded,
  preventing the cow from wandering into unloaded chunks
- Cow chunk positions are saved to disk on server stop and reloaded on
  server start, so teleportation works across restarts
- Cow chunk positions are updated every 100 ticks (5 sec.) to track
  wandering cows
- Named milk buckets now correctly tag stacked buckets with the cow's
  UUID and display name

### Changed

- Cow invincibility is now configurable (default: off)
- Config system refactored to use `SettingsEnum`, `ScoreboardEnum`, and
  `PermissionsEnum` for type-safe property keys with comments and defaults
- Commands renamed for consistency (`mod_enabled`, `bypass_enabled`, etc.)
- Logger instances now use class-based names instead of hardcoded strings

### Technical

- Added `CowChunkLoaderFeature` for chunk management
- Added `CowPositionsData` for persistent cow position storage via JSON
- Added `data` package for world-persistent mod data

## [2.1.0] - 2025-10-02

### Changed

- **Minecraft Compatibility**: Updated from 1.21.7 to 1.21.9
- **Fabric Loader**: Bumped from 0.16.14 to 0.17.2
- **Fabric API**: Updated from 0.129.0+1.21.7 to 0.134.0+1.21.9
- **Yarn Mappings**: Updated to `1.21.9+build.1` for latest method/field mappings
- **Permissions Handling**: Replaced legacy `isOperator()` check with proper `hasPermissionLevel()` to align with modern Fabric/Minecraft standards

### Technical

- **Gradle**: Updated `processResources` task to use `providers.provider` for version expansion (removes deprecation warnings)
- **Codebase**: Unified punishment logic by storing `ServerWorld` reference once per event
- **Documentation**:
  - Updated README with new icon, compatibility info, and permission level table
  - Added changelog entry for 2.1.0
- **Assets**: Added `icon.png` for mod metadata and README badge

### Compatibility

- **Minecraft**: 1.21.9 only (no longer supports 1.21.7)
- **Server Software**: Fabric Server with Fabric API
- **Java**: Requires Java 21

## [1.2.0] - 2025-07-12

### Added

- **Paper API Support**: Migrated from Spigot to Paper API for enhanced performance and features
- **Adventure Component API**: Modern rich text messaging system replacing legacy string-based messages
- **Mojang Mappings**: Added official Mojang mappings support for better compatibility
- **Enhanced Build Configuration**: Comprehensive Gradle build improvements with proper manifest attributes
- **Command Aliases**: Added `/cm` alias for the main `/cowmurder` command

### Changed

- **Minecraft Compatibility**: Updated from 1.21.4 to 1.21.7 support
- **API Migration**: Replaced deprecated Spigot APIs with modern Paper equivalents
  - Updated `event.setDeathMessage()` to `event.deathMessage(Component)`
  - Replaced string-based scoreboard creation with `Criteria.DUMMY` and `Component.text()`
  - Migrated to Adventure Components for all user-facing messages
- **Build System Modernization**:
  - Updated Gradle from 8.9 to 8.14.3
  - Migrated Shadow plugin from `com.github.johnrengelman.shadow` to `com.gradleup.shadow`
  - Updated Shadow plugin from 8.1.1 to 8.3.7 (eliminates Gradle 9.0 deprecation warnings)
  - Added paperweight userdev plugin for Paper development
- **Plugin Metadata**: Updated `getDescription().getVersion()` to `getPluginMeta().getVersion()`
- **Code Quality**: Improved code formatting and structure throughout

### Fixed

- **Gradle Deprecation Warnings**: Eliminated `FileTreeElement.getMode()` deprecation warning
- **Build Compatibility**: Resolved plugin resolution issues with updated Shadow plugin coordinates
- **API Deprecations**: Updated all deprecated method calls to modern equivalents

### Technical

- **Dependency Updates**:
  - Migrated from Spigot API to Paper dev bundle
  - Updated Java toolchain configuration for Paper compatibility
  - Added Adventure API dependencies for rich text support
- **Build Configuration**:
  - Enhanced shadow jar configuration with proper archiving
  - Added compiler arguments for better deprecation warnings
  - Improved plugin.yml with updated API version and mappings
- **Development Tools**: Updated wrapper and plugin versions for latest Gradle features

### Compatibility

- **Minecraft**: Now supports 1.21.7 (up from 1.21.4)
- **Server Software**: Paper, Spigot, and Paper-based forks
- **Java**: Requires Java 21 (unchanged)
- **API**: Paper API 1.21.7+ recommended, Spigot API 1.21.7+ minimum

## [1.1.0] - 2025-06-13

### Added

- **Configuration System**: Comprehensive `config.yml` with customizable settings
  - Configurable punishment types: DEATH, DAMAGE, or LIGHTNING_ONLY
  - Customizable damage amounts for DAMAGE punishment type
  - Toggleable lightning effects and death messages
  - Master enable/disable switch for the plugin
  - Debug mode for detailed logging
- **Permission System**: Role-based access control
  - `cowmurder.bypass` - Allows players to harm cows without punishment
  - `cowmurder.admin` - Grants access to admin commands
- **Admin Commands**: Server management functionality
  - `/cowmurder` - Display plugin information and help
  - `/cowmurder reload` - Reload configuration without server restart
  - `/cowmurder stats <player>` - View player's cow-related statistics
- **Enhanced Death Messages**: Fully customizable with placeholder support
  - Support for `%player%` placeholder in death messages
  - Configurable message list in config.yml
- **Comprehensive Logging**: Improved debugging and error reporting
  - Debug mode with detailed operation logging
  - Proper error handling with informative messages
  - Warning logs for configuration issues
- **Scoreboard Enhancements**: More control over statistics tracking
  - Configurable objective names and display names
  - Independent control for assault and kill tracking
  - Option to completely disable scoreboard functionality

### Changed

- **Updated Minecraft Compatibility**: Now supports Minecraft 1.21.4
- **Improved Event Handling**: Replaced temporary event listeners with proper cleanup system
- **Enhanced Error Handling**: Added try-catch blocks and null checks throughout
- **Better Code Structure**: Refactored for maintainability and readability
- **Performance Improvements**: Fixed memory leaks and optimized event processing
- **Plugin.yml Updates**: Added command and permission definitions

### Fixed

- **Memory Leak**: Eliminated memory leak from temporary event listeners that weren't properly cleaned up
- **Null Pointer Exceptions**: Added comprehensive null checks for scoreboard operations
- **Event Handler Cleanup**: Proper cleanup of event handlers to prevent resource leaks
- **Scoreboard Management**: Improved scoreboard creation and management with error handling
- **Configuration Loading**: Robust configuration loading with fallback defaults

### Technical

- **Dependency Updates**: Updated Spigot API to 1.21.4-R0.1-SNAPSHOT
- **Code Quality**: Added comprehensive error handling and logging
- **Documentation**: Created detailed configuration documentation and examples
- **Testing**: Added extensive playtest documentation (PLAYTEST.md)
- **Release Process**: Created release documentation (RELEASE.md)

## [1.0.3] - 2025-01-15

### Changed

- Updated for Minecraft 1.21.1 compatibility
- Updated Spigot API dependency to 1.21-R0.1-SNAPSHOT

### Technical

- Updated README with new version compatibility information

## [1.0.2] - 2024-XX-XX

### Added

- Initial release functionality
- Basic cow protection mechanism
- Hardcoded death messages
- Simple scoreboard tracking
- Lightning effects on punishment

### Fixed

- Various bug fixes and stability improvements

## [1.0.1] - 2024-XX-XX

### Fixed

- Initial bug fixes and improvements

## [1.0.0] - 2024-XX-XX

### Added

- Initial plugin release
- Basic cow harm detection
- Player punishment system
- Death message broadcasting
- Scoreboard tracking for cow assaults and kills
- Lightning effect on punishment

---

## Release Notes Format

### Categories Used:

- **Added** for new features
- **Changed** for changes in existing functionality
- **Deprecated** for soon-to-be removed features
- **Removed** for now removed features
- **Fixed** for any bug fixes
- **Security** for security-related changes
- **Technical** for developer-focused changes

### Version Numbering:

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR.MINOR.PATCH** (e.g., 1.1.0)
- **MAJOR**: Breaking changes or major rewrites
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

### Links:

- [Unreleased]: https://github.com/voidfemme/cow_murder/compare/v1.2.0...HEAD
- [1.2.0]: https://github.com/voidfemme/cow_murder/compare/v1.1.0...v1.2.0
- [1.1.0]: https://github.com/voidfemme/cow_murder/compare/v1.0.3...v1.1.0
- [1.0.3]: https://github.com/voidfemme/cow_murder/compare/v1.0.2...v1.0.3
- [1.0.2]: https://github.com/voidfemme/cow_murder/compare/v1.0.1...v1.0.2
- [1.0.1]: https://github.com/voidfemme/cow_murder/compare/v1.0.0...v1.0.1
- [1.0.0]: https://github.com/voidfemme/cow_murder/releases/tag/v1.0.0
