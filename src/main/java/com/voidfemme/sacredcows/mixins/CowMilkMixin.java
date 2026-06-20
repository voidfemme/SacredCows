package com.voidfemme.sacredcows.mixins;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCow.class)
public class CowMilkMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger(SacredCows.class.getName());

  // Namespaced key stored inside the vanilla minecraft:custom_data component.
  // We no longer register our own DataComponentType, so nothing lands in a
  // synced registry and vanilla clients are no longer kicked on join.
  private static final String COW_ID_KEY = "sacredcows:cow_id";

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
            if (slot.is(Items.MILK_BUCKET) && !hasCowId(slot)) {
              // This is the untagged milk bucket
              // Give the bucket a unique name
              slot.set(DataComponents.CUSTOM_NAME, cow.getCustomName());
              // Save the Cow's UUID to the milk bucket
              setCowId(slot, cow);
              break;
            }
          }
        } else {
          // Just in case the single bucket case breaks:
          // Give the bucket a unique name
          stack.set(DataComponents.CUSTOM_NAME, cow.getCustomName());

          // Save the Cow's UUID to the milk bucket
          setCowId(stack, cow);
        }
      }
    }
  }

  // Writes the cow's UUID (as a String) into the bucket's minecraft:custom_data,
  // creating the component if it isn't present yet.
  private static void setCowId(ItemStack stack, Cow cow) {
    CustomData.update(
        DataComponents.CUSTOM_DATA,
        stack,
        tag -> tag.putString(COW_ID_KEY, cow.getUUID().toString()));
  }

  // True if this stack already carries our cow id in custom_data.
  private static boolean hasCowId(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);

    if (data == null) return false;
    return data.copyTag().getString(COW_ID_KEY).isPresent();
  }
}
