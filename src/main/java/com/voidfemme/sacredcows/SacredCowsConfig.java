package com.voidfemme.sacredcows;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SacredCowsConfig {
    private final Path configFile;
    private final Properties properties = new Properties();

    // Default values
    private boolean enabled = true;
    private boolean debugEnabled = false;
    private String punishmentType = "DEATH";
    private double damageAmount = 10.0;
    private boolean lightningEffectEnabled = true;
    private boolean customDeathMessagesEnabled = true;
    private boolean allowBypass = true;

    // Scoreboard settings
    private boolean scoreboardEnabled = true;
    private boolean trackAssaultsEnabled = true;
    private boolean trackKillsEnabled = true;
    private String assaultObjective = "cowAssaults";
    private String killObjective = "cowKills";
    private String assaultDisplay = "Cow Assaults";
    private String killDisplay = "Cow Kills";

    // Permissions
    private String bypassPermission = "sacredcows.bypass";
    private String adminPermission = "sacredcows.admin";

    // Death messages
    private List<String> deathMessages = Arrays.asList(
            "%player% was moo-rdered for their bovine crimes",
            "%player% faced divine bovine retribution",
            "The cows fought back, and %player% lost",
            "%player% learned the hard way not to mess with cows",
            "A mysterious force struck down %player% for harming a cow");

    public SacredCowsConfig(Path configFile) {
        this.configFile = configFile;
    }

    public void load() {
        if (configFile == null || !Files.exists(configFile)) {
            createDefaultConfig();
            return;
        }

        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
            parseProperties();
        } catch (IOException e) {
            System.err.println("Error loading config file: " + e.getMessage());
            createDefaultConfig();
        }
    }

    private void createDefaultConfig() {
        if (configFile == null)
            return;

        try {
            // Ensure parent directories exist
            Files.createDirectories(configFile.getParent());

            // Create default properties
            properties.clear();
            setDefaultProperties();

            // Save to file
            try (OutputStream output = Files.newOutputStream(configFile)) {
                properties.store(output, "sacredcows Configuration File");
            }

            parseProperties();
        } catch (IOException e) {
            System.err.println("Error creating default config file: " + e.getMessage());
        }
    }

    private void setDefaultProperties() {
        // General Settings
        properties.setProperty("settings.enabled", "true");
        properties.setProperty("settings.debug", "false");
        properties.setProperty("settings.punishment-type", "DEATH");
        properties.setProperty("settings.damage-amount", "10.0");
        properties.setProperty("settings.lightning-effect", "true");
        properties.setProperty("settings.custom-death-messages", "true");
        properties.setProperty("settings.allow-bypass", "true");

        // Scoreboard Settings
        properties.setProperty("scoreboard.enabled", "true");
        properties.setProperty("scoreboard.track-assaults", "true");
        properties.setProperty("scoreboard.track-kills", "true");
        properties.setProperty("scoreboard.assault-objective", "cowAssaults");
        properties.setProperty("scoreboard.kill-objective", "cowKills");
        properties.setProperty("scoreboard.assault-display", "Cow Assaults");
        properties.setProperty("scoreboard.kill-display", "Cow Kills");

        // Permissions
        properties.setProperty("permissions.bypass-permission", "sacredcows.bypass");
        properties.setProperty("permissions.admin-permission", "sacredcows.admin");

        // Death Messages
        for (int i = 0; i < deathMessages.size(); i++) {
            properties.setProperty("death-messages." + i, deathMessages.get(i));
        }
    }

    private void parseProperties() {
        // General settings
        enabled = Boolean.parseBoolean(properties.getProperty("settings.enabled", "true"));
        debugEnabled = Boolean.parseBoolean(properties.getProperty("settings.debug", "false"));
        punishmentType = properties.getProperty("settings.punishment-type", "DEATH");
        damageAmount = Double.parseDouble(properties.getProperty("settings.damage-amount", "10.0"));
        lightningEffectEnabled = Boolean.parseBoolean(properties.getProperty("settings.lightning-effect", "true"));
        customDeathMessagesEnabled = Boolean
                .parseBoolean(properties.getProperty("settings.custom-death-messages", "true"));
        allowBypass = Boolean.parseBoolean(properties.getProperty("settings.allow-bypass", "true"));

        // Scoreboard settings
        scoreboardEnabled = Boolean.parseBoolean(properties.getProperty("scoreboard.enabled", "true"));
        trackAssaultsEnabled = Boolean.parseBoolean(properties.getProperty("scoreboard.track-assaults", "true"));
        trackKillsEnabled = Boolean.parseBoolean(properties.getProperty("scoreboard.track-kills", "true"));
        assaultObjective = properties.getProperty("scoreboard.assault-objective", "cowAssaults");
        killObjective = properties.getProperty("scoreboard.kill-objective", "cowKills");
        assaultDisplay = properties.getProperty("scoreboard.assault-display", "Cow Assaults");
        killDisplay = properties.getProperty("scoreboard.kill-display", "Cow Kills");

        // Permissions
        bypassPermission = properties.getProperty("permissions.bypass-permission", "sacredcows.bypass");
        adminPermission = properties.getProperty("permissions.admin-permission", "sacredcows.admin");

        // Death messages
        deathMessages = new ArrayList<>();
        for (int i = 0; i < 100; i++) { // Check up to 100 death messages
            String message = properties.getProperty("death-messages." + i);
            if (message != null && !message.trim().isEmpty()) {
                deathMessages.add(message);
            } else {
                break;
            }
        }

        // If no death messages found, use defaults
        if (deathMessages.isEmpty()) {
            deathMessages = Arrays.asList(
                    "%player% was moo-rdered for their bovine crimes",
                    "%player% faced divine bovine retribution",
                    "The cows fought back, and %player% lost",
                    "%player% learned the hard way not to mess with cows",
                    "A mysterious force struck down %player% for harming a cow");
        }
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public String getPunishmentType() {
        return punishmentType;
    }

    public double getDamageAmount() {
        return damageAmount;
    }

    public boolean isLightningEffectEnabled() {
        return lightningEffectEnabled;
    }

    public boolean isCustomDeathMessagesEnabled() {
        return customDeathMessagesEnabled;
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public boolean isTrackAssaultsEnabled() {
        return trackAssaultsEnabled;
    }

    public boolean isTrackKillsEnabled() {
        return trackKillsEnabled;
    }

    public boolean isAllowBypass() {
        return allowBypass;
    }

    public String getAssaultObjective() {
        return assaultObjective;
    }

    public String getKillObjective() {
        return killObjective;
    }

    public String getAssaultDisplay() {
        return assaultDisplay;
    }

    public String getKillDisplay() {
        return killDisplay;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public String getAdminPermission() {
        return adminPermission;
    }

    public List<String> getDeathMessages() {
        return new ArrayList<>(deathMessages);
    }
}
