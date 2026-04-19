package com.voidfemme.sacredcows;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.commands.SharedSuggestionProvider;

import java.io.IOException;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SacredCowsCommands {
  public static final String MOD_ID = "sacredcows.SacredCowsCommands";
  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private final SacredCows owner;
  private final SacredCowsConfig config;

  public SacredCowsCommands(SacredCows owner, SacredCowsConfig config) {
    this.owner = owner;
    this.config = config;
  }

  // Tab-completion suggestions for the example subcommand argument.
  // Replace or expand the list below with whatever values make sense for your command.
  private static final SuggestionProvider<CommandSourceStack> PUNISHMENT_TYPE_SUGGESTIONS =
      (context, builder) -> {
        builder.suggest("death");
        builder.suggest("damage");
        builder.suggest("lightning_effect");
        return builder.buildFuture();
      };
  private static final SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS = 
      (context, builder) -> 
        SharedSuggestionProvider.suggest(
                context.getSource().getOnlinePlayerNames(), builder);

  // == REGISTRATION ==
  // To add a new subcommand:
  //    1. Write an executeFoo(...) method at the bottom.
  //    2. Add one line here using the appropriate helper:
  //            addBoolCommand(root, "foo", SacredCowsCommands::executeFoo);
  //        or
  //            addEnumCommand(root, "foo", FOO_SUGGESTIONS, SacredCowsCommands::executeFoo);
  //    3. Add a line in executeHelp describing it.
  public void register() {
    LOGGER.info("Registering /sacredcows command...");
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          LiteralArgumentBuilder<CommandSourceStack> root =
              Commands.literal("sacredcows")
                  .executes(SacredCowsCommands::executeHelp);
          root.then(Commands.literal("help").executes(SacredCowsCommands::executeHelp));
          addBoolCommand(
            root, 
            "reload_config", 
            this::executeReloadConfig, 
            source -> checkPermission(this.config, source)
          );
          addBoolCommand(
            root, 
            "bypass", 
            this::executeBypass, 
            source -> checkPermission(this.config, source)
          );
          addBoolCommand(
            root, 
            "enabled", 
            this::executeEnabled, 
            source -> checkPermission(this.config, source)
          );
          addBoolCommand(
            root, 
            "lightning_effect", 
            this::executeLightningEffect, 
            source -> checkPermission(this.config, source)
          );
          addEnumCommand(
              root, 
              "punishment_type", 
              PUNISHMENT_TYPE_SUGGESTIONS, 
              this::executePunishmentType, 
              source -> checkPermission(this.config, source)
          );
          root.then(
            Commands.literal("stats")
                .then(
                    Commands.literal("player")
                        .then(
                            Commands.argument("name", StringArgumentType.word())
                            .suggests(PLAYER_NAME_SUGGESTIONS)
                            .executes(this::executeStatsPlayer))
                        .executes(
                            ctx -> {
                                ctx.getSource()
                                .sendFailure(
                                    Component.literal("Usage: /sacredcows stats player <name>"));
                                return 0;
                            }
                        )
                )
                .then(Commands.literal("global").executes(this::executeStatsGlobal))
                .executes(
                    ctx -> {
                        ctx.getSource()
                            .sendFailure(
                                Component.literal("Usage: /sacredcows stats <player|global>"));
                            return 0;
                    }
                )
          );
          root.then(
            Commands.literal("save_config")
                .requires(source -> checkPermission(this.config, source))
                .executes(this::executeSaveConfig));

          dispatcher.register(root);
        });
  }

    private static boolean checkPermission(
        SacredCowsConfig config, CommandSourceStack source) {
        if (source.permissions() instanceof LevelBasedPermissionSet leveled) {
            PermissionLevel sourceLevel = leveled.level();
            PermissionLevel requiredLevel = PermissionLevel.byId(config.getAdminOpLevel());
            return sourceLevel.isEqualOrHigherThan(requiredLevel);
        }
        return false;
    }

  // == REGISTRATION HELPERS ==

  /** /sacredcows <name> <true|false> - auto-suggests true/false. */
  private static void addBoolCommand(
      LiteralArgumentBuilder<CommandSourceStack> root,
      String name,
      Command<CommandSourceStack> executor,
      Predicate<CommandSourceStack> permission) {
    root.then(
        Commands.literal(name)
            .requires(permission) // op-level
            .then(Commands.argument("value", BoolArgumentType.bool()).executes(executor))
            .executes(
                ctx -> {
                  ctx.getSource()
                      .sendFailure(
                          Component.literal("Usage: /sacredcows " + name + " <true|false>"));
                  return 0;
                }));
  }

  /** /sacredcows <name> <value> where value is tab-completed from the provider. */
  private static void addEnumCommand(
      LiteralArgumentBuilder<CommandSourceStack> root,
      String name,
      SuggestionProvider<CommandSourceStack> suggestions,
      Command<CommandSourceStack> executor,
      Predicate<CommandSourceStack> permission) {
    root.then(
        Commands.literal(name)
            .requires(permission)
            .then(
                Commands.argument("value", StringArgumentType.word())
                    .suggests(suggestions)
                    .executes(executor))
            .executes(
                ctx -> {
                  ctx.getSource()
                      .sendFailure(Component.literal("Usage: /sacredcows " + name + " <value>"));
                  return 0;
                }));
  }

  // == COMMAND IMPLEMENTATIONS ==

  /** Prints help text listing all available subcommands. */
  private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
    ctx.getSource()
        .sendSuccess(
            () ->
                Component.literal(
                    "§6=== Sacred Cows Commands ===\n"
                        + "§e/sacredcows help §r- Show this message\n"
                        + "§e/sacredcows bypass <true|false> §r- Toggle admin bypass\n"
                        + "§e/sacredcows enabled <true|false> §r- Toggle the mod\n"
                        + "§e/sacredcows lightning_effect §r- Toggle lightning effect\n"
                        + "§e/sacredcows punishment_type <death|damage|lightning_effect> §r- Set"
                        + " punishment type\n"
                        + "§e/sacredcows stats <player <name>> | global> §r- See stats for a player or for the whole world"),
            false);
    return 1;
  }

  private void displaySaveConfigMessage(CommandSourceStack source) {
      source.sendSuccess(() -> Component.literal("To save your current configuration to file, run '/sacredcows save_config'"), false);
  }

  private int executeSaveConfig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            config.save();
            source.sendSuccess(() -> Component.literal("Sacred Cows configuration saved successfully!"), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(
                    Component.literal("Sacred Cows Configuration failed to save: " + e.getMessage()));
        }
        return 0;
  }

  private int executeReloadConfig(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    try {
      owner.loadConfig();
      owner.setupScoreboard();
      source.sendSuccess(
          () ->
              Component.literal("SacredCows configuration reloaded successfully!")
                  .withStyle(ChatFormatting.GREEN),
          false);
      return 1;
    } catch (Exception e) {
      source.sendFailure(
          Component.literal("§cFailed to reload configuration: " + e.getMessage()));
      LOGGER.warn("Failed to reload config: {}", e.getMessage());
      return 0;
    }
  }

  private int executeBypass(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setAllowBypass(value);
    source.sendSuccess(() -> Component.literal("bypass set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

  private int executeEnabled(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setEnabled(value);
    source.sendSuccess(() -> Component.literal("enabled set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

  private int executeLightningEffect(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setLightningEffectEnabled(value);
    source.sendSuccess(() -> Component.literal("lightning_effect set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

  private int executePunishmentType(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
    String value = StringArgumentType.getString(ctx, "value");
    // Suggestions don't enforce - validate here.
    if (!value.equals("death") && !value.equals("damage") && !value.equals("lightning_effect")) {
      ctx.getSource()
          .sendFailure(
              Component.literal(
                  "Invalid punishment type: "
                      + value
                      + ". Must be one of: death, damage, lightning_effect"));
      return 0;
    }
    this.config.setPunishmentType(value);
    source.sendSuccess(() -> Component.literal("punishment_type set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

    private int executeStatsPlayer(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      String playerName = StringArgumentType.getString(ctx, "name");
      try {
        Scoreboard scoreboard = owner.getServer().getScoreboard();
        source.sendSuccess(
            () ->
                Component.literal("=== Cow Stats for " + playerName + " ===")
                    .withStyle(ChatFormatting.GOLD),
            false);
        if (this.config.isTrackAssaultsEnabled()) {
          Objective assaults = scoreboard.getObjective(this.config.getAssaultObjective());
          if (assaults != null) {
            ScoreHolder scoreHolder = ScoreHolder.forNameOnly(playerName);
            int assaultScore = scoreboard.getOrCreatePlayerScore(scoreHolder, assaults).get();
            source.sendSuccess(
                () ->
                    Component.literal("Cow Assaults: " + assaultScore)
                        .withStyle(ChatFormatting.YELLOW),
                false);
          }
        }
        if (this.config.isTrackKillsEnabled()) {
          Objective kills = scoreboard.getObjective(this.config.getKillObjective());
          if (kills != null) {
            ScoreHolder scoreHolder = ScoreHolder.forNameOnly(playerName);
            int killScore = scoreboard.getOrCreatePlayerScore(scoreHolder, kills).get();
            source.sendSuccess(
                () ->
                    Component.literal("Cow Kills: " + killScore)
                        .withStyle(ChatFormatting.YELLOW),
                false);
          }
        }
      } catch (Exception e) {
        source.sendFailure(Component.literal("Error retrieving stats: " + e.getMessage()));
        LOGGER.warn("Error showing stats for {}: {}", playerName, e.getMessage());
      }
      return 1;
    }
    
    private int executeStatsGlobal(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      try {
        Scoreboard scoreboard = owner.getServer().getScoreboard();
        source.sendSuccess(
            () ->
                Component.literal("=== Global Cow Stats ===").withStyle(ChatFormatting.GOLD),
            false);
        if (this.config.isTrackAssaultsEnabled()) {
          Objective assaults = scoreboard.getObjective(this.config.getAssaultObjective());
          if (assaults != null) {
            int cowAssaultsTotal =
                scoreboard.listPlayerScores(assaults).stream()
                    .mapToInt(PlayerScoreEntry::value)
                    .sum();
            source.sendSuccess(
                () ->
                    Component.literal("Total Cow Assaults: " + cowAssaultsTotal)
                        .withStyle(ChatFormatting.YELLOW),
                false);
          }
        }
        if (this.config.isTrackKillsEnabled()) {
          Objective kills = scoreboard.getObjective(this.config.getKillObjective());
          if (kills != null) {
            int killScoreTotal =
                scoreboard.listPlayerScores(kills).stream()
                    .mapToInt(PlayerScoreEntry::value)
                    .sum();
            source.sendSuccess(
                () ->
                    Component.literal("Total Cow Kills: " + killScoreTotal)
                        .withStyle(ChatFormatting.YELLOW),
                false);
          }
        }
      } catch (Exception e) {
        source.sendFailure(Component.literal("Error retrieving stats: " + e.getMessage()));
        LOGGER.warn("Error showing global stats: {}", e.getMessage());
      }
      return 1;
    }
}
