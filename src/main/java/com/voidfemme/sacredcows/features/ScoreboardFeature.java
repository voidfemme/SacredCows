package com.voidfemme.sacredcows.features;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScoreboardFeature {
  private static final Logger LOGGER = LoggerFactory.getLogger(ScoreboardFeature.class.getName());
  private final SacredCows owner;
  private final CowConfig config;

  public ScoreboardFeature(SacredCows owner, CowConfig config) {
    this.owner = owner;
    this.config = config;
  }

  public void setupScoreboard() {
    if (!config.scoreboardEnabled.get()) return;

    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();

      if (config.trackAssaults.get()) {
        String assaultObjective = config.assaultObjective.get();
        if (scoreboard.getObjective(assaultObjective) == null) {
          scoreboard.addObjective(
              assaultObjective,
              ObjectiveCriteria.DUMMY,
              Component.literal(config.assaultDisplay.get()),
              ObjectiveCriteria.RenderType.INTEGER,
              false,
              null);
          if (config.debugMode.get()) {
            LOGGER.info("Created assault scoreboard objective: {}", assaultObjective);
          }
        }
      }

      if (config.trackKills.get()) {
        String killObjective = config.killObjective.get();
        if (scoreboard.getObjective(killObjective) == null) {
          scoreboard.addObjective(
              killObjective,
              ObjectiveCriteria.DUMMY,
              Component.literal(config.killDisplay.get()),
              ObjectiveCriteria.RenderType.INTEGER,
              false,
              null);
          if (config.debugMode.get()) {
            LOGGER.info("Created kill scoreboard objective: {}", killObjective);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to setup scoreboard: {}", e.getMessage());
    }
  }

  public void registerEventHandlers() {
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          setupScoreboard();
        });
  }

  public void trackAssault(ServerPlayer player, CowConfig config) {
    if (!config.scoreboardEnabled.get() || !config.trackAssaults.get()) return;

    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();
      Objective objective = scoreboard.getObjective(config.assaultObjective.get());

      if (objective != null) {
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly(player.getName().getString());
        int currentScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective).get();
        scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(currentScore + 1);

        if (config.debugMode.get()) {
          LOGGER.info(
              "Tracked assault for {}, new score: {}",
              player.getName().getString(),
              currentScore + 1);
        }
      }
    } catch (Exception e) {
      LOGGER.warn(
          "Failed to track assault for {}: {}", player.getName().getString(), e.getMessage());
    }
  }

  public void trackKill(ServerPlayer player, CowConfig config) {
    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();
      Objective objective = scoreboard.getObjective(config.killObjective.get());

      if (objective != null) {
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly(player.getName().getString());
        int currentScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective).get();
        scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(currentScore + 1);

        if (config.debugMode.get()) {
          LOGGER.info(
              "Tracked kill for {}, new score: {}", player.getName().getString(), currentScore + 1);
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to track kill for {}: {}", player.getName().getString(), e.getMessage());
    }
  }
}
