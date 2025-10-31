package org.maks.eventPlugin.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public void set(String path, Object value) {
        config.set(path, value);
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException ignored) {
        }
    }

    public ConfigurationSection getSection(String path) {
        return config.getConfigurationSection(path);
    }

    public void reload() {
        load();
    }

    /**
     * Check if debug mode is enabled in config.
     * @return true if debug is enabled
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    /**
     * Log a debug message if debug mode is enabled.
     * @param message The message to log
     */
    public void debug(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

}
