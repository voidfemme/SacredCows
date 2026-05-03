package com.voidfemme.sacredcows.features;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowProtectionFeature {
  public static final String MOD_ID = "sacredcows.features.CowProtectionFeature";
  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final long PUNISHMENT_EXPIRE_TICKS = 200; // 10 sec
  private final Map<UUID, PendingPunishment> pendingPunishments = new ConcurrentHashMap<>();
  private final SacredCows owner;
  private final CowConfig config;
  private final Random random = new Random();

  public CowProtectionFeature(SacredCows owner, CowConfig config) {
    this.owner = owner;
    this.config = config;
  }

  public enum PunishmentMode {
    DEATH,
    DAMAGE,
    LIGHTNING_ONLY;

    public static PunishmentMode fromString(String s) {
      return switch (s.toLowerCase()) {
        case "death", "kill" -> DEATH;
        case "damage", "hurt" -> DAMAGE;
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
      this.creationTick = owner.serverTickCounter;
    }
  }

  public void cleanupExpiredPunishments() {
    long currentTick = owner.serverTickCounter;

    pendingPunishments
        .entrySet()
        .removeIf(
            entry -> {
              boolean expired =
                  (currentTick - entry.getValue().creationTick) > PUNISHMENT_EXPIRE_TICKS;
              if (expired && config.isDebugEnabled()) {
                LOGGER.info("Cleaned up expired punishment for player UUID: {}", entry.getKey());
              }
              return expired;
            });
  }

  public void registerEventHandlers() {
    // Handle entity damage
    ServerLivingEntityEvents.ALLOW_DAMAGE.register(
        (entity, source, amount) -> {
          if (!config.isEnabled()) return true;

          // Check if entity is a cow and damage source is a player
          if (!(entity instanceof Cow)) return true;

          ServerPlayer player = getPlayerFromDamageSource(source);
          if (player == null) return true;

          // Get the permission level of the player
          if (player.permissions() instanceof LevelBasedPermissionSet leveled) {
            PermissionLevel playerlevel = leveled.level();
            PermissionLevel requiredLevel = PermissionLevel.byId(config.getBypassOpLevel());

            // Now what can you do with level?
            // Check if player has bypass permission (configurable OP level)
            if (config.isAllowBypass() && playerlevel.isEqualOrHigherThan(requiredLevel)) {
              // Player has sufficient permissions
              if (config.isDebugEnabled()) {
                LOGGER.info(
                    "Player {} bypassed cow protection (OP level {} >= required {})",
                    player.getName().getString(),
                    playerlevel,
                    config.getBypassOpLevel());
              }
              return true;
            }
            if (config.isDebugEnabled()) {
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

          // Cancel damage
          return false;
        });

    // Handle entity death for kill tracking
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, source) -> {
          // If config is not enabled or if we are not tracking kills, return without doing anything
          if (!config.isEnabled() || !config.isTrackKillsEnabled()) return;

          // If the entity is not a cow, return without doing anything
          if (!(entity instanceof Cow)) return;

          ServerPlayer player = getPlayerFromDamageSource(source);
          if (player != null) {
            owner.getScoreboard().trackKill(player, config);
          }
        });
  }

  private void applyPunishment(ServerPlayer player) {
    PunishmentMode punishmentMode = config.getPunishmentMode();
    ServerLevel world = (ServerLevel) player.level();

    // Lightning effect
    if (config.isLightningEffectEnabled()) {
      try {
        Vec3 pos = new Vec3(player.getX(), player.getY(), player.getZ());
        LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
        lightning.setPos(pos);
        // Make it cosmetic only - otherwise the cow dies too and that's not good praxis
        lightning.setVisualOnly(true);
        world.addFreshEntity(lightning);
      } catch (Exception e) {
        LOGGER.warn("Failed to create lightning effect: {}", e.getMessage());
      }
    }

    // Prepare death message if needed
    if (config.isCustomDeathMessagesEnabled()) {
      String deathMessage = getRandomDeathMessage(player.getName().getString());
      pendingPunishments.put(player.getUUID(), new PendingPunishment(deathMessage));
    }

    // Apply punishment
    switch (punishmentMode) {
      case PunishmentMode.DEATH:
        try {
          player.hurtServer(world, player.damageSources().generic(), Float.MAX_VALUE);
          if (config.isDebugEnabled()) {
            LOGGER.info("Applied death punishment to {}", player.getName().getString());
          }
        } catch (Exception e) {
          LOGGER.warn("Failed to kill player {}: {}", player.getName().getString(), e.getMessage());
        }
        break;

      case PunishmentMode.DAMAGE:
        try {
          float damage = (float) config.getDamageAmount();
          player.hurtServer(world, player.damageSources().generic(), damage);
          if (config.isDebugEnabled()) {
            LOGGER.info("Applied {} damage to {}", damage, player.getName().getString());
          }
        } catch (Exception e) {
          LOGGER.warn(
              "Failed to damage player {}: {}", player.getName().getString(), e.getMessage());
        }
        break;

      case PunishmentMode.LIGHTNING_ONLY:
        // Lightning effect already applied above
        if (config.isDebugEnabled()) {
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
      if (projectile instanceof Projectile) {
        Projectile proj = (Projectile) projectile;
        if (proj.getOwner() instanceof ServerPlayer) {
          return (ServerPlayer) proj.getOwner();
        }
      }

      // Some projectiles might extend different classes, check for Ownable interface
      if (projectile instanceof TraceableEntity) {
        TraceableEntity ownable = (TraceableEntity) projectile;
        if (ownable.getOwner() instanceof ServerPlayer) {
          return (ServerPlayer) ownable.getOwner();
        }
      }
    }
    return null;
  }

  public void registerAfterDeath() {
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, damageSource) -> {
          if (entity instanceof ServerPlayer player) {
            if (owner.getConfig().isCustomDeathMessagesEnabled()) {
              String customMessage = getPendingDeathMessage(player.getUUID());
              if (customMessage != null) {
                owner
                    .getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(Component.literal(customMessage), false);
              }
            }
          }
        });
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
