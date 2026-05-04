// package com.voidfemme.sacredcows.features;
//
// import com.voidfemme.sacredcows.SacredCows;
// import com.voidfemme.sacredcows.config.CowConfig;
// import net.minecraft.server.level.ServerLevel;
// import net.minecraft.world.InteractionHand;
// import net.minecraft.world.entity.animal.cow.Cow;
// import net.minecraft.world.entity.player.Player;
// import net.minecraft.world.item.ItemStack;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// class MilkTeleportFeature {
//   public static final String MOD_ID = "sacredcows.features.MilkTeleportFeature";
//   private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
//
//   private final SacredCows owner;
//   private final CowConfig config;
//
//   public MilkTeleportFeature(SacredCows owner, CowConfig config) {
//     this.owner = owner;
//     this.config = config;
//   }
//
//   public ItemStack getStack(Player player, InteractionHand hand) {
//     ServerLevel serverLevel = (ServerLevel) player.level();
//     Cow cow = (Cow) (Object) this;
//     ItemStack stack = player.getItemInHand(hand);
//     return stack;
//   }
// }
