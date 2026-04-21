package com.voidfemme.sacredcows;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SacredCows implements ModInitializer {
    public static final String MOD_ID = "sacredcows";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final long PUNISHMENT_EXPIRE_TICKS = 200; // 10 seconds
    private static final int CLEANUP_INTERVAL_TICKS = 600; // 30 seconds

    private static SacredCows instance;
    private SacredCowsConfig config;
    private final Random random = new Random();
    private final Map<UUID, PendingPunishment> pendingPunishments = new ConcurrentHashMap<>();
    private MinecraftServer server;
    private SacredCowsCommands commands;
    private int cleanupTickCounter = 0;
    private long serverTickCounter = 0;

    private class PendingPunishment {
        final String deathMessage;
        final long creationTick;

        PendingPunishment(String deathMessage) {
            this.deathMessage = deathMessage;
            this.creationTick = serverTickCounter;
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("=== Initializing SacredCows mod... ===");
        instance = this;

        // Load configuration
        LOGGER.info("Loading configuration...");
        loadConfig();
        LOGGER.info("Configuration loaded. Enabled: {}", config.isEnabled());

        if (!config.isEnabled()) {
            LOGGER.info("SacredCows is disabled via configuration.");
            return;
        }

        commands = new SacredCowsCommands(this, config);
        commands.register();

        // Register event handlers
        registerEventHandlers();

        // Setup server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            setupScoreboard();
        });

        // Set up server tick tracking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            this.cleanupTickCounter += 1;
            this.serverTickCounter += 1;
            if (this.cleanupTickCounter >= CLEANUP_INTERVAL_TICKS) {
                this.cleanupTickCounter = 0;
                cleanupExpiredPunishments();
            }
        });

        // In your SacredCows.java onInitialize() method, add:
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                if (getConfig().isCustomDeathMessagesEnabled()) {
                    String customMessage = getPendingDeathMessage(player.getUUID());
                    if (customMessage != null) {
                        server.getPlayerList().broadcastSystemMessage(
                                Component.literal(customMessage), false);
                    }
                }
            }
        });

        LOGGER.info("SacredCows mod initialized!");
        if (config.isDebugEnabled()) {
            LOGGER.info("Debug mode is enabled.");
        }
    }

    public void loadConfig() {
        try {
            Path configDir = Paths.get("config");
            LOGGER.info("Config directory: {}", configDir.toAbsolutePath());
            if (!Files.exists(configDir)) {
                LOGGER.info("Config directory doesn't exist, creating it...");
                Files.createDirectories(configDir);
            }

            Path configFile = configDir.resolve("sacredcows.properties");
            LOGGER.info("Config file path: {}", configFile.toAbsolutePath());
            config = new SacredCowsConfig(configFile);
            config.load();
            LOGGER.info("Config loaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration, using defaults", e);
            config = new SacredCowsConfig(null);
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

    private void registerEventHandlers() {
        // Handle entity damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!config.isEnabled())
                return true;

            // Check if entity is a cow and damage source is a player
            if (!(entity instanceof Cow))
                return true;

            ServerPlayer player = getPlayerFromDamageSource(source);
            if (player == null)
                return true;
            // Get the permissionlevel of the player
            if (player.permissions() instanceof LevelBasedPermissionSet leveled) {
                PermissionLevel playerlevel = leveled.level();
                PermissionLevel requiredLevel = PermissionLevel.byId(config.getBypassOpLevel());
                // now what can you do with level?
                // Check if player has bypass permission (configurable OP level)
                if (config.isAllowBypass() && playerlevel.isEqualOrHigherThan(requiredLevel)) {
                    // Player has sufficient permission
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Player {} bypassed cow protection (OP level {} >= required {})",
                                player.getName().getString(), playerlevel, config.getBypassOpLevel());
                    }
                    return true;
                }
                if (config.isDebugEnabled()) {
                    LOGGER.info("Player {} attacked a cow, applying punishment (OP level: {})",
                            player.getName().getString(), playerlevel);
                }

            }

            // Track assault
            trackAssault(player);

            // Apply punishment
            applyPunishment(player);

            // Cancel damage
            return false;
        });

        // Handle entity death for kill tracking
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!config.isEnabled() || !config.isTrackKillsEnabled())
                return;

            if (!(entity instanceof Cow))
                return;

            ServerPlayer player = getPlayerFromDamageSource(source);
            if (player != null) {
                trackKill(player);
            }
        });
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
            if (projectile instanceof net.minecraft.world.entity.projectile.Projectile) {
                net.minecraft.world.entity.projectile.Projectile proj = (net.minecraft.world.entity.projectile.Projectile) projectile;
                if (proj.getOwner() instanceof ServerPlayer) {
                    return (ServerPlayer) proj.getOwner();
                }
            }

            // Some projectiles might extend different classes, check for Ownable interface
            if (projectile instanceof net.minecraft.world.entity.TraceableEntity) {
                net.minecraft.world.entity.TraceableEntity ownable = (net.minecraft.world.entity.TraceableEntity) projectile;
                if (ownable.getOwner() instanceof ServerPlayer) {
                    return (ServerPlayer) ownable.getOwner();
                }
            }
        }

        return null;
    }

    public void setupScoreboard() {
        if (!config.isScoreboardEnabled())
            return;

        try {
            Scoreboard scoreboard = server.getScoreboard();

            if (config.isTrackAssaultsEnabled()) {
                String assaultObjective = config.getAssaultObjective();
                if (scoreboard.getObjective(assaultObjective) == null) {
                    scoreboard.addObjective(assaultObjective, ObjectiveCriteria.DUMMY,
                            Component.literal(config.getAssaultDisplay()), ObjectiveCriteria.RenderType.INTEGER, false,
                            null);
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Created assault scoreboard objective: {}", assaultObjective);
                    }
                }
            }

            if (config.isTrackKillsEnabled()) {
                String killObjective = config.getKillObjective();
                if (scoreboard.getObjective(killObjective) == null) {
                    scoreboard.addObjective(killObjective, ObjectiveCriteria.DUMMY,
                            Component.literal(config.getKillDisplay()), ObjectiveCriteria.RenderType.INTEGER, false, null);
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Created kill scoreboard objective: {}", killObjective);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to setup scoreboard: {}", e.getMessage());
        }
    }

    private void trackAssault(ServerPlayer player) {
        if (!config.isScoreboardEnabled() || !config.isTrackAssaultsEnabled())
            return;

        try {
            Scoreboard scoreboard = server.getScoreboard();
            Objective objective = scoreboard.getObjective(config.getAssaultObjective());

            if (objective != null) {
                ScoreHolder scoreHolder = ScoreHolder.forNameOnly(player.getName().getString());
                int currentScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective).get();
                scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(currentScore + 1);

                if (config.isDebugEnabled()) {
                    LOGGER.info("Tracked assault for {}, new score: {}", player.getName().getString(),
                            currentScore + 1);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to track assault for {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private void trackKill(ServerPlayer player) {
        try {
            Scoreboard scoreboard = server.getScoreboard();
            Objective objective = scoreboard.getObjective(config.getKillObjective());

            if (objective != null) {
                ScoreHolder scoreHolder = ScoreHolder.forNameOnly(player.getName().getString());
                int currentScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective).get();
                scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(currentScore + 1);

                if (config.isDebugEnabled()) {
                    LOGGER.info("Tracked kill for {}, new score: {}", player.getName().getString(), currentScore + 1);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to track kill for {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private void applyPunishment(ServerPlayer player) {
        String punishmentType = config.getPunishmentType().toUpperCase();
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
        switch (punishmentType) {
            case "DEATH":
                try {
                    player.hurtServer(world, player.damageSources().generic(),
                            Float.MAX_VALUE);
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Applied death punishment to {}", player.getName().getString());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to kill player {}: {}", player.getName().getString(), e.getMessage());
                }
                break;

            case "DAMAGE":
                try {
                    float damage = (float) config.getDamageAmount();
                    player.hurtServer(world, player.damageSources().generic(), damage);
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Applied {} damage to {}", damage, player.getName().getString());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to damage player {}: {}", player.getName().getString(), e.getMessage());
                }
                break;

            case "LIGHTNING_ONLY":
                // Lightning effect already applied above
                if (config.isDebugEnabled()) {
                    LOGGER.info("Applied lightning-only punishment to {}", player.getName().getString());
                }
                break;

            default:
                LOGGER.warn("Unknown punishment type: {}. Defaulting to DEATH.", punishmentType);
                try {
                    player.hurtServer(world, player.damageSources().generic(),
                            Float.MAX_VALUE);
                } catch (Exception e) {
                    LOGGER.warn("Failed to apply default death punishment: {}", e.getMessage());
                }
                break;
        }
    }

    private String getRandomDeathMessage(String playerName) {
        List<String> messages = config.getDeathMessages();
        if (messages.isEmpty()) {
            return playerName + " was punished for harming a cow";
        }
        String message = messages.get(random.nextInt(messages.size()));
        return message.replace("%player%", playerName);
    }

    private void cleanupExpiredPunishments() {
        long currentTick = this.serverTickCounter;

        pendingPunishments.entrySet().removeIf(entry -> {
            boolean expired = (currentTick - entry.getValue().creationTick) > PUNISHMENT_EXPIRE_TICKS;
            if (expired && config.isDebugEnabled()) {
                LOGGER.info("Cleaned up expired punishment for player UUID: {}", entry.getKey());
            }
            return expired;
        });
    }

    private static String getVersion() {
        try (InputStream input = SacredCows.class.getResourceAsStream("/version.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }

    public static SacredCows getInstance() {
        return instance;
    }

    public SacredCowsConfig getConfig() {
        return config;
    }

    public String getPendingDeathMessage(UUID playerUuid) {
        PendingPunishment punishment = pendingPunishments.remove(playerUuid);
        return punishment != null ? punishment.deathMessage : null;
    }
}
