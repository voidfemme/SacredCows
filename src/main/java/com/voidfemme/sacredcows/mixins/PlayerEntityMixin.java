package com.voidfemme.sacredcows.mixin;

import com.voidfemme.sacredcows.SacredCows;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        SacredCows sacredCows = SacredCows.getInstance();

        if (sacredCows != null && sacredCows.getConfig().isCustomDeathMessagesEnabled()) {
            // Check if there's a pending punishment for this player
            String customMessage = sacredCows.getPendingDeathMessage(player.getUuid());
            if (customMessage != null) {
                // Send custom death message to all players
                player.getServer().getPlayerManager().broadcast(
                        Text.literal(customMessage), false);
            }
        }
    }
}
