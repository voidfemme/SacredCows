package com.voidfemme.sacredcows.features;

import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
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
    if (!config.isScoreboardEnabled()) return;

    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();

      if (config.isTrackAssaultsEnabled()) {
        String assaultObjective = config.getAssaultObjective();
        if (scoreboard.getObjective(assaultObjective) == null) {
          scoreboard.addObjective(
              assaultObjective,
              ObjectiveCriteria.DUMMY,
              Component.literal(config.getAssaultDisplay()),
              ObjectiveCriteria.RenderType.INTEGER,
              false,
              null);
          if (config.isDebugEnabled()) {
            LOGGER.info("Created assault scoreboard objective: {}", assaultObjective);
          }
        }
      }

      if (config.isTrackKillsEnabled()) {
        String killObjective = config.getKillObjective();
        if (scoreboard.getObjective(killObjective) == null) {
          scoreboard.addObjective(
              killObjective,
              ObjectiveCriteria.DUMMY,
              Component.literal(config.getKillDisplay()),
              ObjectiveCriteria.RenderType.INTEGER,
              false,
              null);
          if (config.isDebugEnabled()) {
            LOGGER.info("Created kill scoreboard objective: {}", killObjective);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to setup scoreboard: {}", e.getMessage());
    }
  }

  public void trackAssault(ServerPlayer player, CowConfig config) {
    if (!config.isScoreboardEnabled() || !config.isTrackAssaultsEnabled()) return;

    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();
      Objective objective = scoreboard.getObjective(config.getAssaultObjective());

      if (objective != null) {
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly(player.getName().getString());
        int currentScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective).get();
        scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(currentScore + 1);

        if (config.isDebugEnabled()) {
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
      Objective objective = scoreboard.getObjective(config.getKillObjective());

      if (objective != null) {
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly(player.getName().getString());
        int currentScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective).get();
        scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(currentScore + 1);

        if (config.isDebugEnabled()) {
          LOGGER.info(
              "Tracked kill for {}, new score: {}", player.getName().getString(), currentScore + 1);
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to track kill for {}: {}", player.getName().getString(), e.getMessage());
    }
  }
}
