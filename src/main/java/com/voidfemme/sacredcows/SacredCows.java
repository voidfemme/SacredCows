package com.voidfemme.sacredcows;

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
  public static final String MOD_ID = "sacredcows";
  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final int CLEANUP_INTERVAL_TICKS = 600; // 30 seconds

  private MinecraftServer server;
  private static SacredCows instance;
  private SacredCowsConfig config;
  private SacredCowsCommands commands;
  private CowProtectionFeature cowProtectionFeature;
  private ScoreboardFeature scoreboard;
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
    commands = new SacredCowsCommands(this, config);
    this.scoreboard = new ScoreboardFeature(this, config);
    commands.register();

    // Create the cowProtectionFeature - We will need to call the supplier after the fact
    this.cowProtectionFeature = new CowProtectionFeature(this, config);

    // Register event handlers
    cowProtectionFeature.registerEventHandlers();

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

  public SacredCowsConfig getConfig() {
    return config;
  }
}
