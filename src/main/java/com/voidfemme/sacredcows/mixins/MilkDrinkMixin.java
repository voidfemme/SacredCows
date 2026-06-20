package com.voidfemme.sacredcows.mixins;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class MilkDrinkMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger(MilkDrinkMixin.class.getName());

  // Must match the key written in CowMilkMixin.
  private static final String COW_ID_KEY = "sacredcows:cow_id";

  @Inject(method = "finishUsingItem", at = @At("HEAD"))
  private void onFinishUsing(
      ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
    CowConfig config = SacredCows.getInstance().getConfig();
    if (!stack.is(Items.MILK_BUCKET)) return;
    // Don't let the client handle teleporting
    if (level.isClientSide()) return;

    // teleport code here
    if (config.teleport.get()) {
      // Get player data
      if (!(entity instanceof ServerPlayer player)) return;

      // Read uuid from milk bucket (stored in minecraft:custom_data) and find the cow.
      UUID cowUuid = readCowId(stack);
      if (cowUuid == null) return;

      MinecraftServer server = ((ServerLevel) level).getServer();
      ServerLevel cowLevel = null;
      Entity cow = null;
      for (ServerLevel playerLevel : server.getAllLevels()) {
        Entity found = playerLevel.getEntity(cowUuid);
        if (found != null) {
          cow = found;
          cowLevel = playerLevel;
          break;
        }
      }

      if (cow == null) return;

      TeleportTransition transition =
          new TeleportTransition(
              cowLevel,
              cow.position(),
              Vec3.ZERO,
              player.getYRot(),
              player.getXRot(),
              TeleportTransition.DO_NOTHING);
      player.setDeltaMovement(Vec3.ZERO);
      player.teleport(transition);
    }
  }

  // Returns the stored cow UUID, or null if this bucket isn't tagged / the value is malformed.
  private static UUID readCowId(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) return null;

    Optional<String> idStr = data.copyTag().getString(COW_ID_KEY);
    if (idStr.isEmpty()) return null;

    try {
      return UUID.fromString(idStr.get());
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Milk bucket carried an unparseable {}: {}", COW_ID_KEY, idStr.get());
      return null;
    }
  }
}
