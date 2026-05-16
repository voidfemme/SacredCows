package com.voidfemme.sacredcows.config;

public enum ScoreboardEnum implements CowConfigKeys {
  SCOREBOARD_ENABLED("true", "Whether the scoreboard is enabled"),
  TRACK_ASSAULTS("true", "Whether to track assaults"),
  TRACK_KILLS("true", "Whether to track kills"),
  ASSAULT_OBJECTIVE("cowAssaults", "String: what to call the assault objective"),
  KILL_OBJECTIVE("cowKills", "String: what to call the kill objective"),
  ASSAULT_DISPLAY(
      "Cow Assaults", "String: what to call the assault display string in the stats command"),
  KILL_DISPLAY("Cow Kills", "String: what to call the kill display string in the stats command");

  private final String defaultValue;
  private final String comment;

  ScoreboardEnum(String defaultValue, String comment) {
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
    return "scoreboard." + this.name().toLowerCase().replace("_", "-");
  }

  public String toShortString() {
    return this.name().toLowerCase().replace("_", "-");
  }
}
