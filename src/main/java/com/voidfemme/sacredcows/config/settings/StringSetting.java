package com.voidfemme.sacredcows.config.settings;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StringSetting implements Setting {
  private static final Logger LOGGER = LoggerFactory.getLogger(StringSetting.class.getName());

  private final String name;
  private final String serializationKey;
  private final String defaultValue;
  private final String comment;
  private String value;

  public StringSetting(String name, String serializationKey, String defaultValue, String comment) {
    this.name = name;
    this.serializationKey = serializationKey;
    this.defaultValue = defaultValue;
    this.comment = comment;
    this.value = defaultValue;
  }

  public String get() {
    return value;
  }

  public void set(String value) {
    this.value = value;
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
    this.value = raw;
    return true;
  }

  @Override
  public String defaultSerialized() {
    return defaultValue;
  }

  @Override
  public MutableComponent status() {
    return Component.literal(value).withStyle(ChatFormatting.YELLOW);
  }
}
