package org.maks.eventPlugin.fullmoon;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.fullmoon.map2.Map2InstanceManager;
import org.maks.eventPlugin.fullmoon.map2.Map2BossSequenceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main manager for the Full Moon event.
 * Coordinates quests, map access, mob tracking, and rewards.
 */
public class FullMoonManager {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;
    private final EventManager eventManager;
    private final QuestManager questManager;
    private final CursedAmphoryManager cursedAmphoryManager;
    private final Map2InstanceManager map2InstanceManager;
    private final Map2BossSequenceManager map2BossSequenceManager;

    // Track which difficulty each player is currently in (normal or hard)
    private final Map<UUID, String> playerDifficulty = new HashMap<>();

    // Track mob kills for quest progress (mob type -> kill count)
    // This is separate from EventManager progress
    private final Map<UUID, Map<String, Integer>> playerMobKills = new HashMap<>();

    public FullMoonManager(JavaPlugin plugin, DatabaseManager database, ConfigManager config, EventManager eventManager) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.eventManager = eventManager;
        this.questManager = new QuestManager(database, config);  // Pass ConfigManager!
        this.cursedAmphoryManager = new CursedAmphoryManager(plugin, this, config);
        this.map2InstanceManager = new Map2InstanceManager(plugin, config);
        this.map2BossSequenceManager = new Map2BossSequenceManager(config);

        // Start Cursed Amphory system if event is active
        if (isEventActive()) {
            cursedAmphoryManager.start();
        }
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ConfigManager getConfig() {
        return config;
    }

    public CursedAmphoryManager getCursedAmphoryManager() {
        return cursedAmphoryManager;
    }

    public Map2InstanceManager getMap2InstanceManager() {
        return map2InstanceManager;
    }

    public Map2BossSequenceManager getMap2BossSequenceManager() {
        return map2BossSequenceManager;
    }

    /**
     * Stop the Full Moon event and reset all quest progress.
     * This should be called when the event ends or is manually stopped.
     */
    public void stopEvent() {
        // Stop Cursed Amphory spawns
        cursedAmphoryManager.stop();

        // Reset all quest progress for all players
        questManager.resetAllProgress();
        plugin.getLogger().info("[Full Moon] All quest progress has been reset for event rerun");

        // Clear player difficulty tracking
        playerDifficulty.clear();
        playerMobKills.clear();

        // Stop the event through EventManager
        eventManager.stop();
    }

    /**
     * Set the difficulty mode for a player (normal or hard).
     */
    public void setPlayerDifficulty(UUID playerId, String difficulty) {
        if (difficulty.equalsIgnoreCase("normal") || difficulty.equalsIgnoreCase("hard")) {
            playerDifficulty.put(playerId, difficulty.toLowerCase());
        }
    }

    /**
     * Get the current difficulty for a player.
     */
    public String getPlayerDifficulty(UUID playerId) {
        return playerDifficulty.getOrDefault(playerId, "normal");
    }

    /**
     * Check if player is in hard mode.
     */
    public boolean isHardMode(UUID playerId) {
        return "hard".equalsIgnoreCase(getPlayerDifficulty(playerId));
    }

    /**
     * Check if player meets the requirements for normal mode.
     */
    public boolean meetsNormalRequirements(Player player) {
        int requiredLevel = config.getSection("full_moon.requirements.normal").getInt("level", 50);
        return player.getLevel() >= requiredLevel;
    }

    /**
     * Check if player meets the requirements for hard mode.
     * Requires level check + IPS check (via IngredientPouch plugin).
     */
    public boolean meetsHardRequirements(Player player) {
        int requiredLevel = config.getSection("full_moon.requirements.hard").getInt("level", 75);
        int requiredIPS = config.getSection("full_moon.requirements.hard").getInt("ips", 15);

        if (player.getLevel() < requiredLevel) {
            return false;
        }

        // Check IPS if IngredientPouch is available
        if (PouchHelper.isAvailable()) {
            return PouchHelper.hasEnough(player, "ips", requiredIPS);
        }

        // If IngredientPouch is not available, just check level
        return true;
    }

    /**
     * Handle a MythicMob kill by a player.
     * Updates both quest progress and event progress.
     *
     * @param player The player who killed the mob
     * @param mobType The MythicMobs internal name
     * @param isHard Whether this was a hard mode kill
     * @param progressAmount Base progress amount for event
     */
    public void handleMobKill(Player player, String mobType, boolean isHard, int progressAmount) {
        UUID playerId = player.getUniqueId();

        // Determine actual mob type (strip _normal or _hard suffix for quest matching)
        String baseMobType = mobType;
        if (mobType.endsWith("_normal")) {
            baseMobType = mobType.substring(0, mobType.length() - 7);
        } else if (mobType.endsWith("_hard")) {
            baseMobType = mobType.substring(0, mobType.length() - 5);
        }

        // Update quest progress (pass isHard for quest matching)
        boolean questCompleted = questManager.addQuestProgress(playerId, baseMobType, 1, isHard);
        if (questCompleted) {
            player.sendMessage("§a§l[Full Moon] §aQuest completed!");
            player.sendTitle("§aQuest Complete!", "", 10, 40, 10);
        }

        // Update event progress (with 2x multiplier for hard mode)
        double multiplier = isHard ? 2.0 : 1.0;
        eventManager.addProgress(player, progressAmount, multiplier);

        // Track kill count for statistics
        Map<String, Integer> kills = playerMobKills.computeIfAbsent(playerId, k -> new HashMap<>());
        kills.put(baseMobType, kills.getOrDefault(baseMobType, 0) + 1);
    }

    /**
     * Get total kills of a mob type for a player.
     */
    public int getPlayerKills(UUID playerId, String mobType) {
        Map<String, Integer> kills = playerMobKills.get(playerId);
        if (kills == null) return 0;
        return kills.getOrDefault(mobType, 0);
    }

    /**
     * Reset all Full Moon data for event rerun.
     */
    public void resetAllData() {
        questManager.resetAllProgress();
        playerDifficulty.clear();
        playerMobKills.clear();
        plugin.getLogger().info("[Full Moon] All data reset for event rerun");
    }

    /**
     * Reset data for a specific player.
     */
    public void resetPlayerData(UUID playerId) {
        questManager.resetPlayerProgress(playerId);
        playerDifficulty.remove(playerId);
        playerMobKills.remove(playerId);
    }

    /**
     * Check if the Full Moon event is currently active.
     */
    public boolean isEventActive() {
        return eventManager != null && eventManager.isActive();
    }

    /**
     * Start all Full Moon systems (called when event becomes active).
     */
    public void onEventStart() {
        cursedAmphoryManager.start();
        plugin.getLogger().info("[Full Moon] Event systems started");
    }

    /**
     * Stop all Full Moon systems (called when event becomes inactive).
     */
    public void onEventStop() {
        cursedAmphoryManager.stop();
        map2InstanceManager.cleanupAll();
        plugin.getLogger().info("[Full Moon] Event systems stopped");
    }
}
