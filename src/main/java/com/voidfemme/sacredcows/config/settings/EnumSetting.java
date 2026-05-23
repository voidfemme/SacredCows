package com.voidfemme.sacredcows.config.settings;

import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnumSetting<E extends Enum<E>> implements Setting {
  private static final Logger LOGGER = LoggerFactory.getLogger(StringSetting.class.getName());

  private final String name;
  private final String serializationKey;
  private final E defaultValue;
  private final String comment;
  private final Class<E> type;
  private final Function<String, E> parser; // for tolerant parsing (handles aliases)
  private E value;

  public EnumSetting(
      String name,
      String serializationKey,
      E defaultValue,
      String comment,
      Class<E> type,
      Function<String, E> parser) {
    this.name = name;
    this.serializationKey = serializationKey;
    this.defaultValue = defaultValue;
    this.comment = comment;
    this.type = type;
    this.parser = parser;
    this.value = defaultValue;
  }

  // ** Convenience constructor for the common case (strict Enum.valueOf). */
  public EnumSetting(
      String name, String serializationKey, E defaultValue, String comment, Class<E> type) {
    this(
        name,
        serializationKey,
        defaultValue,
        comment,
        type,
        raw -> Enum.valueOf(type, raw.toUpperCase()));
  }

  public E get() {
    return value;
  }

  public void set(E v) {
    this.value = v;
  }

  public Class<E> type() {
    return type;
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
    return value.toString();
  }

  @Override
  public boolean tryDeserialize(String raw) {
    E parsed;
    try {
      parsed = parser.apply(raw);
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Invalid {} for '{}' (got '{}').", type.getSimpleName(), serializationKey, raw);
      return false;
    }
    this.value = parsed;
    return true;
  }

  @Override
  public String defaultSerialized() {
    return defaultValue.toString();
  }

  @Override
  public MutableComponent status() {
    return Component.literal(value.toString()).withStyle(ChatFormatting.YELLOW);
  }
}
