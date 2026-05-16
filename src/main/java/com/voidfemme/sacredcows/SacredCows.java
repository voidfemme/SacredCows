package com.voidfemme.sacredcows;

import com.voidfemme.sacredcows.commands.CowCommands;
import com.voidfemme.sacredcows.components.CowComponents;
import com.voidfemme.sacredcows.config.CowConfig;
import com.voidfemme.sacredcows.features.CowChunkLoaderFeature;
import com.voidfemme.sacredcows.features.CowProtectionFeature;
import com.voidfemme.sacredcows.features.ScoreboardFeature;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SacredCows implements ModInitializer {
  public static final String MOD_ID = "sacredcows.SacredCows";
  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final int CLEANUP_INTERVAL_TICKS = 600; // 30 seconds

  private MinecraftServer server;
  private static SacredCows instance;
  private CowConfig config;
  private CowCommands commands;
  private CowProtectionFeature cowProtectionFeature;
  private ScoreboardFeature scoreboard;
  private CowChunkLoaderFeature cowChunkLoader;
  private int cleanupTickCounter = 0;
  public long serverTickCounter = 0;

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

    // Config is now loaded, so we can create the commands and the features!
    commands = new CowCommands(this, config);
    this.scoreboard = new ScoreboardFeature(this, config);
    commands.register();

    // Create the cowProtectionFeature - We will need to call the supplier after the fact
    this.cowProtectionFeature = new CowProtectionFeature(this, config);
    this.cowChunkLoader = new CowChunkLoaderFeature();

    // Initialize CowComponents so the mixins work
    CowComponents.initialize();

    // Register event handlers
    cowProtectionFeature.registerEventHandlers();
    cowChunkLoader.registerEventHandlers();

    // Setup server lifecycle events
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          this.server = server;
          scoreboard.setupScoreboard();
        });

    // Set up server tick tracking
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          this.cleanupTickCounter += 1;
          this.serverTickCounter += 1;
          if (this.cleanupTickCounter >= CLEANUP_INTERVAL_TICKS) {
            this.cleanupTickCounter = 0;
            cowProtectionFeature.cleanupExpiredPunishments();
          }
        });

    cowProtectionFeature.registerAfterDeath();

    LOGGER.info("SacredCows mod initialized!");
    if (config.isDebugEnabled()) {
      LOGGER.info("Debug mode is enabled.");
    }
  }

  public MinecraftServer getServer() {
    return this.server;
  }

  public void loadConfig() {
    try {
      Path configDir = Paths.get("config");
      Path configFile = configDir.resolve("sacredcows.properties");

      if (config == null) {
        // Config doesn't exist, create it:
        LOGGER.info("Config directory: {}", configDir.toAbsolutePath());

        // Create the directory if it doesn't exist
        if (!Files.exists(configDir)) {
          LOGGER.info("Config directory doesn't exist, creating it...");
          Files.createDirectories(configDir);
        }

        // Create the new config
        LOGGER.info("Config file path: {}", configFile.toAbsolutePath());
        config = new CowConfig(null);
      }

      // Config is guaranteed to exist now, so load it!
      config.load();
      LOGGER.info("Config loaded successfully");
    } catch (Exception e) {
      // Config file doesn't exist anyways, create a new one and use the defaults
      LOGGER.error("Failed to load configuration, using defaults", e);
      if (config == null) config = new CowConfig(null);
    }
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

  public ScoreboardFeature getScoreboard() {
    return scoreboard;
  }

  public static SacredCows getInstance() {
    return instance;
  }

  public CowConfig getConfig() {
    return config;
  }
}
