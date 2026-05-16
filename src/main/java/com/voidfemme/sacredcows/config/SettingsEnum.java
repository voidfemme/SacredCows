package com.voidfemme.sacredcows.config;

import com.voidfemme.sacredcows.features.CowProtectionFeature.PunishmentMode;

public enum SettingsEnum implements CowConfigKeys {
  MOD_ENABLED("true", "Whether the mod is enabled"),
  DEBUG("false", "Enable debug logging"),
  PUNISHMENT_MODE(
      PunishmentMode.DEATH.toString(), "How to punish cow harm: 'death', 'damage', or 'lightning'"),
  PLAYER_DAMAGE_AMOUNT("10.0", "Amount to damage player when 'punishment-mode' is 'damage'"),
  LIGHTNING_EFFECT("true", "Whether the lightning effect is enabled"),
  TELEPORT_ENABLED("true", "Whether drinking a named cow's milk will teleport the player"),
  COW_INVINCIBILITY("false", "Whether cows are invincible"),
  COW_HEALTH("20.0", "How much health cows have"),
  ALLOW_BYPASS(
      "false",
      "Whether OP's can bypass punishment, allowing them to kill cows with no repercussions"),
  BYPASS_OP_LEVEL("2", "Integer: What auth level needed to bypass punishment"),
  ADMIN_OP_LEVEL("2", "Integer: What level is considered 'Admin'");

  private final String defaultValue;
  private final String comment;

  SettingsEnum(String defaultValue, String comment) {
    this.defaultValue = defaultValue;
    this.comment = comment;
  }

  public String getComment() {
    return comment;
  }

  public String getDefault() {
    return defaultValue;
  }

  public String toLongString() {
    return "settings." + this.name().toLowerCase().replace("_", "-");
  }

  public String toShortString() {
    return this.name().toLowerCase().replace("_", "-");
  }
}
