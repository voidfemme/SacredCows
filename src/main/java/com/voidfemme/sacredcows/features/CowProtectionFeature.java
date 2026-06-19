package com.voidfemme.sacredcows.features;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import com.voidfemme.sacredcows.util.TickCounter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowProtectionFeature {
  public static final String MOD_ID = "sacredcows.features.CowProtectionFeature";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CowProtectionFeature.class.getName());
  private static final long PUNISHMENT_EXPIRY_TICKS = 200; // 10 seconds
  private final Map<UUID, PendingPunishment> pendingPunishments = new ConcurrentHashMap<>();
  private final SacredCows owner;
  private final CowConfig config;
  private final TickCounter tickCounter;
  private final Random random = new Random();

  public CowProtectionFeature(SacredCows owner, TickCounter tickCounter, CowConfig config) {
    this.owner = owner;
    this.config = config;
    this.tickCounter = tickCounter;
  }

  public enum PunishmentMode {
    DEATH,
    DAMAGE,
    LIGHTNING_ONLY;

    public String toString() {
      return this.name().toLowerCase();
    }

    public static PunishmentMode fromString(String s) {
      return switch (s.toLowerCase()) {
        case "death" -> DEATH;
        case "damage" -> DAMAGE;
        case "lightning_only", "lightning-only" -> LIGHTNING_ONLY;
        default -> throw new IllegalArgumentException("Unknown punishment: " + s);
      };
    }
  }

  private class PendingPunishment {
    final String deathMessage;
    final long creationTick;

    PendingPunishment(String deathMessage) {
      this.deathMessage = deathMessage;
      this.creationTick = tickCounter.getServerTickCounter();
    }
  }

  public void cleanupExpiredPunishments() {
    long currentTick = tickCounter.getServerTickCounter();

    pendingPunishments
        .entrySet()
        .removeIf(
            entry -> {
              boolean expired =
                  (currentTick - entry.getValue().creationTick) > PUNISHMENT_EXPIRY_TICKS;
              if (expired && config.debugMode.get()) {
                LOGGER.info("Cleaned up expired punishment for player UUID: {}", entry.getKey());
              }
              return expired;
            });
  }

  public void registerEventHandlers() {
    tickCounter.registerIntervalCallback(
        TickCounter.CLEANUP_INTERVAL_TICKS, this::cleanupExpiredPunishments);
    // Handle entity damage
    ServerLivingEntityEvents.ALLOW_DAMAGE.register(
        (entity, source, amount) -> {
          if (!config.modStatus.get()) return true;

          // Check if entity is a cow
          if (!(entity instanceof Cow)) return true;

          // Check if damage source is a player
          ServerPlayer player = getPlayerFromDamageSource(source);
          if (player == null) return true;

          // Get the permission level of the player
          if (player.permissions() instanceof LevelBasedPermissionSet leveled) {
            PermissionLevel playerlevel = leveled.level();
            PermissionLevel requiredLevel = config.bypassOpLevel.get();

            // Now what can you do with level?
            // Check if player has bypass permission (configurable OP level)
            if (config.bypassEnabled.get() && playerlevel.isEqualOrHigherThan(requiredLevel)) {
              // Player has sufficient permissions
              if (config.debugMode.get()) {
                LOGGER.info(
                    "Player {} bypassed cow protection (OP level {} >= required {})",
                    player.getName().getString(),
                    playerlevel,
                    config.bypassOpLevel.get());
              }
              return true;
            }
            if (config.debugMode.get()) {
              LOGGER.info(
                  "Player {} attacked a cow, applying punishment (OP level: {})",
                  player.getName().getString(),
                  playerlevel);
            }
          }

          // Track assault
          owner.getScoreboard().trackAssault(player, config);

          // Apply punishment
          applyPunishment(player);

          // Cancel damage?
          return !config.cowInvincibility.get();
        });

    // Handle entity death for kill tracking
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, source) -> {
          if (!config.modStatus.get()) return;
          if (!(entity instanceof Cow)) return;
          if (!config.trackKills.get()) return;

          ServerPlayer player = getPlayerFromDamageSource(source);
          if (player != null) {
            owner.getScoreboard().trackKill(player, config);
          }
        });

    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, source) -> {
          if (!config.modStatus.get()) return;
          if (!(entity instanceof ServerPlayer player)) return;
          if (!owner.getConfig().deathMessagesEnabled.get()) return;

          String customMessage = getPendingDeathMessage(player.getUUID());
          if (customMessage != null) {
            owner
                .getServer()
                .getPlayerList()
                .broadcastSystemMessage(Component.literal(customMessage), false);
          }
        });
  }

  private void applyPunishment(ServerPlayer player) {
    PunishmentMode punishmentMode = config.punishment.get();
    ServerLevel world = player.level();

    // Lightning effect
    if (config.lightningEffect.get()) {
      try {
        Vec3 pos = new Vec3(player.getX(), player.getY(), player.getZ());
        LightningBolt lightning = new LightningBolt(EntityTypes.LIGHTNING_BOLT, world);
        lightning.setPos(pos);
        // Make it cosmetic only - otherwise the cow dies too and that's not good praxis
        lightning.setVisualOnly(true);
        world.addFreshEntity(lightning);
      } catch (Exception e) {
        LOGGER.warn("Failed to create lightning effect: {}", e.getMessage());
      }
    }

    // Prepare death message if needed
    if (config.deathMessagesEnabled.get()) {
      String deathMessage = getRandomDeathMessage(player.getName().getString());
      pendingPunishments.put(player.getUUID(), new PendingPunishment(deathMessage));
    }

    // Apply punishment
    switch (punishmentMode) {
      case PunishmentMode.DEATH:
        try {
          player.hurtServer(world, player.damageSources().generic(), Float.MAX_VALUE);
          if (config.debugMode.get()) {
            LOGGER.info("Applied death punishment to {}", player.getName().getString());
          }
        } catch (Exception e) {
          LOGGER.warn("Failed to kill player {}: {}", player.getName().getString(), e.getMessage());
        }
        break;

      case PunishmentMode.DAMAGE:
        try {
          float damage = (float) config.playerDamageAmount.get();
          player.hurtServer(world, player.damageSources().generic(), damage);
          if (config.debugMode.get()) {
            LOGGER.info("Applied {} damage to {}", damage, player.getName().getString());
          }
        } catch (Exception e) {
          LOGGER.warn(
              "Failed to damage player {}: {}", player.getName().getString(), e.getMessage());
        }
        break;

      case PunishmentMode.LIGHTNING_ONLY:
        // Lightning effect already applied above
        if (config.debugMode.get()) {
          LOGGER.info("Applied lightning-only punishment to {}", player.getName().getString());
        }
        break;

      default:
        LOGGER.warn("Unknown punishment mode: {}. Defaulting to DEATH.", punishmentMode);
        try {
          player.hurtServer(world, player.damageSources().generic(), Float.MAX_VALUE);
        } catch (Exception e) {
          LOGGER.warn("Failed to apply default death punishment: {}", e.getMessage());
        }
        break;
    }
  }

  private ServerPlayer getPlayerFromDamageSource(DamageSource source) {
    // Direct player damage
    if (source.getEntity() instanceof ServerPlayer) {
      return (ServerPlayer) source.getEntity();
    }

    // Projectile damage - check if the projectile was shot by a player
    if (source.getDirectEntity() != null) {
      Entity projectile = source.getDirectEntity();

      // Check if it's a projectile entity with an owner
      if (projectile instanceof Projectile proj) {
        if (proj.getOwner() instanceof ServerPlayer) {
          return (ServerPlayer) proj.getOwner();
        }
      }

      // Some projectiles might extend different classes, check for Ownable interface
      if (projectile instanceof TraceableEntity ownable) {
        if (ownable.getOwner() instanceof ServerPlayer) {
          return (ServerPlayer) ownable.getOwner();
        }
      }
    }
    return null;
  }

  private String getRandomDeathMessage(String playerName) {
    List<String> messages = config.getDeathMessages();
    if (messages.isEmpty()) {
      return playerName + " was punished for harming a cow";
    }
    String message = messages.get(random.nextInt(messages.size()));
    return message.replace("%player%", playerName);
  }

  public String getPendingDeathMessage(UUID playerUuid) {
    PendingPunishment punishment = pendingPunishments.remove(playerUuid);
    return punishment != null ? punishment.deathMessage : null;
  }
}
