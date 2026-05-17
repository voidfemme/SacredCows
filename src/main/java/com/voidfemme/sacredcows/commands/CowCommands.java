package com.voidfemme.sacredcows.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.voidfemme.sacredcows.SacredCows;
import com.voidfemme.sacredcows.config.CowConfig;
import com.voidfemme.sacredcows.config.SettingsEnum;
import com.voidfemme.sacredcows.features.CowProtectionFeature.PunishmentMode;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
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

  // Tab-completion suggestions for the example subcommand argument.
  // Replace or expand the list below with whatever values make sense for your command.
  private static final SuggestionProvider<CommandSourceStack> PUNISHMENT_MODE_SUGGESTIONS =
      (context, builder) -> {
        for (PunishmentMode mode : PunishmentMode.values()) {
          builder.suggest(mode.toString());
        }
        return builder.buildFuture();
      };

  private static final SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS =
      (context, builder) ->
          SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder);

  // == REGISTRATION ==
  // To add a new subcommand:
  //    1. Write an executeFoo(...) method at the bottom.
  //    2. Add one line here using the appropriate helper:
  //            addBoolCommand(root, "foo", CowCommands::executeFoo);
  //        or
  //            addEnumCommand(root, "foo", FOO_SUGGESTIONS, CowCommands::executeFoo);
  //    3. Add a line in executeHelp describing it.
  public void register() {
    LOGGER.info("Registering /sacredcows command...");
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          LiteralArgumentBuilder<CommandSourceStack> root =
              Commands.literal("sacredcows").executes(this::executeHelp);
          root.then(Commands.literal("help").executes(this::executeHelp));
          addBoolCommand(
              root,
              "bypass_enabled",
              this::executeBypassEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "mod_enabled",
              this::executeModEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "lightning_effect_enabled",
              this::executeLightningEffectEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "debug_enabled",
              this::enableDebugMode,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "teleport_enabled",
              this::enableTeleport,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "cow_invincibility_enabled",
              this::executeCowInvincibilityEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "scoreboard_enabled",
              this::executeScoreboardEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "track_assaults_enabled",
              this::executeTrackAssaultsEnabled,
              source -> checkPermission(this.config, source));
          addBoolCommand(
              root,
              "track_kills_enabled",
              this::executeTrackKillsEnabled,
              source -> checkPermission(this.config, source));
          addIntegerCommand(
              root,
              "set_bypass_level",
              this::executeSetBypassLevel,
              source -> checkPermission(this.config, source));
          addIntegerCommand(
              root,
              "set_admin_level",
              this::executeSetAdminLevel,
              source -> checkPermission(this.config, source));

          // TODO: Be able to change all cows' maximum health
          // addDoubleCommand(
          //     root,
          //     "cow_health",
          //     this::executeSetCowHealth,
          //     source -> checkPermission(this.config, source));
          addDoubleCommand(
              root,
              "set_player_damage_amount",
              this::executeSetPlayerDamageAmount,
              source -> checkPermission(this.config, source));
          addEnumCommand(
              root,
              "punishment_mode",
              PUNISHMENT_MODE_SUGGESTIONS,
              this::executeSetPunishmentMode,
              source -> checkPermission(this.config, source));
          root.then(
              Commands.literal("stats")
                  .then(
                      Commands.literal("player")
                          .then(
                              Commands.argument("name", StringArgumentType.word())
                                  .suggests(PLAYER_NAME_SUGGESTIONS)
                                  .executes(this::executePrintPlayerStats))
                          .executes(
                              ctx -> {
                                ctx.getSource()
                                    .sendFailure(
                                        Component.literal(
                                            "Usage: /sacredcows stats player <name>"));
                                return 0;
                              }))
                  .then(Commands.literal("global").executes(this::executePrintGlobalStats))
                  .executes(
                      ctx -> {
                        ctx.getSource()
                            .sendFailure(
                                Component.literal("Usage: /sacredcows stats <player|global>"));
                        return 0;
                      }));
          root.then(
              Commands.literal("reload_config")
                  .requires(source -> checkPermission(this.config, source))
                  .executes(this::executeReloadConfig));
          root.then(
              Commands.literal("print_config")
                  .requires(source -> checkPermission(this.config, source))
                  .executes(this::printConfig));
          root.then(
              Commands.literal("save_config")
                  .requires(source -> checkPermission(this.config, source))
                  .executes(this::executeSaveConfig));

          dispatcher.register(root);
        });
  }

  private static boolean checkPermission(CowConfig config, CommandSourceStack source) {
    return Permissions.check(source, config.getAdminPermission(), config.getAdminOpLevel());
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

  private static void addDoubleCommand(
      LiteralArgumentBuilder<CommandSourceStack> root,
      String name,
      Command<CommandSourceStack> executor,
      Predicate<CommandSourceStack> permission) {
    root.then(
        Commands.literal(name)
            .requires(permission)
            .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(executor))
            .executes(
                ctx -> {
                  ctx.getSource()
                      .sendFailure(Component.literal("Usage: /sacredcows " + name + " <value>"));
                  return 0;
                }));
  }

  private static void addIntegerCommand(
      LiteralArgumentBuilder<CommandSourceStack> root,
      String name,
      Command<CommandSourceStack> executor,
      Predicate<CommandSourceStack> permission) {
    root.then(
        Commands.literal(name)
            .requires(permission)
            .then(Commands.argument("value", IntegerArgumentType.integer()).executes(executor))
            .executes(
                ctx -> {
                  ctx.getSource()
                      .sendFailure(Component.literal("Usage: /sacredcows " + name + " <value>"));
                  return 0;
                }));
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
                          + "§e/sacredcows mod_enabled <true|false> §r- Toggle the mod\n"
                          + "§e/sacredcows help §r- Show this message\n"
                          + "§e/sacredcows reload_config §r- Reload the configuration from file\n"
                          + "§e/sacredcows save_config §r- Save the configuration to file\n"
                          + "§e/sacredcows print_config §r- Print the currently active config.\n"
                          + "§e/sacredcows bypass_enabled <true|false> §r- Toggle admin bypass\n"
                          + "§e/sacredcows debug_enabled <true|false> §r- Toggle debug mode.\n"
                          + "§e/sacredcows lightning_effect_enabled <true|false> §r- Toggle"
                          + " lightning effect\n"
                          + "§e/sacredcows teleport_enabled <true|false> §r- Toggle milk"
                          + " teleportation.\n"
                          + "§e/sacredcows cow_invincibility_enabled <true|false> §r- Toggle cow"
                          + " invincibility.\n"
                          // + "§e/sacredcows cow_health <double> §r- Set max cow health.\n"
                          + "§e/sacredcows scoreboard_enabled <true|false> §r- Toggle the"
                          + " scoreboard.\n"
                          + "§e/sacredcows track_assaults_enabled <true|false> §r- Toggle tracking"
                          + " of cow assaults\n"
                          + "§e/sacredcows track_kills_enabled <true|false> §r- Toggle tracking of"
                          + " cow kills\n"
                          + "§e/sacredcows set_bypass_level <int> §r- Set bypass permission"
                          + " level.\n"
                          + "§e/sacredcows set_admin_level <int> §r- Set admin permission level.\n"
                          + "§e/sacredcows set_player_damage_amount <double> §r- Set damage level"
                          + " of 'damage' punishment mode.\n"
                          + "§e/sacredcows punishment_mode <death|damage|lightning_only> §r- Set"
                          + " punishment mode\n"),
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

  private Component status(boolean value) {
    return value
        ? Component.literal("enabled").withStyle(ChatFormatting.GREEN)
        : Component.literal("disabled").withStyle(ChatFormatting.RED);
  }

  private <T> Component changed(T current, T saved) {
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
    configMessage.append(Component.literal("\nMod Status: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isEnabled()));
    configMessage.append(changed(config.isEnabled(), properties.getProperty("settings.enabled")));

    // Debug status
    configMessage.append(Component.literal("\nDebug: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isDebugEnabled()));
    configMessage.append(
        changed(config.isDebugEnabled(), properties.getProperty("settings.debug")));

    // Lightning status
    configMessage.append(Component.literal("\nLightning Effect: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isLightningEffectEnabled()));
    configMessage.append(
        changed(
            config.isLightningEffectEnabled(),
            properties.getProperty("settings.lightning-effect")));

    // Milk Teleportation status
    configMessage.append(
        Component.literal("\nMilk Teleportation: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isTeleportEnabled()));
    configMessage.append(
        changed(config.isTeleportEnabled(), properties.getProperty("settings.teleport-enabled")));

    // Cow Invincibility Status
    configMessage.append(Component.literal("\nCow Invincibility: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isCowInvincibilityEnabled()));
    configMessage.append(
        changed(
            config.isCowInvincibilityEnabled(),
            properties.getProperty(SettingsEnum.COW_INVINCIBILITY.toLongString())));

    // Cow Health Level
    // configMessage.append(Component.literal("\nMax Cow Health: ").withStyle(ChatFormatting.GRAY));
    // configMessage.append(String.valueOf(config.getCowHealth()));
    // configMessage.append(
    //     changed(
    //        config.getCowHealth(),
    //        properties.getProperty(SettingsEnum.COW_HEALTH.toLongString()))
    //     );

    // Player Damage Amount
    configMessage.append(
        Component.literal("\nPlayer Damage Amount: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(String.valueOf(config.getPlayerDamageAmount()));
    configMessage.append(
        changed(
            config.getPlayerDamageAmount(),
            properties.getProperty(SettingsEnum.PLAYER_DAMAGE_AMOUNT.toLongString())));

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
    configMessage.append(Component.literal("\nAdmin Bypass: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(status(config.isAllowBypass()));
    configMessage.append(
        changed(config.isAllowBypass(), properties.getProperty("settings.allow-bypass")));

    // Punishment Mode
    configMessage.append(Component.literal("\nPunishment Mode: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(config.getPunishmentMode().toString());
    configMessage.append(
        changed(
            config.getPunishmentMode().toString(),
            properties.getProperty("settings.punishment-mode")));

    // == Other values ==
    configMessage.append(Component.literal("\nBypass OP Level: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(Component.literal(String.valueOf(config.getBypassOpLevel())));
    configMessage.append(
        changed(config.getBypassOpLevel(), properties.getProperty("settings.bypass-op-level")));

    configMessage.append(Component.literal("\nAdmin OP Level: ").withStyle(ChatFormatting.GRAY));
    configMessage.append(Component.literal(String.valueOf(config.getAdminOpLevel())));
    configMessage.append(
        changed(config.getAdminOpLevel(), properties.getProperty("settings.admin-op-level")));

    if (config.isDebugEnabled()) {
      // Custom death message status
      configMessage.append(
          Component.literal("\nCustom Death Messages: ").withStyle(ChatFormatting.GRAY));
      configMessage.append(status(config.isCustomDeathMessagesEnabled()));
      configMessage.append(
          changed(
              config.isCustomDeathMessagesEnabled(),
              properties.getProperty("settings.custom-death-messages")));

      // == String values ==
      List<String> deathMessages = config.getDeathMessages();
      String formattedDeathMessages = String.join("\n   ", deathMessages);

      // Assault Objective
      configMessage.append(
          Component.literal("\nAssault Objective: ").withStyle(ChatFormatting.GRAY));
      configMessage.append(config.getAssaultObjective());
      configMessage.append(
          changed(
              config.getAssaultObjective(),
              properties.getProperty("scoreboard.assault-objective")));

      // Kill Objective
      configMessage.append(Component.literal("\nKill Objective: ").withStyle(ChatFormatting.GRAY));
      configMessage.append(config.getKillObjective());
      configMessage.append(
          changed(config.getKillObjective(), properties.getProperty("scoreboard.kill-objective")));

      // Assault Display
      configMessage.append(Component.literal("\nAssault Display: ").withStyle(ChatFormatting.GRAY));
      configMessage.append(config.getAssaultDisplay());
      configMessage.append(
          changed(
              config.getAssaultDisplay(), properties.getProperty("scoreboard.assault-display")));

      // Kill Display
      configMessage.append(Component.literal("\nKill Display: ").withStyle(ChatFormatting.GRAY));
      configMessage.append(config.getKillDisplay());
      configMessage.append(
          changed(config.getKillDisplay(), properties.getProperty("scoreboard.kill-display")));

      // Bypass Permission
      configMessage.append(
          Component.literal("\nAdmin Bypass Permission: ").withStyle(ChatFormatting.GRAY));
      configMessage.append(config.getBypassPermission());
      configMessage.append(
          changed(
              config.getBypassPermission(),
              properties.getProperty("permissions.bypass-permission")));

      // Admin Permission
      configMessage.append(
          Component.literal("\nAdmin Permission: ").withStyle(ChatFormatting.GRAY));
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
    }

    ctx.getSource().sendSuccess(() -> configMessage, false);
    return 1;
  }

  private String boolToEnabled(boolean value) {
    if (value) {
      return "enabled";
    } else {
      return "disabled";
    }
  }

  private int enableTeleport(CommandContext<CommandSourceStack> ctx) {
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setTeleportEnabled(value);
    String enabled = boolToEnabled(value);
    String message = "Milk teleportation " + enabled;
    ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    displaySaveConfigMessage(ctx.getSource());
    return 1;
  }

  private int executeCowInvincibilityEnabled(CommandContext<CommandSourceStack> ctx) {
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setCowInvincibilityEnabled(value);
    String enabled = boolToEnabled(value);
    String message = "Cow invincibility " + enabled;
    ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    displaySaveConfigMessage(ctx.getSource());
    return 1;
  }

  private int executeScoreboardEnabled(CommandContext<CommandSourceStack> ctx) {
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setScoreboardEnabled(value);
    String enabled = boolToEnabled(value);
    String message = "Scoreboard " + enabled;
    ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    displaySaveConfigMessage(ctx.getSource());
    return 1;
  }

  private int executeTrackAssaultsEnabled(CommandContext<CommandSourceStack> ctx) {
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setTrackAssaultsEnabled(value);
    String enabled = boolToEnabled(value);
    String message = "Cow Assault Tracking " + enabled;
    ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    displaySaveConfigMessage(ctx.getSource());
    return 1;
  }

  private int executeTrackKillsEnabled(CommandContext<CommandSourceStack> ctx) {
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setTrackKillsEnabled(value);
    String enabled = boolToEnabled(value);
    String message = "Cow Kill Tracking " + enabled;
    ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    displaySaveConfigMessage(ctx.getSource());
    return 1;
  }

  private int executeSetCowHealth(CommandContext<CommandSourceStack> ctx) {
    double value = DoubleArgumentType.getDouble(ctx, "value");
    this.config.setCowHealth(value);
    ctx.getSource()
        .sendSuccess(
            () -> Component.literal("Set default cow health to: " + String.valueOf(value)), false);
    return 1;
  }

  private int executeSetPlayerDamageAmount(CommandContext<CommandSourceStack> ctx) {
    double value = DoubleArgumentType.getDouble(ctx, "value");
    this.config.setPlayerDamageAmount(value);
    ctx.getSource()
        .sendSuccess(
            () -> Component.literal("Set player punishment damage to: " + String.valueOf(value)),
            false);
    return 1;
  }

  private int executeSetBypassLevel(CommandContext<CommandSourceStack> ctx) {
    int value = IntegerArgumentType.getInteger(ctx, "value");
    this.config.setBypassOpLevel(value);
    ctx.getSource()
        .sendSuccess(() -> Component.literal("Set Punishment Bypass Level To: " + value), false);
    return 1;
  }

  private int executeSetAdminLevel(CommandContext<CommandSourceStack> ctx) {
    int value = IntegerArgumentType.getInteger(ctx, "value");
    this.config.setAdminOpLevel(value);
    ctx.getSource().sendSuccess(() -> Component.literal("Set Admin OP Level To: " + value), false);
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
    displaySaveConfigMessage(source);
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

  private int executeBypassEnabled(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setBypassEnabled(value);
    source.sendSuccess(() -> Component.literal("bypass set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

  private int executeModEnabled(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setModEnabled(value);
    source.sendSuccess(() -> Component.literal("enabled set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

  private int executeLightningEffectEnabled(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    boolean value = BoolArgumentType.getBool(ctx, "value");
    this.config.setLightningModeEnabled(value);
    source.sendSuccess(() -> Component.literal("lightning_effect set to " + value), true);
    displaySaveConfigMessage(source);
    return 1;
  }

  private int executeSetPunishmentMode(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack source = ctx.getSource();
    PunishmentMode mode;
    try {
      String value = StringArgumentType.getString(ctx, "value");
      mode = PunishmentMode.fromString(value);
    } catch (IllegalArgumentException e) {
      LOGGER.warn(invalidPunishmentModeMessage(e.getMessage()));
      mode = PunishmentMode.DEATH;
    }
    final PunishmentMode resolvedMode = mode;

    // Suggestions don't enforce - validate here.
    if (!mode.equals(PunishmentMode.DEATH)
        && !mode.equals(PunishmentMode.DAMAGE)
        && !mode.equals(PunishmentMode.LIGHTNING_ONLY)) {
      ctx.getSource().sendFailure(Component.literal(invalidPunishmentModeMessage(mode.toString())));
      return 0;
    }
    this.config.setPunishmentMode(mode);
    source.sendSuccess(
        () -> Component.literal("punishment-mode set to " + resolvedMode.toString().toLowerCase()),
        true);
    displaySaveConfigMessage(source);
    return 1;
  }

  public String invalidPunishmentModeMessage(String mode) {
    return "Invalid punishment mode: "
        + mode
        + ". Must be one of: 'death' OR 'kill', 'damage' OR 'hurt',"
        + " 'lightning-only' OR 'lightning_only'.";
  }

  private int executePrintPlayerStats(CommandContext<CommandSourceStack> ctx) {
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

  private int executePrintGlobalStats(CommandContext<CommandSourceStack> ctx) {
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
