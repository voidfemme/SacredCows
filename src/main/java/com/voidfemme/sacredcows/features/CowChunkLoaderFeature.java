package com.voidfemme.sacredcows.features;

import com.voidfemme.sacredcows.SacredCows;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowChunkLoaderFeature {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CowChunkLoaderFeature.class.getName());
  private final SacredCows owner;
  // private final CowConfig config;
  Map<UUID, ChunkPos> cowPositions = new ConcurrentHashMap<>();

  public CowChunkLoaderFeature(SacredCows owner) {
    this.owner = owner;
    //   this.config = config;
  }

  public void registerEventHandlers() {
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, source) -> {
          // If naming a cow, force the chunk it's in to load
          if (entity instanceof Cow cow && cow.hasCustomName()) {
            ChunkPos chunkPos = ChunkPos.containing(cow.blockPosition());
            source.setChunkForced(chunkPos.x(), chunkPos.z(), true);
            // Make sure the Map gets updated
            cowPositions.put(cow.getUUID(), chunkPos);
            LOGGER.info("Cow: " + cow.getCustomName() + " has been added to cowPositions");
          }
        });
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
          LOGGER.info("Cow: " + cow.getCustomName() + "has moved to a new chunk!");
          // Save new cow position
          cowPositions.put(cow.getUUID(), currentChunk);

          Set<ChunkPos> oldChunks = getChunks3x3(lastChunk);
          Set<ChunkPos> newChunks = getChunks3x3(currentChunk);

          // Force load new chunks
          for (ChunkPos chunk : newChunks) {
            serverLevel.setChunkForced(chunk.x(), chunk.z(), true);
            LOGGER.info(
                "New chunk at ("
                    + currentChunk.x()
                    + ", "
                    + currentChunk.z()
                    + ") is force-loaded");
          }

          // Unload non-overlapping chunks
          for (ChunkPos chunk : oldChunks) {
            if (!newChunks.contains(chunk)) {
              serverLevel.setChunkForced(chunk.x(), chunk.z(), false);
            }
            LOGGER.info(
                "New chunk at ("
                    + lastChunk.x()
                    + ", "
                    + lastChunk.z()
                    + ") is no longer force-loaded");
          }
        }
      } else if (!cowPositions.containsKey(cow.getUUID())) {
        // The cow is not in the list of cowPositions, but has a custom name
        ChunkPos currentChunk = ChunkPos.containing(cow.blockPosition());
        // Add the named cow to the list of known positions.
        cowPositions.put(cow.getUUID(), currentChunk);
        LOGGER.info("Cow: " + cow.getCustomName() + " has been saved to cowPositions");
        serverLevel.setChunkForced(currentChunk.x(), currentChunk.z(), true);
        LOGGER.info(
            "New chunk at (" + currentChunk.x() + ", " + currentChunk.z() + ") is force-loaded");
        return;
      }
    }
  }

  private Set<ChunkPos> getChunks3x3(ChunkPos center) {
    Set<ChunkPos> chunks = new HashSet<>();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        chunks.add(new ChunkPos(center.x(), center.z()));
      }
    }
    return chunks;
  }

  private String getCowDeathMessage(Cow cow) {
    return cow.getCustomName() + " has fallen.";
  }

  public void onCowDeath(ServerLevel serverLevel) {
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, DamageSource) -> {
          if (entity instanceof Cow cow) {
            if (cow.hasCustomName() && cowPositions.containsKey(cow.getUUID())) {
              cowPositions.remove(cow.getUUID());
              ChunkPos currentChunk = ChunkPos.containing(cow.blockPosition());
              serverLevel.setChunkForced(currentChunk.x(), currentChunk.z(), false);
              LOGGER.info(
                  "Fallen cow: " + cow.getCustomName() + " in chunk (" + currentChunk.x(),
                  ", ",
                  currentChunk.z(),
                  ") has been removed from cowPositions");
            }
            String customMessage = getCowDeathMessage(cow);
            if (customMessage != null) {
              owner
                  .getServer()
                  .getPlayerList()
                  .broadcastSystemMessage(Component.literal(customMessage), false);
            }
            return;
          }
        });
  }
}
