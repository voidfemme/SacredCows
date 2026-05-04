package com.voidfemme.sacredcows.mixins;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.components.CowComponents;
import com.voidfemme.sacredcows.config.CowConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCow.class)
public class CowMilkMixin {

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

    if (config.isTeleportEnabled()) {
      ServerLevel serverLevel = (ServerLevel) player.level();
      // Code here: player and hand are available
      Cow cow = (Cow) (Object) this;
      ItemStack stack = player.getItemInHand(hand);

      // If it's a named cow, add the cow's Id to the bucket, and give the bucket a glint
      if (cow.hasCustomName()) {

        // Create a glint on the object
        Holder<Enchantment> enchantment =
            serverLevel
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING);
        stack.enchant(enchantment, 0);

        // Hide the tooltip so the player doesn't see the random enchantment name.
        stack.set(
            DataComponents.TOOLTIP_DISPLAY,
            TooltipDisplay.DEFAULT.withHidden(DataComponents.ENCHANTMENTS, true));

        // Save the Cow's UUID to the milk bucket
        stack.set(CowComponents.COW_ID, cow.getUUID());
      }
    }
  }
}
