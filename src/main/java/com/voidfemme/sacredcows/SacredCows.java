package com.voidfemme.sacredcows;

import com.voidfemme.sacredcows.commands.CowCommands;
import com.voidfemme.sacredcows.components.CowComponents;
import com.voidfemme.sacredcows.config.CowConfig;
import com.voidfemme.sacredcows.data.CowPositionsData;
import com.voidfemme.sacredcows.features.CowChunkLoaderFeature;
import com.voidfemme.sacredcows.features.CowProtectionFeature;
import com.voidfemme.sacredcows.features.ScoreboardFeature;
import com.voidfemme.sacredcows.util.TickCounter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SacredCows implements ModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SacredCows.class.getName());

  private MinecraftServer server;
  private static SacredCows instance;
  private CowConfig config;
  private CowCommands commands;
  private CowProtectionFeature cowProtectionFeature;
  private ScoreboardFeature scoreboard;
  private CowChunkLoaderFeature cowChunkLoader;
  private TickCounter tickCounter = new TickCounter();
  private CowPositionsData cowPositionsData;

  public final File getDataFolder() {
    return server.getWorldPath(LevelResource.ROOT).resolve("sacredcows").toFile();
  }

  @Override
  public void onInitialize() {
    LOGGER.info("=== Initializing SacredCows mod... ===");
    instance = this;

    // Load configuration
    LOGGER.info("Loading configuration...");
    loadConfig();
    LOGGER.info("Configuration loaded. Enabled: {}", config.modStatus.serialize());

    if (!config.modStatus.get()) {
      LOGGER.info("SacredCows is disabled via configuration.");
      return;
    }

    // Config is now loaded, so we can create the commands and the features!
    commands = new CowCommands(this, config);
    this.scoreboard = new ScoreboardFeature(this, config);
    commands.register();

    // Create the cowProtectionFeature - We will need to call the supplier after the fact
    this.cowPositionsData = new CowPositionsData(this, config);
    cowPositionsData.load();
    this.cowProtectionFeature = new CowProtectionFeature(this, tickCounter, config);
    this.cowChunkLoader = new CowChunkLoaderFeature(this, tickCounter, cowPositionsData, config);

    // Initialize CowComponents so the mixins work
    CowComponents.initialize();

    // Setup server lifecycle events
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          this.server = server;
        });

    // Register event handlers
    cowProtectionFeature.registerEventHandlers();
    cowChunkLoader.registerEventHandlers();
    scoreboard.registerEventHandlers();
    tickCounter.registerEventHandlers();

    LOGGER.info("SacredCows mod initialized!");
    if (config.debugMode.get()) {
      LOGGER.info("Debug mode is enabled.");
    }
  }

  public MinecraftServer getServer() {
    return this.server;
  }

  public void loadConfig() {
    Path configDir = Paths.get("config");
    Path configFile = configDir.resolve("sacredcows.properties");

    try {
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
        config = new CowConfig(configFile);
      }

      // Config is guaranteed to exist now, so load it!
      config.load();
      LOGGER.info("Config loaded successfully");
    } catch (Exception e) {
      // Config file doesn't exist anyways, create a new one and use the defaults
      LOGGER.error("Failed to load configuration, using defaults", e);
      if (config == null) config = new CowConfig(configFile);
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
