package com.voidfemme.sacredcows.config.settings;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IntSetting implements Setting {
  private static final Logger LOGGER = LoggerFactory.getLogger(IntSetting.class.getName());

  private final String name;
  private final String serializationKey;
  private final int defaultValue;
  private final String comment;
  private int value;

  public IntSetting(String name, String serializationKey, int defaultValue, String comment) {
    this.name = name;
    this.serializationKey = serializationKey;
    this.defaultValue = defaultValue;
    this.comment = comment;
    this.value = defaultValue;
  }

  public int get() {
    return value;
  }

  public void set(int v) {
    this.value = v;
  }

  @Override
  public String name() {
    return name;
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
    int parsed;
    try {
      parsed = Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      LOGGER.warn("Could not deserialize '{}' for setting {}.", raw, this.name);
      return false; // swallow, signal via return
    }
    this.value = parsed;
    return true;
  }

  @Override
  public String defaultSerialized() {
    return String.valueOf(defaultValue);
  }

  @Override
  public MutableComponent status() {
    return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
  }
}
