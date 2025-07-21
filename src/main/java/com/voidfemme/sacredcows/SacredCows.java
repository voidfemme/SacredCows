package com.voidfemme.sacredcows;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

public class SacredCows implements ModInitializer {
    public static final String MOD_ID = "sacredcows";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SacredCows instance;
    private SacredCowsConfig config;
    private final Random random = new Random();
    private final Map<UUID, PendingPunishment> pendingPunishments = new ConcurrentHashMap<>();
    private MinecraftServer server;

    private static class PendingPunishment {
        final String deathMessage;
        final long timestamp;

        PendingPunishment(String deathMessage) {
            this.deathMessage = deathMessage;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SacredCows mod...");
        instance = this;

        // Load configuration
        loadConfig();

        if (!config.isEnabled()) {
            LOGGER.info("SacredCows is disabled via configuration.");
            return;
        }

        // Register event handlers
        registerEventHandlers();

        // Register commands
        registerCommands();

        // Setup server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            setupScoreboard();
            startCleanupTask();
        });

        // In your SacredCows.java onInitialize() method, add:
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (getConfig().isCustomDeathMessagesEnabled()) {
                    String customMessage = getPendingDeathMessage(player.getUuid());
                    if (customMessage != null) {
                        player.getServer().getPlayerManager().broadcast(
                                Text.literal(customMessage), false);
                    }
                }
            }
        });

        LOGGER.info("SacredCows mod initialized!");
        if (config.isDebugEnabled()) {
            LOGGER.info("Debug mode is enabled.");
        }
    }

    private void loadConfig() {
        try {
            Path configDir = Paths.get("config");
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            Path configFile = configDir.resolve("sacredcows.properties");
            config = new SacredCowsConfig(configFile);
            config.load();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration, using defaults", e);
            config = new SacredCowsConfig(null);
        }
    }

    private void registerEventHandlers() {
        // Handle entity damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!config.isEnabled())
                return true;

            // Check if entity is a cow and damage source is a player
            if (!(entity instanceof CowEntity))
                return true;

            ServerPlayerEntity player = getPlayerFromDamageSource(source);
            if (player == null)
                return true;

            // Check bypass permission
            if (hasPermission(player, config.getBypassPermission())) {
                if (config.isDebugEnabled()) {
                    LOGGER.info("Player {} bypassed cow protection with permission.", player.getName().getString());
                }
                return true;
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

            if (!(entity instanceof CowEntity))
                return;

            ServerPlayerEntity player = getPlayerFromDamageSource(source);
            if (player != null) {
                trackKill(player);
            }
        });
    }

    private ServerPlayerEntity getPlayerFromDamageSource(DamageSource source) {
        if (source.getAttacker() instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity) source.getAttacker();
        }
        return null;
    }

    private boolean hasPermission(ServerPlayerEntity player, String permission) {
        // Fabric doesn't have built-in permissions, so we'll use OP status for
        // bypass/admin permissions
        // For bypass permission, check if player is OP
        if (permission.equals(config.getBypassPermission()) || permission.equals(config.getAdminPermission())) {
            return server.getPlayerManager().isOperator(player.getGameProfile());
        }
        return false;
    }

    private void setupScoreboard() {
        if (!config.isScoreboardEnabled())
            return;

        try {
            Scoreboard scoreboard = server.getScoreboard();

            if (config.isTrackAssaultsEnabled()) {
                String assaultObjective = config.getAssaultObjective();
                if (scoreboard.getNullableObjective(assaultObjective) == null) {
                    scoreboard.addObjective(assaultObjective, ScoreboardCriterion.DUMMY,
                            Text.literal(config.getAssaultDisplay()), ScoreboardCriterion.RenderType.INTEGER, false,
                            null);
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Created assault scoreboard objective: {}", assaultObjective);
                    }
                }
            }

            if (config.isTrackKillsEnabled()) {
                String killObjective = config.getKillObjective();
                if (scoreboard.getNullableObjective(killObjective) == null) {
                    scoreboard.addObjective(killObjective, ScoreboardCriterion.DUMMY,
                            Text.literal(config.getKillDisplay()), ScoreboardCriterion.RenderType.INTEGER, false, null);
                    if (config.isDebugEnabled()) {
                        LOGGER.info("Created kill scoreboard objective: {}", killObjective);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to setup scoreboard: {}", e.getMessage());
        }
    }

    private void trackAssault(ServerPlayerEntity player) {
        if (!config.isScoreboardEnabled() || !config.isTrackAssaultsEnabled())
            return;

        try {
            Scoreboard scoreboard = server.getScoreboard();
            ScoreboardObjective objective = scoreboard.getNullableObjective(config.getAssaultObjective());

            if (objective != null) {
                ScoreHolder scoreHolder = ScoreHolder.fromName(player.getName().getString());
                int currentScore = scoreboard.getOrCreateScore(scoreHolder, objective).getScore();
                scoreboard.getOrCreateScore(scoreHolder, objective).setScore(currentScore + 1);

                if (config.isDebugEnabled()) {
                    LOGGER.info("Tracked assault for {}, new score: {}", player.getName().getString(),
                            currentScore + 1);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to track assault for {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private void trackKill(ServerPlayerEntity player) {
        try {
            Scoreboard scoreboard = server.getScoreboard();
            ScoreboardObjective objective = scoreboard.getNullableObjective(config.getKillObjective());

            if (objective != null) {
                ScoreHolder scoreHolder = ScoreHolder.fromName(player.getName().getString());
                int currentScore = scoreboard.getOrCreateScore(scoreHolder, objective).getScore();
                scoreboard.getOrCreateScore(scoreHolder, objective).setScore(currentScore + 1);

                if (config.isDebugEnabled()) {
                    LOGGER.info("Tracked kill for {}, new score: {}", player.getName().getString(), currentScore + 1);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to track kill for {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private void applyPunishment(ServerPlayerEntity player) {
        String punishmentType = config.getPunishmentType().toUpperCase();

        // Lightning effect
        if (config.isLightningEffectEnabled()) {
            try {
                Vec3d pos = player.getPos();
                LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, player.getWorld());
                lightning.setPosition(pos);
                // Make it cosmetic only - otherwise the cow dies too and that's not good praxis
                lightning.setCosmetic(true);
                player.getWorld().spawnEntity(lightning);
            } catch (Exception e) {
                LOGGER.warn("Failed to create lightning effect: {}", e.getMessage());
            }
        }

        // Prepare death message if needed
        if (config.isCustomDeathMessagesEnabled()) {
            String deathMessage = getRandomDeathMessage(player.getName().getString());
            pendingPunishments.put(player.getUuid(), new PendingPunishment(deathMessage));
        }

        // Apply punishment
        switch (punishmentType) {
            case "DEATH":
                try {
                    player.damage((ServerWorld) player.getWorld(), player.getDamageSources().generic(),
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
                    player.damage((ServerWorld) player.getWorld(), player.getDamageSources().generic(), damage);
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
                    player.damage((ServerWorld) player.getWorld(), player.getDamageSources().generic(),
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

    private void startCleanupTask() {
        // Schedule cleanup task every 30 seconds (600 ticks)
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredPunishments();
            }
        }, 30000, 30000);
    }

    private void cleanupExpiredPunishments() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 10000; // 10 seconds

        pendingPunishments.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue().timestamp) > expireTime;
            if (expired && config.isDebugEnabled()) {
                LOGGER.info("Cleaned up expired punishment for player UUID: {}", entry.getKey());
            }
            return expired;
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerSacredCowsCommand(dispatcher);
        });
    }

    private void registerSacredCowsCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sacredcows")
                .requires(source -> {
                    try {
                        ServerPlayerEntity player = source.getPlayer();
                        return player != null && hasPermission(player, config.getAdminPermission());
                    } catch (Exception e) {
                        // If not a player (console), allow
                        return true;
                    }
                })
                .executes(this::executeMainCommand)
                .then(CommandManager.literal("reload")
                        .executes(this::executeReloadCommand))
                .then(CommandManager.literal("stats")
                        .then(CommandManager.argument("player", StringArgumentType.string())
                                .executes(this::executeStatsCommand))));
    }

    private int executeMainCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("§6SacredCows v1.2.1"), false);
        source.sendFeedback(() -> Text.literal("Usage: /sacredcows [reload|stats <player>]").formatted(Formatting.GOLD),
                false);
        return 1;
    }

    private int executeReloadCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            loadConfig();
            setupScoreboard();
            source.sendFeedback(
                    () -> Text.literal("SacredCows configuration reloaded successfully!").formatted(Formatting.GREEN),
                    false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§cFailed to reload configuration: " + e.getMessage()), false);
            LOGGER.warn("Failed to reload config: {}", e.getMessage());
        }
        return 1;
    }

    private int executeStatsCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "player");

        try {
            Scoreboard scoreboard = server.getScoreboard();

            source.sendFeedback(
                    () -> Text.literal("=== Cow Stats for " + playerName + " ===").formatted(Formatting.GOLD), false);

            if (config.isTrackAssaultsEnabled()) {
                ScoreboardObjective assaults = scoreboard.getNullableObjective(config.getAssaultObjective());
                if (assaults != null) {
                    ScoreHolder scoreHolder = ScoreHolder.fromName(playerName);
                    int assaultScore = scoreboard.getOrCreateScore(scoreHolder, assaults).getScore();
                    source.sendFeedback(
                            () -> Text.literal("Cow Assaults: " + assaultScore).formatted(Formatting.YELLOW), false);
                }
            }

            if (config.isTrackKillsEnabled()) {
                ScoreboardObjective kills = scoreboard.getNullableObjective(config.getKillObjective());
                if (kills != null) {
                    ScoreHolder scoreHolder = ScoreHolder.fromName(playerName);
                    int killScore = scoreboard.getOrCreateScore(scoreHolder, kills).getScore();
                    source.sendFeedback(() -> Text.literal("Cow Kills: " + killScore).formatted(Formatting.YELLOW),
                            false);
                }
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§cError retrieving stats: " + e.getMessage()), false);
            LOGGER.warn("Error showing stats for {}: {}", playerName, e.getMessage());
        }

        return 1;
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
