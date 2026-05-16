package com.voidfemme.sacredcows.config;

public enum PermissionsEnum implements CowConfigKeys {
  BYPASS_PERMISSION("sacredcows.bypass", ""),
  ADMIN_PERMISSION("sacredcows.admin", "");

  private final String defaultValue;
  private final String comment;

  PermissionsEnum(String defaultValue, String comment) {
    this.defaultValue = defaultValue;
    this.comment = comment;
  }

  public String getComment() {
    return comment;
  }

  public String getDefault() {
    return defaultValue; // for single-value keys
  }

  public String toLongString() {
    return "permissions." + this.name().toLowerCase().replace("_", "-");
  }

  public String toShortString() {
    return this.name().toLowerCase().replace("_", "-");
  }
}
