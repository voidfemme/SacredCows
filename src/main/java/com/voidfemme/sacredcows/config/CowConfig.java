package com.voidfemme.sacredcows.config;

import com.voidfemme.sacredcows.features.CowProtectionFeature.PunishmentMode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(CowConfig.class.getName());
  private final Path configFile;
  private final Properties properties = new Properties();

  // Default values
  private boolean enabled = true;
  private boolean debugEnabled = false;
  private PunishmentMode punishmentMode = PunishmentMode.DEATH;
  private boolean teleportEnabled = true;
  private double playerDamageAmount = 10.0;
  private boolean lightningEffectEnabled = true;
  private boolean customDeathMessagesEnabled = true;
  private boolean cowInvincibility = false;
  private double cowHealth = 20.0;
  private boolean bypassEnabled = true;
  private int bypassOpLevel = 2;
  private int adminOpLevel = 2;

  // Scoreboard settings
  private boolean scoreboardEnabled = true;
  private boolean trackAssaultsEnabled = true;
  private boolean trackKillsEnabled = true;
  private String assaultObjective = "cowAssaults";
  private String killObjective = "cowKills";
  private String assaultDisplay = "Cow Assaults";
  private String killDisplay = "Cow Kills";

  // Permissions
  private String bypassPermission = "sacredcows.bypass";
  private String adminPermission = "sacredcows.admin";

  // Death messages
  private List<String> deathMessages =
      Arrays.asList(
          "%player% was moo-rdered for their bovine crimes",
          "%player% faced divine bovine retribution",
          "The cows fought back, and %player% lost",
          "%player% learned the hard way not to mess with cows",
          "A mysterious force struck down %player% for harming a cow");

  public CowConfig(Path configFile) {
    this.configFile = configFile;
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
      System.err.println("Error loading config file: " + e.getMessage());
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
      System.err.println("Error loading config file: " + e.getMessage());
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
        for (SettingsEnum key : SettingsEnum.values()) {
          writer.write("# " + key.getComment());
          writer.newLine();
          writer.write(key.toLongString() + "=" + key.getDefault());
          writer.newLine();
        }
        for (ScoreboardEnum key : ScoreboardEnum.values()) {
          writer.write("# " + key.getComment());
          writer.newLine();
          writer.write(key.toLongString() + "=" + key.getDefault());
          writer.newLine();
        }
        for (PermissionsEnum key : PermissionsEnum.values()) {
          writer.write("# " + key.getComment());
          writer.newLine();
          writer.write(key.toLongString() + "=" + key.getDefault());
          writer.newLine();
        }
      }
      load();
      parseProperties();
    } catch (IOException e) {
      System.err.println("Error creating default config file: " + e.getMessage());
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
    properties.setProperty(SettingsEnum.MOD_ENABLED.toLongString(), String.valueOf(enabled));
    properties.setProperty(SettingsEnum.DEBUG.toLongString(), String.valueOf(debugEnabled));
    properties.setProperty(SettingsEnum.PUNISHMENT_MODE.toLongString(), punishmentMode.toString());
    properties.setProperty(
        SettingsEnum.PLAYER_DAMAGE_AMOUNT.toLongString(), String.valueOf(playerDamageAmount));
    properties.setProperty(
        SettingsEnum.LIGHTNING_EFFECT.toLongString(), String.valueOf(lightningEffectEnabled));
    properties.setProperty(
        SettingsEnum.TELEPORT_ENABLED.toLongString(), String.valueOf(teleportEnabled));
    properties.setProperty(
        SettingsEnum.COW_INVINCIBILITY.toLongString(), String.valueOf(cowInvincibility));
    properties.setProperty(SettingsEnum.COW_HEALTH.toLongString(), String.valueOf(cowHealth));
    // properties.setProperSettingsEnum.ENUM.togs.custom-death-messages", "true");
    properties.setProperty(SettingsEnum.ALLOW_BYPASS.toLongString(), String.valueOf(bypassEnabled));
    properties.setProperty(
        SettingsEnum.BYPASS_OP_LEVEL.toLongString(), String.valueOf(bypassOpLevel));
    properties.setProperty(
        SettingsEnum.ADMIN_OP_LEVEL.toLongString(), String.valueOf(adminOpLevel));

    // Scoreboard Settings
    properties.setProperty(
        ScoreboardEnum.SCOREBOARD_ENABLED.toLongString(), String.valueOf(scoreboardEnabled));
    properties.setProperty(
        ScoreboardEnum.TRACK_ASSAULTS.toLongString(), String.valueOf(trackAssaultsEnabled));
    properties.setProperty(
        ScoreboardEnum.TRACK_KILLS.toLongString(), String.valueOf(trackKillsEnabled));
    properties.setProperty(ScoreboardEnum.ASSAULT_OBJECTIVE.toLongString(), assaultObjective);
    properties.setProperty(ScoreboardEnum.KILL_OBJECTIVE.toLongString(), killObjective);
    properties.setProperty(ScoreboardEnum.ASSAULT_DISPLAY.toLongString(), assaultDisplay);
    properties.setProperty(ScoreboardEnum.KILL_DISPLAY.toLongString(), killDisplay);

    // Permissions
    properties.setProperty(PermissionsEnum.BYPASS_PERMISSION.toLongString(), "sacredcows.bypass");
    properties.setProperty(PermissionsEnum.ADMIN_PERMISSION.toLongString(), "sacredcows.admin");

    // Death Messages
    for (int i = 0; i < deathMessages.size(); i++) {
      properties.setProperty("death-messages." + i, deathMessages.get(i));
    }

    // Save to file
    try (OutputStream output = Files.newOutputStream(configFile)) {
      properties.store(output, "Sacred Cows Configuration File");
    }
  }

  private void setDefaultProperties() {
    // General Settings
    for (SettingsEnum key : SettingsEnum.values()) {
      properties.setProperty(key.toLongString(), key.getDefault());
    }

    // Scoreboard Settings
    for (ScoreboardEnum key : ScoreboardEnum.values()) {
      properties.setProperty(key.toLongString(), key.getDefault());
    }

    // Permissions
    for (PermissionsEnum key : PermissionsEnum.values()) {
      properties.setProperty(key.toLongString(), key.getDefault());
    }

    // Death Messages
    for (int i = 0; i < deathMessages.size(); i++) {
      properties.setProperty("death-messages." + i, deathMessages.get(i));
    }
  }

  private String get(CowConfigKeys key) {
    return properties.getProperty(key.toLongString(), key.getDefault());
  }

  private boolean getBool(CowConfigKeys key) {
    return Boolean.parseBoolean(get(key));
  }

  private int getInt(CowConfigKeys key) {
    try {
      return Integer.parseInt(get(key));
    } catch (NumberFormatException e) {
      LOGGER.warn(
          "Invalid integer for '{}', defaulting to {}", key.toLongString(), key.getDefault());
      return Integer.parseInt(key.getDefault());
    }
  }

  private double getDouble(CowConfigKeys key) {
    try {
      return Double.parseDouble(get(key));
    } catch (NumberFormatException e) {
      LOGGER.warn(
          "Invalid double for '{}', defaulting to {}", key.toLongString(), key.getDefault());
      return Double.parseDouble(key.getDefault());
    }
  }

  private void parseProperties() {
    enabled = getBool(SettingsEnum.MOD_ENABLED);
    debugEnabled = getBool(SettingsEnum.DEBUG);
    playerDamageAmount = getDouble(SettingsEnum.PLAYER_DAMAGE_AMOUNT);
    bypassOpLevel = getInt(SettingsEnum.BYPASS_OP_LEVEL);
    adminOpLevel = getInt(SettingsEnum.ADMIN_OP_LEVEL);
    lightningEffectEnabled = getBool(SettingsEnum.LIGHTNING_EFFECT);
    teleportEnabled = getBool(SettingsEnum.TELEPORT_ENABLED);
    cowInvincibility = getBool(SettingsEnum.COW_INVINCIBILITY);
    cowHealth = getDouble(SettingsEnum.COW_HEALTH);
    bypassEnabled = getBool(SettingsEnum.ALLOW_BYPASS);

    try {
      punishmentMode = PunishmentMode.fromString(get(SettingsEnum.PUNISHMENT_MODE));
    } catch (IllegalArgumentException e) {
      LOGGER.warn(
          "Invalid punishment mode '{}', defaulting to {}",
          e.getMessage(),
          SettingsEnum.PUNISHMENT_MODE.getDefault());
      punishmentMode = PunishmentMode.fromString(SettingsEnum.PUNISHMENT_MODE.getDefault());
    }

    scoreboardEnabled = getBool(ScoreboardEnum.SCOREBOARD_ENABLED);
    trackAssaultsEnabled = getBool(ScoreboardEnum.TRACK_ASSAULTS);
    trackKillsEnabled = getBool(ScoreboardEnum.TRACK_KILLS);
    assaultObjective = get(ScoreboardEnum.ASSAULT_OBJECTIVE);
    killObjective = get(ScoreboardEnum.KILL_OBJECTIVE);
    assaultDisplay = get(ScoreboardEnum.ASSAULT_DISPLAY);
    killDisplay = get(ScoreboardEnum.KILL_DISPLAY);

    bypassPermission = get(PermissionsEnum.BYPASS_PERMISSION);
    adminPermission = get(PermissionsEnum.ADMIN_PERMISSION);

    // Death messages (structurally different, handled separately)
    deathMessages = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      String message = properties.getProperty("death-messages." + i);
      if (message != null && !message.trim().isEmpty()) {
        deathMessages.add(message);
      } else break;
    }

    if (deathMessages.isEmpty()) {
      deathMessages = Arrays.asList("%player% was moo-rdered for their bovine crimes.");
    }
  }

  // Getters
  public boolean isEnabled() {
    return enabled;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public PunishmentMode getPunishmentMode() {
    return punishmentMode;
  }

  public boolean isLightningEffectEnabled() {
    return lightningEffectEnabled;
  }

  public boolean isTeleportEnabled() {
    return teleportEnabled;
  }

  public boolean isCowInvincibilityEnabled() {
    return cowInvincibility;
  }

  public double getCowHealth() {
    return cowHealth;
  }

  public double getPlayerDamageAmount() {
    return playerDamageAmount;
  }

  public boolean isCustomDeathMessagesEnabled() {
    return customDeathMessagesEnabled;
  }

  public boolean isScoreboardEnabled() {
    return scoreboardEnabled;
  }

  public boolean isTrackAssaultsEnabled() {
    return trackAssaultsEnabled;
  }

  public boolean isTrackKillsEnabled() {
    return trackKillsEnabled;
  }

  public boolean isAllowBypass() {
    return bypassEnabled;
  }

  public int getBypassOpLevel() {
    return bypassOpLevel;
  }

  public int getAdminOpLevel() {
    return adminOpLevel;
  }

  public String getAssaultObjective() {
    return assaultObjective;
  }

  public String getKillObjective() {
    return killObjective;
  }

  public String getAssaultDisplay() {
    return assaultDisplay;
  }

  public String getKillDisplay() {
    return killDisplay;
  }

  public String getBypassPermission() {
    return bypassPermission;
  }

  public String getAdminPermission() {
    return adminPermission;
  }

  public List<String> getDeathMessages() {
    return new ArrayList<>(deathMessages);
  }

  // == SETTERS ==
  public void setModEnabled(boolean enabled, boolean apply) {
    this.enabled = enabled;
    if (apply) {
      properties.setProperty(SettingsEnum.MOD_ENABLED.toLongString(), String.valueOf(enabled));
    }
  }

  public void setModEnabled(boolean enabled) {
    setModEnabled(enabled, false);
  }

  public void setDebugEnabled(boolean enabled, boolean apply) {
    this.debugEnabled = enabled;
    if (apply) {
      properties.setProperty(SettingsEnum.DEBUG.toLongString(), String.valueOf(enabled));
    }
  }

  public void setDebugEnabled(boolean enabled) {
    setDebugEnabled(enabled, false);
  }

  public void setPunishmentMode(PunishmentMode mode, boolean apply) {
    this.punishmentMode = mode;
    if (apply) {
      properties.setProperty(SettingsEnum.PUNISHMENT_MODE.toLongString(), mode.toString());
    }
  }

  public void setPunishmentMode(PunishmentMode mode) {
    setPunishmentMode(mode, false);
  }

  public void setPlayerDamageAmount(double amount, boolean apply) {
    this.playerDamageAmount = amount;
    if (apply) {
      properties.setProperty(
          SettingsEnum.PLAYER_DAMAGE_AMOUNT.toLongString(), String.valueOf(amount));
    }
  }

  public void setPlayerDamageAmount(double amount) {
    setPlayerDamageAmount(amount, false);
  }

  public void setLightningModeEnabled(boolean enabled, boolean apply) {
    this.lightningEffectEnabled = enabled;
    if (apply) {
      properties.setProperty(SettingsEnum.LIGHTNING_EFFECT.toLongString(), String.valueOf(enabled));
    }
  }

  public void setLightningModeEnabled(boolean enabled) {
    setLightningModeEnabled(enabled, false);
  }

  public void setTeleportEnabled(boolean enabled, boolean apply) {
    this.teleportEnabled = enabled;
    if (apply) {
      properties.setProperty(SettingsEnum.TELEPORT_ENABLED.toLongString(), String.valueOf(enabled));
    }
  }

  public void setTeleportEnabled(boolean enabled) {
    setTeleportEnabled(enabled, false);
  }

  public void setCowInvincibilityEnabled(boolean enabled, boolean apply) {
    this.cowInvincibility = enabled;
    if (apply) {
      properties.setProperty(
          SettingsEnum.COW_INVINCIBILITY.toLongString(), String.valueOf(enabled));
    }
  }

  public void setCowInvincibilityEnabled(boolean enabled) {
    setCowInvincibilityEnabled(enabled, false);
  }

  public void setCowHealth(double max_health, boolean apply) {
    this.cowHealth = max_health;
    if (apply) {
      properties.setProperty(SettingsEnum.COW_HEALTH.toLongString(), String.valueOf(max_health));
    }
  }

  public void setCowHealth(double max_health) {
    setCowHealth(max_health, false);
  }

  public void setBypassEnabled(boolean enabled, boolean apply) {
    this.bypassEnabled = enabled;
    if (apply) {
      properties.setProperty(
          SettingsEnum.ALLOW_BYPASS.toLongString(), String.valueOf(bypassEnabled));
    }
  }

  public void setBypassEnabled(boolean enabled) {
    setBypassEnabled(enabled, false);
  }

  public void setBypassOpLevel(int level, boolean apply) {
    this.bypassOpLevel = level;
    if (apply) {
      properties.setProperty(SettingsEnum.BYPASS_OP_LEVEL.toLongString(), String.valueOf(level));
    }
  }

  public void setBypassOpLevel(int level) {
    setBypassOpLevel(level, false);
  }

  public void setAdminOpLevel(int level, boolean apply) {
    this.adminOpLevel = level;
    if (apply) {
      properties.setProperty(SettingsEnum.ADMIN_OP_LEVEL.toLongString(), String.valueOf(level));
    }
  }

  public void setAdminOpLevel(int level) {
    setAdminOpLevel(level, false);
  }

  public void setScoreboardEnabled(boolean enabled, boolean apply) {
    this.scoreboardEnabled = enabled;
    if (apply) {
      properties.setProperty(
          ScoreboardEnum.SCOREBOARD_ENABLED.toLongString(), String.valueOf(enabled));
    }
  }

  public void setScoreboardEnabled(boolean enabled) {
    setScoreboardEnabled(enabled, false);
  }

  public void setTrackAssaultsEnabled(boolean enabled, boolean apply) {
    this.trackAssaultsEnabled = enabled;
    if (apply) {
      properties.setProperty(ScoreboardEnum.TRACK_ASSAULTS.toLongString(), String.valueOf(enabled));
    }
  }

  public void setTrackAssaultsEnabled(boolean enabled) {
    setTrackAssaultsEnabled(enabled, false);
  }

  public void setTrackKillsEnabled(boolean enabled, boolean apply) {
    this.trackKillsEnabled = enabled;
    if (apply) {
      properties.setProperty(ScoreboardEnum.TRACK_KILLS.toLongString(), String.valueOf(enabled));
    }
  }

  public void setTrackKillsEnabled(boolean enabled) {
    setTrackKillsEnabled(enabled, false);
  }

  public void setAssaultObjective(String objective, boolean apply) {
    this.assaultObjective = objective;
    if (apply) {
      properties.setProperty(
          ScoreboardEnum.ASSAULT_OBJECTIVE.toLongString(), String.valueOf(objective));
    }
  }

  public void setAssaultObjective(String objective) {
    setAssaultObjective(objective, false);
  }

  public void setKillObjective(String objective, boolean apply) {
    this.killObjective = objective;
    if (apply) {
      properties.setProperty(ScoreboardEnum.KILL_OBJECTIVE.toLongString(), objective);
    }
  }

  public void setKillObjective(String objective) {
    setKillObjective(objective, false);
  }

  public void setAssaultDisplay(String display, boolean apply) {
    this.assaultDisplay = display;
    if (apply) {
      properties.setProperty(ScoreboardEnum.ASSAULT_DISPLAY.toLongString(), display);
    }
  }

  public void setAssaultDisplay(String display) {
    setAssaultDisplay(display, false);
  }

  public void setKillDisplay(String display, boolean apply) {
    this.killDisplay = display;
    if (apply) {
      properties.setProperty(ScoreboardEnum.KILL_DISPLAY.toLongString(), display);
    }
  }

  public void setKillDisplay(String display) {
    setKillDisplay(display, false);
  }

  public void setBypassPermission(String permission, boolean apply) {
    this.bypassPermission = permission;
    if (apply) {
      properties.setProperty(PermissionsEnum.BYPASS_PERMISSION.toLongString(), permission);
    }
  }

  public void setBypassPermission(String permission) {
    setBypassPermission(permission, false);
  }

  public void setAdminPermission(String permission, boolean apply) {
    this.adminPermission = permission;
    if (apply) {
      properties.setProperty(PermissionsEnum.ADMIN_PERMISSION.toLongString(), permission);
    }
  }

  public void setAdminPermission(String permission) {
    setAdminPermission(permission, false);
  }
}
