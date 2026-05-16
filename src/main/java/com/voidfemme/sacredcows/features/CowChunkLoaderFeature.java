package com.voidfemme.sacredcows.features;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.ChunkPos;

public class CowChunkLoaderFeature {
  // public static final String MOD_ID = "sacredcows.features.CowChunkLoaderFeature";
  // private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  // private final SacredCows owner;
  // private final CowConfig config;

  // public CowChunkLoaderFeature(SacredCows owner, CowConfig config) {
  //   this.owner = owner;
  //   this.config = config;
  // }

  public void registerEventHandlers() {
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, source) -> {
          // logic here
          if (entity instanceof Cow cow && cow.hasCustomName()) {
            ChunkPos chunkPos = ChunkPos.containing(cow.blockPosition());
            source.setChunkForced(chunkPos.x(), chunkPos.z(), true);
          }
        });
  }
}
