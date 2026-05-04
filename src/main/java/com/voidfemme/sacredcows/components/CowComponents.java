package com.voidfemme.sacredcows.components;

import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class CowComponents {
  public static final DataComponentType<UUID> COW_ID =
      Registry.register(
          BuiltInRegistries.DATA_COMPONENT_TYPE,
          Identifier.fromNamespaceAndPath("sacredcows", "cow_id"),
          DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).build());

  public static void initialize() {}
}
