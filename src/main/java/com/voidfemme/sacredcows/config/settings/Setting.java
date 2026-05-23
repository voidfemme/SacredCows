package com.voidfemme.sacredcows.config.settings;

import net.minecraft.network.chat.MutableComponent;

public sealed interface Setting
    permits BoolSetting, IntSetting, DoubleSetting, StringSetting, EnumSetting {
  /** Command-facing name, e.g. "mod_status" */
  String name();

  /** File-facing Properties key, e.g. "settings.enabled" */
  String serializationKey();

  /** Comment written above the entry in the default configuration file */
  String comment();

  /** Current value rendered as String for the .properties file */
  String serialize();

  /** Parse a String from the config file that returns false on failure. */
  boolean tryDeserialize(String raw);

  /** The default value, also rendered as String for createDefaultConfig() */
  String defaultSerialized();

  /** Current value rendered as a styled Component for display in chat. */
  MutableComponent status();
}
