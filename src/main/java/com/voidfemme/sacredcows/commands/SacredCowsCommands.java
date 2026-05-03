package com.voidfemme.sacredcows.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.SacredCowsConfig;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
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
        builder.suggest("lightning_only");
        return builder.buildFuture();
      };
  private static final SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS =
      (context, builder) ->
          SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder);

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
              Commands.literal("sacredcows").executes(SacredCowsCommands::executeHelp);
          root.then(Commands.literal("help").executes(SacredCowsCommands::executeHelp));
          addBoolCommand(
              root,
              "reload_config",
              this::executeReloadConfig,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root, "bypass", this::executeBypass, source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "enabled",
              this::executeEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "lightning_effect",
              this::executeLightningEffect,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root, "debug", this::enableDebugMode, source -> checkPermission(this.config, source));
          addEnumCommand(
              root,
              "punishment_type",
              PUNISHMENT_TYPE_SUGGESTIONS,
              this::executePunishmentType,
              source -> checkPermission(this.config, source));
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
                                        Component.literal(
                                            "Usage: /sacredcows stats player <name>"));
                                return 0;
                              }))
                  .then(Commands.literal("global").executes(this::executeStatsGlobal))
                  .executes(
                      ctx -> {
                        ctx.getSource()
                            .sendFailure(
                                Component.literal("Usage: /sacredcows stats <player|global>"));
                        return 0;
                      }));
          root.then(Commands.literal("print_config").executes(this::printConfig));
          root.then(
              Commands.literal("save_config")
                  .requires(source -> checkPermission(this.config, source))
                  .executes(this::executeSaveConfig));

          dispatcher.register(root);
        });
  }

  private static boolean checkPermission(SacredCowsConfig config, CommandSourceStack source) {
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
                        + "§e/sacredcows debug <true|false> §r- Toggle debug mode.\n"
                        + "§e/sacredcows lightning_effect §r- Toggle lightning effect\n"
                        + "§e/sacredcows punishment_type <death|damage|lightning_only> §r- Set"
                        + " punishment type\n"
                        + "§e/sacredcows stats <player <name>> | global> §r- See stats for a player"
                        + " or for the whole world"),
            false);
    return 1;
  }

  private Component status(boolean value) {
    return value
        ? Component.literal("enabled").withStyle(ChatFormatting.GREEN)
        : Component.literal("disabled").withStyle(ChatFormatting.RED);
  }

  private <T> Component changed(T current, String saved) {
    if (saved == null) return Component.literal("");

    boolean equal;
    if (current instanceof Boolean b) {
      equal = b == Boolean.parseBoolean((String) saved);
    } else if (current instanceof Double d) {
      equal = d == Double.parseDouble((String) saved);
    } else if (current instanceof Integer i) {
      equal = i == Integer.parseInt((String) saved);
    } else {
      equal = current.equals(saved);
    }
    return equal
        ? Component.literal("")
        : Component.literal(" <- (changed)").withStyle(ChatFormatting.YELLOW);
  }

  private int printConfig(CommandContext<CommandSourceStack> ctx) {
    Properties properties = config.getSavedConfig();
    MutableComponent configMessage = Component.empty();

    // == Header ==
    configMessage.append(
        Component.literal("== Current Configuration Status ==").withStyle(ChatFormatting.GOLD));

    // == Booleans ==
    // Mod status
    configMessage.append(Component.literal("\nMod status: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isEnabled()));
    configMessage.append(changed(config.isEnabled(), properties.getProperty("settings.enabled")));

    // Debug status
    configMessage.append(Component.literal("\nDebug: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isDebugEnabled()));
    configMessage.append(
        changed(config.isDebugEnabled(), properties.getProperty("settings.debug")));

    // Lightning status
    configMessage.append(Component.literal("\nLightning: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isLightningEffectEnabled()));
    configMessage.append(
        changed(
            config.isLightningEffectEnabled(),
            properties.getProperty("settings.lightning-effect")));

    // Custom death message status
    configMessage.append(
        Component.literal("\nCustom Death Messages: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isCustomDeathMessagesEnabled()));
    configMessage.append(
        changed(
            config.isCustomDeathMessagesEnabled(),
            properties.getProperty("settings.custom-death-messages")));

    // Scoreboard status
    configMessage.append(Component.literal("\nScoreboard: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isScoreboardEnabled()));
    configMessage.append(
        changed(config.isScoreboardEnabled(), properties.getProperty("scoreboard.enabled")));

    // Assault Tracking Status
    configMessage.append(Component.literal("\nAssault Tracking: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isTrackAssaultsEnabled()));
    configMessage.append(
        changed(
            config.isTrackAssaultsEnabled(), properties.getProperty("scoreboard.track-assaults")));

    // Kill Tracking Status
    configMessage.append(Component.literal("\nKill Tracking: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isTrackKillsEnabled()));
    configMessage.append(
        changed(config.isTrackKillsEnabled(), properties.getProperty("scoreboard.track-kills")));

    // Bypass enable status
    configMessage.append(Component.literal("\nBypass punishment: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isAllowBypass()));
    configMessage.append(
        changed(config.isAllowBypass(), properties.getProperty("settings.allow-bypass")));

    // == String values ==
    List<String> deathMessages = config.getDeathMessages();
    String formattedDeathMessages = String.join("\n   ", deathMessages);

    // Punishment Type
    configMessage.append(Component.literal("\nPunishment Type: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getPunishmentType());
    configMessage.append(
        changed(config.getPunishmentType(), properties.getProperty("settings.punishment-type")));

    // Assault Objective
    configMessage.append(Component.literal("\nAssault Objective: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getAssaultObjective());
    configMessage.append(
        changed(
            config.getAssaultObjective(), properties.getProperty("scoreboard.assault-objective")));

    // Kill Objective
    configMessage.append(Component.literal("\nKill Objective: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getKillObjective());
    configMessage.append(
        changed(config.getKillObjective(), properties.getProperty("scoreboard.kill-objective")));

    // Assault Display
    configMessage.append(Component.literal("\nAssault Display: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getAssaultDisplay());
    configMessage.append(
        changed(config.getAssaultDisplay(), properties.getProperty("scoreboard.assault-display")));

    // Kill Display
    configMessage.append(Component.literal("\nKill Display: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getKillDisplay());
    configMessage.append(
        changed(config.getKillDisplay(), properties.getProperty("scoreboard.kill-display")));

    // Bypass Permission
    configMessage.append(Component.literal("\nBypass Permission: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getBypassPermission());
    configMessage.append(
        changed(
            config.getBypassPermission(), properties.getProperty("permissions.bypass-permission")));

    // Admin Permission
    configMessage.append(Component.literal("\nAdmin Permission: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getAdminPermission());
    configMessage.append(
        changed(
            config.getAdminPermission(), properties.getProperty("permissions.admin-permission")));

    // Saved Death Messages
    configMessage.append(
        Component.literal("\nSaved Death Messages: \n").withStyle(ChatFormatting.GRAY));
    configMessage.append("   " + formattedDeathMessages);
    configMessage.append(
        Component.literal(
                "\n(You can only edit the list of death messages manually in the config file.)")
            .withStyle(ChatFormatting.YELLOW));

    // == Other values ==
    configMessage.append(Component.literal("\nDamage Amount: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(Component.literal(String.valueOf(config.getDamageAmount())));
    configMessage.append(
        changed(config.getDamageAmount(), properties.getProperty("settings.damage-amount")));

    configMessage.append(Component.literal("\nBypass OP Level: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(Component.literal(String.valueOf(config.getBypassOpLevel())));
    configMessage.append(
        changed(config.getBypassOpLevel(), properties.getProperty("settings.bypass-op-level")));

    configMessage.append(Component.literal("\nAdmin OP Level: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(Component.literal(String.valueOf(config.getAdminOpLevel())));
    configMessage.append(
        changed(config.getAdminOpLevel(), properties.getProperty("settings.admin-op-level")));

    ctx.getSource().sendSuccess(() -> configMessage, false);
    return 1;
  }

  private void displaySaveConfigMessage(CommandSourceStack source) {
    source.sendSuccess(
        () ->
            Component.literal(
                "To save your current configuration to file, run '/sacredcows save_config'"),
        false);
  }

  private int enableDebugMode(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setDebugEnabled(value);
    String enabled = "";
    if (value) {
      enabled = "enabled";
    } else {
      enabled = "disabled";
    }
    String message = "debug mode " + enabled;
    source.sendSuccess(() -> Component.literal(message), true);
    return 1;
  }

  private int executeSaveConfig(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    try {
      config.save();
      source.sendSuccess(
          () -> Component.literal("Sacred Cows configuration saved successfully!"), false);
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
      owner.getScoreboard().setupScoreboard();
      source.sendSuccess(
          () ->
              Component.literal("SacredCows configuration reloaded successfully!")
                  .withStyle(ChatFormatting.GREEN),
          false);
      return 1;
    } catch (Exception e) {
      source.sendFailure(Component.literal("§cFailed to reload configuration: " + e.getMessage()));
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
    if (!value.equals("death") && !value.equals("damage") && !value.equals("lightning_only")) {
      ctx.getSource()
          .sendFailure(
              Component.literal(
                  "Invalid punishment type: "
                      + value
                      + ". Must be one of: death, damage, lightning_only"));
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
              () -> Component.literal("Cow Kills: " + killScore).withStyle(ChatFormatting.YELLOW),
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
          () -> Component.literal("=== Global Cow Stats ===").withStyle(ChatFormatting.GOLD),
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
              scoreboard.listPlayerScores(kills).stream().mapToInt(PlayerScoreEntry::value).sum();
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
