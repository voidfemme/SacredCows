package com.voidfemme.sacredcows.features;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import com.voidfemme.sacredcows.data.CowPositionsData;
import com.voidfemme.sacredcows.util.TickCounter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowChunkLoaderFeature {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CowChunkLoaderFeature.class.getName());
  //
  private final SacredCows owner;
  private Map<UUID, ChunkPos> cowPositions = new ConcurrentHashMap<>();
  private TickCounter tickCounter;
  private final CowConfig config;
  private final CowPositionsData cowPositionsData;

  // Chunk loading ticket
  private static TicketType COW_TICKET =
      Registry.register(BuiltInRegistries.TICKET_TYPE, "sacred_cows", new TicketType(0L, 14));

  public CowChunkLoaderFeature(
      SacredCows owner,
      TickCounter tickCounter,
      CowPositionsData cowPositionsData,
      CowConfig config) {
    this.owner = owner;
    this.tickCounter = tickCounter;
    this.config = config;
    this.cowPositionsData = cowPositionsData;
  }

  public void registerEventHandlers() {
    tickCounter.registerIntervalCallback(
        TickCounter.COW_INTERVAL_TICKS, this::updateAllCowLocations);

    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          // Phase 1: get singleton (triggers load from JSON) and re-add tickets
          Map<UUID, ChunkPos> persisted = cowPositionsData.getCowPositions();

          if (!persisted.isEmpty()) {
            LOGGER.info("Restoring {} cow chunk tickets from disk", persisted.size());
            for (Map.Entry<UUID, ChunkPos> entry : persisted.entrySet()) {
              ChunkPos chunkPos = entry.getValue();
              // We don't know which level the cow is in from the JSON alone,
              // so add a ticket on every level. Excess tickets get cleaned up
              // when the cow's actual position is confirmed in Phase 2 / ENTITY_LOAD.
              for (ServerLevel level : server.getAllLevels()) {
                level.getChunkSource().addTicketWithRadius(COW_TICKET, chunkPos, 1);
              }
            }
          }

          // Phase 2: scan live entities. this is the authoritative source
          for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
              if (entity instanceof Cow cow && cow.hasCustomName()) {
                updateCowLocation(cow, level);
              }
            }
          }

          // Phase 3: prune ghost entries (cows in JSON that didn't appear in the scan
          // and didn't get re-added to cowPositions). We leave their tickets in place
          // for now in case the chunk just hasn't loaded yet; ENTITY_LOAD will catch
          // them when they do load.
        });

    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, source) -> {
          // If naming a cow, force the chunk it's in to load
          if (entity instanceof Cow cow && cow.hasCustomName()) {
            ChunkPos chunkPos = ChunkPos.containing(cow.blockPosition());
            source.getChunkSource().addTicketWithRadius(COW_TICKET, chunkPos, 1);
            // Make sure the Map gets updated
            cowPositions.put(cow.getUUID(), chunkPos);
            if (config.debugMode.get()) {
              LOGGER.info(
                  "Cow: {} has been added to cowPositions", cow.getCustomName().getString());
            }
          }
        });

    // Make sure we handle cow deaths gracefully
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, damageSource) -> {
          if (entity.level() instanceof ServerLevel level) {
            onCowDeath(level, entity);
          }
        });

    // Save the cowPositions before shutting down the server
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          cowPositionsData.setCowPositions(cowPositions);
          cowPositionsData.save();
        });
  }

  private void updateAllCowLocations() {
    for (ServerLevel level : owner.getServer().getAllLevels()) {
      for (Entity entity : level.getAllEntities()) {
        if (entity instanceof Cow cow && cow.hasCustomName()) {
          updateCowLocation(cow, level);
        }
      }
    }
  }

  public void updateCowLocation(Cow cow, ServerLevel serverLevel) {
    // If the cow has a custom name
    if (cow.hasCustomName()) {
      // If the cow's last position was saved in the hashmap
      if (cowPositions.containsKey(cow.getUUID())) {
        ChunkPos lastChunk = cowPositions.get(cow.getUUID());
        ChunkPos currentChunk = ChunkPos.containing(cow.blockPosition());
        // If the current chunk is not the last chunk, the cow wandered.
        if (!currentChunk.equals(lastChunk)) {
          if (config.debugMode.get()) {

            LOGGER.info("Cow: {} has moved to a new chunk!", cow.getCustomName().getString());
          }
          // Save new cow position
          cowPositions.put(cow.getUUID(), currentChunk);
          serverLevel.getChunkSource().addTicketWithRadius(COW_TICKET, currentChunk, 1);
          serverLevel.getChunkSource().removeTicketWithRadius(COW_TICKET, lastChunk, 1);
        }
      } else {
        // The cow is not in the list of cowPositions, but has a custom name
        ChunkPos currentChunk = ChunkPos.containing(cow.blockPosition());
        // Add the named cow to the list of known positions.
        cowPositions.put(cow.getUUID(), currentChunk);
        serverLevel.getChunkSource().addTicketWithRadius(COW_TICKET, currentChunk, 1);
        if (config.debugMode.get()) {
          LOGGER.info("Cow: {} has been saved to cowPositions", cow.getCustomName().getString());
          LOGGER.info("New chunk at ({}, {}) is force-loaded", currentChunk.x(), currentChunk.z());
        }
        return;
      }
    }
  }

  private String getCowDeathMessage(Cow cow) {
    return cow.getCustomName().getString() + " has fallen.";
  }

  public void onCowDeath(ServerLevel serverLevel, Entity entity) {
    if (entity instanceof Cow cow) {
      if (cow.hasCustomName() && cowPositions.containsKey(cow.getUUID())) {
        cowPositions.remove(cow.getUUID());
        ChunkPos currentChunk = ChunkPos.containing(cow.blockPosition());
        serverLevel.getChunkSource().removeTicketWithRadius(COW_TICKET, currentChunk, 1);
        if (config.debugMode.get()) {
          LOGGER.info(
              "Fallen cow: {} in chunk ({}, {}) has been removed from cowPositions",
              cow.getCustomName().getString(),
              currentChunk.x(),
              currentChunk.z());
        }
        String customMessage = getCowDeathMessage(cow);
        owner
            .getServer()
            .getPlayerList()
            .broadcastSystemMessage(Component.literal(customMessage), false);
      }
      return;
    }
  }
}
