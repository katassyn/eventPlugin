package org.maks.eventPlugin.winterevent;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.winterevent.wintercave.WinterCaveManager;
import org.maks.eventPlugin.winterevent.summit.WinterSummitManager;

import java.util.*;

/**
 * Main manager for the Winter Event (X-mas).
 * Coordinates gift drops, winter cave, and winter summit systems.
 */
public class WinterEventManager {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;
    private final EventManager eventManager;

    // Sub-managers
    private final WinterCaveManager winterCaveManager;
    private final WinterSummitManager winterSummitManager;
    private final WinterQuestManager questManager;

    // Gift rarity system (6 levels)
    private final Map<String, Double> giftRarities = new LinkedHashMap<>();
    private final Map<String, String> giftBoxIds = new HashMap<>();

    // Track which difficulty each player is currently in (infernal/hell/blood)
    private final Map<UUID, String> playerDifficulty = new HashMap<>();

    public WinterEventManager(JavaPlugin plugin, DatabaseManager database, ConfigManager config, EventManager eventManager) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.eventManager = eventManager;

        // Initialize sub-managers
        this.winterCaveManager = new WinterCaveManager(plugin, database, config, this);
        this.winterSummitManager = new WinterSummitManager(plugin, config, this);
        this.questManager = new WinterQuestManager(plugin, database, config, eventManager.getEventId());

        // Load gift system configuration
        loadGiftConfiguration();

