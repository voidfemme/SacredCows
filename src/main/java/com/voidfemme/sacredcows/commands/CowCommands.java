package com.voidfemme.sacredcows.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import com.voidfemme.sacredcows.config.settings.BoolSetting;
import com.voidfemme.sacredcows.config.settings.Setting;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowCommands {
  private static final Logger LOGGER = LoggerFactory.getLogger(CowCommands.class.getName());
  private final SacredCows owner;
  private final CowConfig config;

  public CowCommands(SacredCows owner, CowConfig config) {
    this.owner = owner;
    this.config = config;
  }

  private static SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS =
      (context, builder) ->
          SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder);

  private static SuggestionProvider<CommandSourceStack> buildSuggestions(
      Collection<String> suggestions) {
    return (context, builder) -> {
      for (String s : suggestions) builder.suggest(s);
      return builder.buildFuture();
    };
  }

  public void register() {
    LOGGER.info("Registering /sacredcows command...");
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(
              Commands.literal("sacredcows")
                  .executes(this::executeHelp)
                  .then(Commands.literal("help").executes(this::executeHelp))
                  .then(buildToggle())
                  .then(buildEnable())
                  .then(buildDisable())
                  .then(buildSet())
                  .then(buildGet())
                  .then(buildStats())
                  .then(buildConfig()));
        });
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildToggle() {
    return Commands.literal("toggle")
        .requires(source -> checkPermission(this.config, source))
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests(buildSuggestions(config.boolNames()))
                .executes(this::executeToggleSetting))
        .executes(usage("/sacredcows toggle <setting>"));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildEnable() {
    return Commands.literal("enable")
        .requires(source -> checkPermission(this.config, source))
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests(buildSuggestions(config.boolNames()))
                .executes(this::executeEnableSetting))
        .executes(usage("/sacredcows enable <setting>"));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildDisable() {
    return Commands.literal("disable")
        .requires(source -> checkPermission(this.config, source))
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests(buildSuggestions(config.boolNames()))
                .executes(this::executeDisableSetting))
        .executes(usage("/sacredcows disable <setting>"));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildSet() {
    return Commands.literal("set")
        .requires(source -> checkPermission(this.config, source))
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests(buildSuggestions(config.allNames()))
                .then(
                    Commands.argument("value", StringArgumentType.word())
                        .executes(this::executeSetSetting))
                .executes(usage("/sacredcows set <setting> <value>")))
        .executes(usage("/sacredcows set <setting> <value>"));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildGet() {
    return Commands.literal("get")
        .then(
            Commands.argument("name", StringArgumentType.word())
                .suggests(buildSuggestions(config.allNames()))
                .executes(this::executeGetSetting))
        .executes(usage("/sacredcows get <setting>"));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildStats() {
    return Commands.literal("stats")
        .then(
            Commands.literal("player")
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests(PLAYER_NAME_SUGGESTIONS)
                        .executes(this::executeGetPlayerStats))
                .executes(usage("/sacredcows stats player <name>")))
        .then(Commands.literal("global").executes(this::executeGetGlobalStats))
        .executes(usage("/sacredcows stats <player <name>|global>"));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildConfig() {
    return Commands.literal("config")
        .requires(source -> checkPermission(this.config, source))
        .then(Commands.literal("print").executes(this::executePrintConfig))
        .then(Commands.literal("save").executes(this::executeSaveConfig))
        .then(Commands.literal("reload").executes(this::executeReloadConfig))
        .executes(usage("/sacredcows config <print|save|reload>"));
  }

  private static Command<CommandSourceStack> usage(String message) {
    return ctx -> {
      ctx.getSource().sendFailure(Component.literal("Usage: " + message));
      return 0;
    };
  }

  private void successMessage(
      CommandSourceStack source,
      MutableComponent mutableComponent,
      String message,
      Setting setting) {
    mutableComponent.append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    mutableComponent.append(setting.status());
    source.sendSuccess(() -> mutableComponent, false);
  }

  private int executeToggleSetting(CommandContext<CommandSourceStack> ctx) {
    String name = StringArgumentType.getString(ctx, "name");
    Setting setting = config.find(name).orElse(null);
    if (!(setting instanceof BoolSetting b)) {
      ctx.getSource().sendFailure(Component.literal("Not a toggleable setting: " + name));
      return 0;
    }

    String setUnset;
    if (b.get()) {
      setUnset = "Unset ";
    } else {
      setUnset = "Set ";
    }

    b.set(!b.get());

    successMessage(ctx.getSource(), Component.empty(), setUnset + name + ": ", b);
    return 1;
  }

  private int executeEnableSetting(CommandContext<CommandSourceStack> ctx) {
    String name = StringArgumentType.getString(ctx, "name"); // Which setting are we enabling?
    Setting setting = config.find(name).orElse(null);
    if (!(setting instanceof BoolSetting b)) {
      ctx.getSource().sendFailure(Component.literal("Not a toggleable setting: " + name));
      return 0;
    }
    b.set(true);
    successMessage(ctx.getSource(), Component.empty(), "Set " + name + ": ", setting);
    return 1;
  }

  private int executeDisableSetting(CommandContext<CommandSourceStack> ctx) {
    String name = StringArgumentType.getString(ctx, "name"); // Which setting are we enabling?
    Setting setting = config.find(name).orElse(null);
    if (!(setting instanceof BoolSetting b)) {
      ctx.getSource().sendFailure(Component.literal("Not a toggleable setting: " + name));
      return 0;
    }
    b.set(false);
    successMessage(ctx.getSource(), Component.empty(), "Unset " + name + ": ", b);
    return 1;
  }

  private int executeSetSetting(CommandContext<CommandSourceStack> ctx) {
    String name = StringArgumentType.getString(ctx, "name");
    String value = StringArgumentType.getString(ctx, "value");
    Setting setting = config.find(name).orElse(null);
    if (setting == null) {
      ctx.getSource().sendFailure(Component.literal("Unknown setting: " + name));
      return 0;
    }
    if (!setting.tryDeserialize(value)) {
      ctx.getSource()
          .sendFailure(Component.literal(value + " is an invalid value for " + setting.name()));
      return 0;
    }
    successMessage(ctx.getSource(), Component.empty(), "Set " + name + ": ", setting);
    return 1;
  }

  private int executeGetSetting(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    String name = StringArgumentType.getString(ctx, "name");

    Setting setting = config.find(name).orElse(null);
    if (setting == null) {
      source.sendFailure(Component.literal("Unknown setting: " + name));
      return 0;
    }
    successMessage(ctx.getSource(), Component.empty(), name + ": " + setting.serialize(), setting);
    return 1;
  }

  private int executeGetGlobalStats(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();
      source.sendSuccess(
          () -> Component.literal("=== Global Cow Stats ===").withStyle(ChatFormatting.GOLD),
          false);
      if (this.config.trackAssaults.get()) {
        Objective assaults = scoreboard.getObjective(this.config.assaultObjective.get());
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
      if (this.config.trackKills.get()) {
        Objective kills = scoreboard.getObjective(this.config.killObjective.get());
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

  private int executeGetPlayerStats(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    String playerName = StringArgumentType.getString(ctx, "name");
    try {
      Scoreboard scoreboard = owner.getServer().getScoreboard();
      source.sendSuccess(
          () ->
              Component.literal("=== Cow Stats for " + playerName + " ===")
                  .withStyle(ChatFormatting.GOLD),
          false);
      if (this.config.trackAssaults.get()) {
        Objective assaults = scoreboard.getObjective(this.config.assaultObjective.get());
        if (assaults != null) {
          ScoreHolder scoreHolder = ScoreHolder.forNameOnly(playerName);
          int assaultScore = scoreboard.getOrCreatePlayerScore(scoreHolder, assaults).get();
          successMessage(
              source,
              Component.empty(),
              "Cow assaults: " + assaultScore,
              this.config.trackAssaults);
        }
      }
      if (this.config.trackKills.get()) {
        Objective kills = scoreboard.getObjective(this.config.killObjective.get());
        if (kills != null) {
          ScoreHolder scoreHolder = ScoreHolder.forNameOnly(playerName);
          int killScore = scoreboard.getOrCreatePlayerScore(scoreHolder, kills).get();
          successMessage(
              source, Component.empty(), "Cow kills: " + killScore, this.config.trackKills);
        }
      }
    } catch (Exception e) {
      source.sendFailure(Component.literal("Error retrieving stats: " + e.getMessage()));
      LOGGER.warn("Error showing stats for {}: {}", playerName, e.getMessage());
    }
    return 1;
  }

  private static boolean checkPermission(CowConfig config, CommandSourceStack source) {
    return Permissions.check(source, config.adminPermission.get(), config.adminOpLevel.get());
  }

  // == COMMAND IMPLEMENTATIONS ==

  /** Prints help text listing all available subcommands. */
  private int executeHelp(CommandContext<CommandSourceStack> ctx) {
    if (checkPermission(config, ctx.getSource())) {
      ctx.getSource()
          .sendSuccess(
              () ->
                  Component.literal(
                      "§6=== Sacred Cows Commands ===\n"
                          + "§e/sacredcows help §r- Show this message\n"
                          + "§e/sacredcows toggle <setting>\n"
                          + "§e/sacredcows enable <setting>\n"
                          + "§e/sacredcows disable <setting>\n"
                          + "§e/sacredcows set <setting> <value>\n"
                          + "§e/sacredcows get <setting>\n"
                          + "§e/sacredcows stats <player <name>|global>\n"
                          + "§e/sacredcows config <print|save|reload>"),
              false);
    } else {
      ctx.getSource()
          .sendSuccess(
              () ->
                  Component.literal(
                      "§6=== Sacred Cows Commands ===\n"
                          + "§e/sacredcows help §r- Show this message\n"
                          + "§e/sacredcows stats <player <name>> | global> §r- See stats for a"
                          + " player or for the whole world"),
              false);
    }

    return 1;
  }

  private Component changed(String current, String saved) {
    if (saved == null || current.equals(saved)) return Component.literal("");
    return Component.literal(" <- (changed)").withStyle(ChatFormatting.YELLOW);
  }

  private static String prettify(String name) {
    return Arrays.stream(name.split("_"))
        .filter(w -> !w.isEmpty())
        .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
        .collect(Collectors.joining(" "));
  }

  private int executePrintConfig(CommandContext<CommandSourceStack> ctx) {
    Properties properties = config.getSavedConfig();
    MutableComponent configMessage = Component.empty();

    // == Header ==
    configMessage.append(
        Component.literal("== Current Configuration Status ==").withStyle(ChatFormatting.GOLD));

    for (Setting s : config.allSettings()) {
      configMessage.append(
          Component.literal("\n" + prettify(s.name()) + ": ").withStyle(ChatFormatting.GRAY));
      configMessage.append(s.status());
      configMessage.append(changed(s.serialize(), properties.getProperty(s.serializationKey())));
    }

    // == String values ==
    List<String> deathMessages = config.getDeathMessages();
    String formattedDeathMessages = String.join("\n   ", deathMessages);

    // Saved Death Messages
    configMessage.append(
        Component.literal("\nSaved Death Messages: \n").withStyle(ChatFormatting.GRAY));
    configMessage.append("   " + formattedDeathMessages);
    configMessage.append(
        Component.literal(
                "\n(You can only edit the list of death messages manually in the config file.)")
            .withStyle(ChatFormatting.YELLOW));

    ctx.getSource().sendSuccess(() -> configMessage, false);
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
}
