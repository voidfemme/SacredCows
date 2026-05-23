package com.voidfemme.sacredcows.mixins;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.components.CowComponents;
import com.voidfemme.sacredcows.config.CowConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCow.class)
public class CowMilkMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger(SacredCows.class.getName());

  // The cow is `this` - since the mixin is injecting into `Cow`, I can cast `this` to `Cow`
  //  if I need to call methods on it:
  //      `Cow cow = (Cow)(Object)this`
  //      [[ note: The `(Object)` in the middle is a mixin quirk -- Java doesn't know at compile
  //      time that `this` (which looks like your mixin class) is actually a `Cow` at runtime,
  //      so you cast through `Object` to satisfy the compiler. ]]

  @Inject(method = "mobInteract", at = @At("RETURN"))
  private void onMilked(
      Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
    CowConfig config = SacredCows.getInstance().getConfig();

    // cir.getReturnValue() lets you check if milking actually succeeded
    if (cir.getReturnValue() != InteractionResult.SUCCESS) return;

    if (player.level().isClientSide()) return;

    if (config.teleport.get()) {
      Cow cow = (Cow) (Object) this;
      ItemStack stack = player.getItemInHand(hand);

      // If it's a named cow, add the cow's Id to the bucket, and give the bucket a glint
      if (cow.hasCustomName()) {
        // If we have a stack of empty milk buckets, we need to name the new milk bucket
        if (stack.count() > 1) {
          // Iterate through the inventory
          for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(Items.MILK_BUCKET) && slot.get(CowComponents.COW_ID) == null) {
              // This is the untagged milk bucket
              // Give the bucket a unique name
              slot.set(DataComponents.CUSTOM_NAME, cow.getCustomName());
              // Save the Cow's UUID to the milk bucket
              slot.set(CowComponents.COW_ID, cow.getUUID());
              break;
            }
          }
        } else {
          // Just in case the single bucket case breaks:
          // Give the bucket a unique name
          stack.set(DataComponents.CUSTOM_NAME, cow.getCustomName());

          // Save the Cow's UUID to the milk bucket
          stack.set(CowComponents.COW_ID, cow.getUUID());
        }
      }
    }
  }
}