        plugin.getLogger().info("[Winter Event] Initialized");
    }

    /**
     * Load gift rarity and box IDs from config.
     */
    private void loadGiftConfiguration() {
        giftRarities.put("green", config.getSection("winter_event.gift_drop.rarities").getDouble("green", 50.0));
        giftRarities.put("blue", config.getSection("winter_event.gift_drop.rarities").getDouble("blue", 25.0));
        giftRarities.put("purple", config.getSection("winter_event.gift_drop.rarities").getDouble("purple", 10.0));
        giftRarities.put("orange", config.getSection("winter_event.gift_drop.rarities").getDouble("orange", 6.875));
        giftRarities.put("gold", config.getSection("winter_event.gift_drop.rarities").getDouble("gold", 5.0));
        giftRarities.put("red", config.getSection("winter_event.gift_drop.rarities").getDouble("red", 3.125));

        giftBoxIds.put("green", config.getSection("winter_event.gift_drop.boxes").getString("green", "green_gift"));
        giftBoxIds.put("blue", config.getSection("winter_event.gift_drop.boxes").getString("blue", "blue_gift"));
        giftBoxIds.put("purple", config.getSection("winter_event.gift_drop.boxes").getString("purple", "purple_gift"));
        giftBoxIds.put("orange", config.getSection("winter_event.gift_drop.boxes").getString("orange", "orange_gift"));
        giftBoxIds.put("gold", config.getSection("winter_event.gift_drop.boxes").getString("gold", "gold_gift"));
        giftBoxIds.put("red", config.getSection("winter_event.gift_drop.boxes").getString("red", "red_gift"));
    }

    /**
     * Get the EventManager instance for this event.
     */
    public EventManager getEventManager() {
        return eventManager;
    }

    /**
     * Check if the Winter Event is currently active.
     */
    public boolean isEventActive() {
        return eventManager != null && eventManager.isActive();
    }

    /**
     * Get the global gift drop chance (0.001 = 0.1%).
     * In debug mode, returns 0.5 (50%) for easier testing.
     */
    public double getGlobalDropChance() {
        boolean debugMode = config.getSection("winter_event").getBoolean("debug", false);
        if (debugMode) {
            return 0.5; // 50% chance in debug mode
        }
        return config.getSection("winter_event.gift_drop").getDouble("global_chance", 0.001);
    }

    /**
     * Select a random gift rarity based on configured percentages.
     * Returns one of: green, blue, purple, orange, gold, red
     */
    public String selectRandomGiftRarity() {
        double random = Math.random() * 100.0;
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : giftRarities.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback to green (most common)
        return "green";
    }

    /**
     * Get the EliteLootbox ID for a given rarity.
     */
    public String getGiftBoxId(String rarity) {
        return giftBoxIds.getOrDefault(rarity, "green_gift");
    }

    /**
     * Get the display color for a rarity.
     */
    public String getRarityColor(String rarity) {
        switch (rarity.toLowerCase()) {
            case "green": return "§a";
            case "blue": return "§9";
            case "purple": return "§5";
            case "orange": return "§6";
            case "gold": return "§e";
            case "red": return "§c";
            default: return "§7";
        }
    }

    /**
     * Get the display name for a rarity.
     */
    public String getRarityName(String rarity) {
        switch (rarity.toLowerCase()) {
            case "green": return "Common";
            case "blue": return "Rare";
            case "purple": return "Epic";
            case "orange": return "Legendary";
            case "gold": return "Unique";
            case "red": return "Mythic";
            default: return "Unknown";
        }
    }

    /**
     * Set the difficulty mode for a player (infernal/hell/blood).
     */
    public void setPlayerDifficulty(UUID playerId, String difficulty) {
        String normalized = difficulty.toLowerCase();
        if (normalized.equals("infernal") || normalized.equals("hell") || normalized.equals("blood")) {
            playerDifficulty.put(playerId, normalized);
        }
    }

    /**
     * Get the current difficulty for a player.
     */
    public String getPlayerDifficulty(UUID playerId) {
        return playerDifficulty.getOrDefault(playerId, "infernal");
    }

    /**
     * Check if player meets requirements for a difficulty level.
     */
    public boolean meetsDifficultyRequirements(Player player, String difficulty) {
        String path = "winter_event.summit.requirements." + difficulty.toLowerCase();
        int requiredLevel = config.getSection(path).getInt("level", 50);
        int requiredIPS = config.getSection(path).getInt("ips", 0);

        if (player.getLevel() < requiredLevel) {
            return false;
        }

        // Check IPS if required
        if (requiredIPS > 0) {
            // Integration with IngredientPouch will be handled by PouchHelper
            // For now, just check level
            return true;
        }

        return true;
    }

    /**
     * Determine mob tier from mob internal name.
     */
    public String getMobTier(String mobType) {
        String lowerName = mobType.toLowerCase();

        // Bosses
        if (lowerName.contains("gluttonous_bear") || lowerName.contains("krampus_spirit")) {
            return "bosses";
        }

        // Mini Bosses
        if (lowerName.contains("cookie_thief_goblin") ||
            lowerName.contains("cookie_destroyer_sludge") ||
            lowerName.contains("walking_christmas_tree")) {
            return "mini_bosses";
        }

        // Elite
        if (lowerName.contains("cookie_commander_wolf")) {
            return "elite_mobs";
        }

        // Normal mobs (default)
        return "normal_mobs";
    }

    /**
     * Get random progress amount for a mob kill based on tier and difficulty.
     * Uses winter_event.drop_chances configuration.
     */
    public int getProgressForMob(String mobType, String difficulty) {
        String tier = getMobTier(mobType);
        String configPath = "winter_event.drop_chances." + tier + "." + difficulty;

        var dropChancesSection = config.getSection(configPath);
        if (dropChancesSection == null) {
            plugin.getLogger().warning("[Winter Event] No drop_chances configured for " + configPath);
            return 0;
        }

        // Build weighted random map
        java.util.Map<Integer, Double> chances = new java.util.LinkedHashMap<>();
        for (String key : dropChancesSection.getKeys(false)) {
            try {
                int amount = Integer.parseInt(key);
                double weight = dropChancesSection.getDouble(key, 0.0);
                chances.put(amount, weight / 100.0); // Convert percentage to decimal
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[Winter Event] Invalid drop_chances key: " + key);
            }
        }

        if (chances.isEmpty()) {
            return 0;
        }

        // Weighted random selection
        double totalWeight = chances.values().stream().mapToDouble(Double::doubleValue).sum();
        double random = Math.random() * totalWeight;
        double cumulative = 0.0;

        for (java.util.Map.Entry<Integer, Double> entry : chances.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback to first value
        return chances.keySet().iterator().next();
    }

    /**
     * Handle a MythicMob kill by a player.
     * Updates event progress based on difficulty and mob type.
     */
    public void handleMobKill(Player player, String mobType, int progressAmount, double multiplier) {
        if (!isEventActive()) {
            return;
        }

        eventManager.addProgress(player, progressAmount, multiplier);
    }

    /**
     * Stop the Winter Event and cleanup.
     */
    public void stopEvent() {
        playerDifficulty.clear();
        winterCaveManager.cleanup();
        winterSummitManager.cleanupLeftoverInstances();
        if (questManager != null) {
            questManager.cleanup();
        }
        eventManager.stop();
        plugin.getLogger().info("[Winter Event] Event stopped");
    }

    /**
     * Reset all Winter Event data.
     */
    public void resetAllData() {
        playerDifficulty.clear();
        winterCaveManager.cleanup();
        winterSummitManager.cleanupLeftoverInstances();
        if (questManager != null) {
            questManager.cleanup();
        }
        plugin.getLogger().info("[Winter Event] All data reset");
    }

    /**
     * Get Winter Cave manager.
     */
    public WinterCaveManager getWinterCaveManager() {
        return winterCaveManager;
    }

    /**
     * Get Winter Summit manager.
     */
    public WinterSummitManager getWinterSummitManager() {
        return winterSummitManager;
    }

    /**
     * Get Winter Quest manager.
     */
    public WinterQuestManager getQuestManager() {
        return questManager;
    }

    /**
     * Called when event becomes active.
     */
    public void onEventStart() {
        plugin.getLogger().info("[Winter Event] Event systems started");
    }

    /**
     * Called when event becomes inactive.
     */
    public void onEventStop() {
        plugin.getLogger().info("[Winter Event] Event systems stopped");
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public ConfigManager getConfig() {
        return config;
    }
}
