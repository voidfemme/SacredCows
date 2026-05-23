package com.voidfemme.sacredcows.config;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.voidfemme.sacredcows.config.settings.BoolSetting;
import com.voidfemme.sacredcows.config.settings.DoubleSetting;
import com.voidfemme.sacredcows.config.settings.EnumSetting;
import com.voidfemme.sacredcows.config.settings.IntSetting;
import com.voidfemme.sacredcows.config.settings.Setting;
import com.voidfemme.sacredcows.config.settings.StringSetting;
import com.voidfemme.sacredcows.features.CowProtectionFeature.PunishmentMode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(CowConfig.class.getName());
  private final Path configFile;
  private final Properties properties = new Properties();

  // == Settings (public, final, declared in one place)
  public final BoolSetting modStatus;
  public final BoolSetting debugMode;
  public final BoolSetting lightningEffect;
  public final BoolSetting teleport;
  public final BoolSetting cowInvincibility;
  public final BoolSetting bypassEnabled;
  public final BoolSetting deathMessagesEnabled;
  public final DoubleSetting playerDamageAmount;
  public final DoubleSetting cowHealth;
  public final EnumSetting<PunishmentMode> punishment;
  public final EnumSetting<PermissionLevel> bypassOpLevel;
  public final EnumSetting<PermissionLevel> adminOpLevel;

  public final BoolSetting scoreboardEnabled;
  public final BoolSetting trackAssaults;
  public final BoolSetting trackKills;
  public final StringSetting assaultObjective;
  public final StringSetting killObjective;
  public final StringSetting assaultDisplay;
  public final StringSetting killDisplay;

  public final StringSetting bypassPermission;
  public final StringSetting adminPermission;

  private final List<Setting> all;
  private final Map<String, Setting> byName;

  // Death messages stay special cased since they're list-typed
  private List<String> deathMessages =
      Arrays.asList(
          "%player% was moo-rdered for their bovine crimes",
          "%player% faced divine bovine retribution",
          "The cows fought back, and %player% lost",
          "%player% learned the hard way not to mess with cows",
          "A mysterious force struck down %player% for harming a cow");

  private static PermissionLevel parsePermLevel(String raw) {
    try {
      PermissionLevel byId = PermissionLevel.byId(Integer.parseInt(raw));
      if (byId != null) return byId;
    } catch (NumberFormatException ignored) {
      // fall through to valueOf
    }
    try {
      return PermissionLevel.valueOf(raw.toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return PermissionLevel.GAMEMASTERS;
    }
  }

  public CowConfig(Path configFile) {
    this.configFile = configFile;
    this.modStatus =
        new BoolSetting(
            "mod_status", "settings.enabled", true, "Master enable/disable for the mod");
    this.debugMode = new BoolSetting("debug_mode", "settings.debug", false, "Enable debug logging");
    this.lightningEffect =
        new BoolSetting(
            "lightning_effect",
            "settings.lightning-effect",
            true,
            "Show lightning when punishing players");
    this.teleport =
        new BoolSetting(
            "teleport", "settings.teleport-enabled", true, "Enable milk-teleport feature");
    this.cowInvincibility =
        new BoolSetting(
            "cow_invincibility", "settings.cow-invincibility", false, "Make cows invincible");
    this.bypassEnabled =
        new BoolSetting(
            "bypass_status", "settings.allow-bypass", true, "Allow ops to bypass cow protection");
    this.deathMessagesEnabled =
        new BoolSetting(
            "death_messages_enabled",
            "settings.enable-death-messages",
            true,
            "Enables custom death messages for players who harm cows");
    this.playerDamageAmount =
        new DoubleSetting(
            "player_dmg_amt",
            "settings.player-damage-amount",
            10.0,
            "Damage dealt by 'damage' punishment mode");
    this.cowHealth = new DoubleSetting("cow_health", "settings.cow-health", 20.0, "Max cow health");
    this.punishment =
        new EnumSetting<>(
            "punishment",
            "settings.punishment-mode",
            PunishmentMode.DEATH,
            "Punishment mode",
            PunishmentMode.class,
            PunishmentMode::fromString);
    this.bypassOpLevel =
        new EnumSetting<>(
            "bypass_level",
            "settings.bypass-op-level",
            PermissionLevel.GAMEMASTERS,
            "Min op level to bypass",
            PermissionLevel.class,
            CowConfig::parsePermLevel);
    this.adminOpLevel =
        new EnumSetting<>(
            "admin_level",
            "settings.admin-op-level",
            PermissionLevel.GAMEMASTERS,
            "Min op level for admin commands",
            PermissionLevel.class,
            CowConfig::parsePermLevel);
    this.scoreboardEnabled =
        new BoolSetting(
            "scoreboard_status", "scoreboard.enabled", true, "Enable scoreboard tracking");
    this.trackAssaults =
        new BoolSetting(
            "track_assaults",
            "scoreboard.track-assaults",
            true,
            "Track cow assaults on scoreboard");
    this.trackKills =
        new BoolSetting(
            "track_kills", "scoreboard.track-kills", true, "Track cow kills on scoreboard");
    this.assaultObjective =
        new StringSetting(
            "assault_objective",
            "scoreboard.assault-objective",
            "cowAssaults",
            "Scoreboard objective name for assaults");
    this.killObjective =
        new StringSetting(
            "kill_objective",
            "scoreboard.kill-objective",
            "cowKills",
            "Scoreboard objective name for kills");
    this.assaultDisplay =
        new StringSetting(
            "assault_display",
            "scoreboard.assault-display",
            "Cow Assaults",
            "Display name for assault scoreboard");
    this.killDisplay =
        new StringSetting(
            "kill_display",
            "scoreboard.kill-display",
            "Cow Kills",
            "Display name for kill scoreboard");

    this.bypassPermission =
        new StringSetting(
            "bypass_permission",
            "permissions.bypass-permission",
            "sacredcows.bypass",
            "Permission node for bypass");
    this.adminPermission =
        new StringSetting(
            "admin_permission",
            "permissions.admin-permission",
            "sacredcows.admin",
            "Permission node for admin commands");

    this.all =
        List.of(
            modStatus,
            debugMode,
            lightningEffect,
            teleport,
            cowInvincibility,
            bypassEnabled,
            deathMessagesEnabled,
            playerDamageAmount,
            cowHealth,
            punishment,
            bypassOpLevel,
            adminOpLevel,
            scoreboardEnabled,
            trackAssaults,
            trackKills,
            assaultObjective,
            killObjective,
            assaultDisplay,
            killDisplay,
            bypassPermission,
            adminPermission);

    // toUnmodifiableMap throws `IllegalStateException` if two entries have the same key
    this.byName = all.stream().collect(toUnmodifiableMap(Setting::name, Function.identity()));
  }

  public void load() {
    if (configFile == null || !Files.exists(configFile)) {
      createDefaultConfig();
      return;
    }

    try (InputStream input = Files.newInputStream(configFile)) {
      properties.load(input);
      parseProperties();
    } catch (IOException e) {
      LOGGER.error("Error loading config file: {}", e.getMessage());
      createDefaultConfig();
    }
  }

  public Properties getSavedConfig() {
    Properties properties = new Properties();
    if (configFile == null || !Files.exists(configFile)) {
      return properties;
    }

    try (InputStream input = Files.newInputStream(configFile)) {
      properties.load(input);
    } catch (IOException e) {
      LOGGER.error("Error loading config file: {}", e.getMessage());
    }
    return properties;
  }

  private void createDefaultConfig() {
    if (configFile == null) return;

    try {
      // Ensure parent directories exist
      Files.createDirectories(configFile.getParent());

      try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
        writer.write("# Sacred Cows Configuration");
        writer.newLine();
        for (Setting s : allSettings()) {
          writer.write("# " + s.comment());
          writer.newLine();
          writer.write(s.serializationKey() + "=" + s.defaultSerialized());
          writer.newLine();
        }
      }
      load();
      parseProperties();
    } catch (IOException e) {
      LOGGER.error("Error creating default config file: {}", e.getMessage());
    }
  }

  public void save() throws IOException {
    if (configFile == null) {
      return;
    }

    // Ensure parent directories exist
    Files.createDirectories(configFile.getParent());

    // Save properties
    // General Settings
    for (Setting s : allSettings()) {
      properties.setProperty(s.serializationKey(), s.serialize());
    }

    // Death Messages
    for (int i = 0; i < deathMessages.size(); i++) {
      properties.setProperty("death-messages." + i, deathMessages.get(i));
    }

    // Save to file
    try (OutputStream output = Files.newOutputStream(configFile)) {
      properties.store(output, "Sacred Cows Configuration File");
    }
  }

  private void parseProperties() {
    for (Setting s : all) {
      String raw = properties.getProperty(s.serializationKey());
      if (raw == null) continue;
      if (!s.tryDeserialize(raw)) {
        LOGGER.warn(
            "Invalid value '{}' for {} in config file, keeping default {}",
            raw,
            s.name(),
            s.defaultSerialized());
      }
      // else: leave the default that was set in the constructor
    }
    // Death messages stay special-cased
    parseDeathMessages();
  }

  private void parseDeathMessages() {
    deathMessages = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      String message = properties.getProperty("death-messages." + i);
      if (message == null || message.trim().isEmpty()) break;
      deathMessages.add(message);
    }
    if (deathMessages.isEmpty()) {
      deathMessages = new ArrayList<>(List.of("%player% was moo-rdered for their bovine crimes."));
    }
  }

  public List<String> getDeathMessages() {
    return deathMessages;
  }

  public List<String> allNames() {
    return all.stream().map(Setting::name).toList();
  }

  public List<String> boolNames() {
    return all.stream().filter(s -> s instanceof BoolSetting).map(Setting::name).toList();
  }

  public List<String> intNames() {
    return all.stream().filter(s -> s instanceof IntSetting).map(Setting::name).toList();
  }

  public List<String> doubleNames() {
    return all.stream().filter(s -> s instanceof DoubleSetting).map(Setting::name).toList();
  }

  public List<String> enumNames() {
    return all.stream().filter(s -> s instanceof EnumSetting).map(Setting::name).toList();
  }

  public Optional<Setting> find(String name) {
    return Optional.ofNullable(byName.get(name));
  }

  public List<Setting> allSettings() {
    return all;
  }

  public List<Setting> boolSettings() {
    return all.stream().filter(s -> s instanceof BoolSetting).toList();
  }
}
