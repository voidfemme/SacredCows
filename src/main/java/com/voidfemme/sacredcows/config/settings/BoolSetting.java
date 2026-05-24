package com.voidfemme.sacredcows.config.settings;

import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BoolSetting implements Setting {
  private static final Logger LOGGER = LoggerFactory.getLogger(BoolSetting.class.getName());

  private final String name;
  private final String serializationKey;
  private final boolean defaultValue;
  private final String comment;
  private boolean value;

  public BoolSetting(String name, String serializationKey, boolean defaultValue, String comment) {
    this.name = name;
    this.serializationKey = serializationKey;
    this.defaultValue = defaultValue;
    this.comment = comment;
    this.value = defaultValue;
  }

  public void set(boolean v) {
    this.value = v;
  }

  public boolean get() {
    return value;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String displayName() {
    return Arrays.stream(name.split("_"))
        .filter(w -> !w.isEmpty())
        .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
        .collect(Collectors.joining(" "));
  }

  @Override
  public String serializationKey() {
    return serializationKey;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public String serialize() {
    return String.valueOf(value);
  }

  @Override
  public boolean tryDeserialize(String raw) {
    if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
      this.value = Boolean.parseBoolean(raw);
      return true;
    }
    LOGGER.warn("Could not deserialize '{}' for setting {}.", raw, this.name);
    return false;
  }

  @Override
  public String defaultSerialized() {
    return String.valueOf(defaultValue);
  }

  @Override
  public void resetToDefault() {
    this.value = defaultValue;
  }

  public MutableComponent status() {
    if (value) {
      return Component.literal("enabled").withStyle(ChatFormatting.GREEN);
    } else {
      return Component.literal("disabled").withStyle(ChatFormatting.RED);
    }
  }
}
