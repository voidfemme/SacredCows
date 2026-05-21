package com.voidfemme.sacredcows.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voidfemme.sacredcows.SacredCows;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowPositionsData {
  private static final Logger LOGGER = LoggerFactory.getLogger(CowPositionsData.class.getName());
  private static final String DATAFILE = "sacredcows_cows.json";
  private final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private Map<UUID, ChunkPos> cowPositions = new HashMap<>();
  private static CowPositionsData instance;

  // private CowConfig config; <- TODO: Use config to determine a custom path

  // Fields (not sure if needed)
  private static class CowEntry {
    String uuid;
    int x;
    int z;
  }

  // in SacredCows.java: `Path dataFile =
  // server.getWorldPath(LevelResource.ROOT).resolve("sacredcows_cows.json");`

  public static CowPositionsData getInstance() {
    if (instance == null) {
      instance = new CowPositionsData();
      instance.load();
    }
    return instance;
  }

  public void setCowPositions(Map<UUID, ChunkPos> cowPositions) {
    this.cowPositions = cowPositions;
  }

  public CowPositionsData load() {
    Path cowPositionsDataDir = getCowPositionsDir();
    Path cowPositionsFile = cowPositionsDataDir.resolve(DATAFILE);
    if (Files.exists(cowPositionsFile)) {
      try {
        String json = Files.readString(cowPositionsFile);
        CowEntry[] entries = GSON.fromJson(json, CowEntry[].class);
        for (CowEntry entry : entries) {
          cowPositions.put(UUID.fromString(entry.uuid), new ChunkPos(entry.x, entry.z));
        }
        LOGGER.info("Loaded cow position data from " + cowPositionsFile);
      } catch (IOException e) {
        LOGGER.error(
            "Failed to load cow position data from " + cowPositionsFile + ": " + e.getMessage());
      }
    }
    return this;
  }

  public void save() {
    Path cowPositionsDataDir = getCowPositionsDir();
    Path cowPositionsFile = cowPositionsDataDir.resolve(DATAFILE);

    try {
      // Ensure data directory exists
      Files.createDirectories(cowPositionsDataDir);

      // save logic
      List<CowEntry> entries = new ArrayList<>();
      for (Map.Entry<UUID, ChunkPos> e : cowPositions.entrySet()) {
        CowEntry entry = new CowEntry();
        entry.uuid = e.getKey().toString();
        entry.x = e.getValue().x();
        entry.z = e.getValue().z();
        entries.add(entry);
      }
      String json = GSON.toJson(entries);
      Files.writeString(
          cowPositionsFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      LOGGER.info("Saved cow position data to " + cowPositionsFile);
    } catch (IOException e) {
      LOGGER.error(
          "Failed to save cow position data to " + cowPositionsFile + ": " + e.getMessage());
    }
  }

  private Path getCowPositionsDir() {
    SacredCows mod = SacredCows.getInstance();
    if (mod != null) {
      return mod.getDataFolder().toPath();
    } else {
      // Running outside of the plugin (e.g. in tests) -> use a temp dir
      return Path.of(System.getProperty("java.io.tmpdir"), "sacredcows");
    }
  }

  public Map<UUID, ChunkPos> getCowPositions() {
    return cowPositions;
  }
}
