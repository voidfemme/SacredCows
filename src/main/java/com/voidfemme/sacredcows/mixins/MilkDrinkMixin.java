package com.voidfemme.sacredcows.mixins;

import com.voidfemme.sacredcows.components.CowComponents;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class MilkDrinkMixin {

  @Inject(method = "finishUsingItem", at = @At("HEAD"))
  private void onFinishUsing(
      ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
    if (!stack.is(Items.MILK_BUCKET)) return;
    // Don't let the client handle teleporting
    if (level.isClientSide()) return;

    // teleport code here
    // Get player data
    if (!(entity instanceof ServerPlayer player)) return;

    // Read uuid from milk bucket and find the cow.
    UUID cowUuid = stack.get(CowComponents.COW_ID);
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

    // TODO: Maybe add permission check here?

    TeleportTransition transition =
        new TeleportTransition(
            cowLevel,
            cow.position(),
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot(),
            TeleportTransition.DO_NOTHING);
    player.teleport(transition);
  }
}
