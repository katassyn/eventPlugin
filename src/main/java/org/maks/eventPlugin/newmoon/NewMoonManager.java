package org.maks.eventPlugin.newmoon;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.newmoon.map2.Map2InstanceManager;
import org.maks.eventPlugin.newmoon.map2.Map2MobSpawner;
import org.maks.eventPlugin.newmoon.map2.LordRespawnHologramManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main manager for the New Moon event.
 *
 * New Moon features:
 * - Two independent quest chains (White and Black Lords)
 * - Map 1: Black Bog (Normal and Hard difficulty)
 * - Map 2: Two separate realms (White Realm and Black Realm)
 * - Fairy Wood currency system
 * - Cauldron damage buff system (50 Fairy Wood for 1 minute)
 * - Lord respawn system (100/200/300 Fairy Wood)
 * - Four portals: White/Black x Normal/Hard
 */
public class NewMoonManager {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;
    private final EventManager eventManager;
    private final NewMoonQuestManager questManager;
    private final Map2InstanceManager map2InstanceManager;
    private final Map2MobSpawner map2MobSpawner;
    private final LordRespawnHologramManager hologramManager;

    // Track which difficulty each player is currently in (normal or hard)
    private final Map<UUID, String> playerDifficulty = new HashMap<>();

    // Track which realm each player is currently in (white or black)
    private final Map<UUID, String> playerRealm = new HashMap<>();

    // Track mob kills for quest progress
    private final Map<UUID, Map<String, Integer>> playerMobKills = new HashMap<>();

    // Track cauldron buff active players (UUID -> expiration timestamp)
    private final Map<UUID, Long> activeCauldronBuffs = new HashMap<>();

    // Track boss bars for cauldron buff timer (UUID -> BossBar)
    private final Map<UUID, BossBar> cauldronBuffBossBars = new HashMap<>();

    // Track boss bar update tasks (UUID -> BukkitTask)
    private final Map<UUID, BukkitTask> bossBarTasks = new HashMap<>();

    // Track lord respawn count for each instance (instance key -> respawn count 0-3)
    private final Map<String, Integer> lordRespawnCounts = new HashMap<>();

    public NewMoonManager(JavaPlugin plugin, DatabaseManager database, ConfigManager config, EventManager eventManager) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.eventManager = eventManager;
        this.questManager = new NewMoonQuestManager(database, config);
        this.hologramManager = new LordRespawnHologramManager();
        this.map2InstanceManager = new Map2InstanceManager(plugin, config, hologramManager);
        this.map2MobSpawner = new Map2MobSpawner(config);
    }

    public NewMoonQuestManager getQuestManager() {
        return questManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ConfigManager getConfig() {
        return config;
    }

    public Map2InstanceManager getMap2InstanceManager() {
        return map2InstanceManager;
    }

    public Map2MobSpawner getMap2MobSpawner() {
        return map2MobSpawner;
    }

    public LordRespawnHologramManager getHologramManager() {
        return hologramManager;
    }

    /**
     * Check if the New Moon event is currently active.
     */
    public boolean isEventActive() {
        return eventManager != null && eventManager.isActive();
    }

    /**
     * Stop the New Moon event and cleanup.
     */
    public void stopEvent() {
        // Clear player tracking
        playerDifficulty.clear();
        playerRealm.clear();
        playerMobKills.clear();
        activeCauldronBuffs.clear();
        lordRespawnCounts.clear();

        // Remove all boss bars
        for (UUID playerId : cauldronBuffBossBars.keySet()) {
            removeCauldronBuffBossBar(playerId);
        }

        // Cleanup all Map2 instances
        map2InstanceManager.cleanupAll();

        // Reset all quest progress for all players (for event rerun)
        questManager.resetAllQuests();
        plugin.getLogger().info("[New Moon] All quest progress has been reset for event rerun");

        // Stop the event through EventManager
        eventManager.stop();

        plugin.getLogger().info("[New Moon] Event stopped and data cleared");
    }

    // ==================== DIFFICULTY MANAGEMENT ====================

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

    // ==================== REALM MANAGEMENT ====================

    /**
     * Set the realm for a player (white or black).
     */
    public void setPlayerRealm(UUID playerId, String realm) {
        if (realm.equalsIgnoreCase("white") || realm.equalsIgnoreCase("black")) {
            playerRealm.put(playerId, realm.toLowerCase());
        }
    }

    /**
     * Get the current realm for a player.
     */
    public String getPlayerRealm(UUID playerId) {
        return playerRealm.getOrDefault(playerId, "white");
    }

    // ==================== REQUIREMENTS ====================

    /**
     * Check if player meets the requirements for normal mode.
     */
    public boolean meetsNormalRequirements(Player player) {
        int requiredLevel = config.getSection("new_moon.requirements.normal").getInt("level", 50);
        return player.getLevel() >= requiredLevel;
    }

    /**
     * Check if player meets the requirements for hard mode Map 1.
     * Requires level + 15 IPS.
     */
    public boolean meetsHardRequirements(Player player) {
        int requiredLevel = config.getSection("new_moon.requirements.hard").getInt("level", 75);
        int requiredIPS = config.getSection("new_moon.requirements.hard").getInt("ips", 15);

        if (player.getLevel() < requiredLevel) {
            return false;
        }

        if (PouchHelper.isAvailable()) {
            return PouchHelper.hasEnough(player, "ips", requiredIPS);
        }

        return true;
    }

    /**
     * Check if player can enter portal to White Realm (Normal).
     * Requires: 1x New Moon Parchment + quest 3 completed and claimed.
     */
    public boolean canEnterWhiteRealmNormal(Player player) {
        UUID playerId = player.getUniqueId();

        // Check quest unlock
        if (!questManager.hasUnlockedWhitePortal(playerId)) {
            return false;
        }

        // Check New Moon Parchment
        if (PouchHelper.isAvailable()) {
            return PouchHelper.hasEnough(player, "new_moon_scrol", 1);
        }

        return true;
    }

    /**
     * Check if player can enter portal to White Realm (Hard).
     * Requires: 1x New Moon Parchment + 30 IPS + quest 3 completed and claimed.
     */
    public boolean canEnterWhiteRealmHard(Player player) {
        UUID playerId = player.getUniqueId();

        // Check quest unlock
        if (!questManager.hasUnlockedWhitePortal(playerId)) {
            return false;
        }

        if (PouchHelper.isAvailable()) {
            return PouchHelper.hasEnough(player, "new_moon_scrol", 1) &&
                   PouchHelper.hasEnough(player, "ips", 30);
        }

        return true;
    }

    /**
     * Check if player can enter portal to Black Realm (Normal).
     * Requires: 1x New Moon Parchment + quest 8 completed and claimed.
     */
    public boolean canEnterBlackRealmNormal(Player player) {
        UUID playerId = player.getUniqueId();

        // Check quest unlock
        if (!questManager.hasUnlockedBlackPortal(playerId)) {
            return false;
        }

        // Check New Moon Parchment
        if (PouchHelper.isAvailable()) {
            return PouchHelper.hasEnough(player, "new_moon_scrol", 1);
        }

        return true;
    }

    /**
     * Check if player can enter portal to Black Realm (Hard).
     * Requires: 1x New Moon Parchment + 30 IPS + quest 8 completed and claimed.
     */
    public boolean canEnterBlackRealmHard(Player player) {
        UUID playerId = player.getUniqueId();

        // Check quest unlock
        if (!questManager.hasUnlockedBlackPortal(playerId)) {
            return false;
        }

        if (PouchHelper.isAvailable()) {
            return PouchHelper.hasEnough(player, "new_moon_scrol", 1) &&
                   PouchHelper.hasEnough(player, "ips", 30);
        }

        return true;
    }

    // ==================== MOB KILL HANDLING ====================

    /**
     * Handle a MythicMob kill by a player.
     * Updates both quest progress and event progress.
     *
     * @param player The player who killed the mob
     * @param mobType The MythicMobs internal name
     * @param isHard Whether this was a hard mode kill
     * @param progressAmount Base progress amount for event
     * @param buffMultiplier The buff multiplier (e.g., 1.0 for none, 1.5 for Attrie)
     */
    public void handleMobKill(Player player, String mobType, boolean isHard, int progressAmount, double buffMultiplier) {
        UUID playerId = player.getUniqueId();

        // Strip _normal or _hard suffix for quest matching
        String baseMobType = mobType;
        if (mobType.endsWith("_normal")) {
            baseMobType = mobType.substring(0, mobType.length() - 7);
        } else if (mobType.endsWith("_hard")) {
            baseMobType = mobType.substring(0, mobType.length() - 5);
        }

        // Update quest progress
        boolean questCompleted = questManager.addQuestProgress(playerId, baseMobType, 1, isHard);
        if (questCompleted) {
            player.sendMessage("§a§l[New Moon] §aQuest completed!");
            player.sendTitle("§aQuest Complete!", "", 10, 40, 10);
        }

        // Update event progress (with 2x multiplier for hard mode AND buff multiplier)
        double hardMultiplier = isHard ? 2.0 : 1.0;
        double totalMultiplier = hardMultiplier * buffMultiplier;
        eventManager.addProgress(player, progressAmount, totalMultiplier);

        // Track kill count
        Map<String, Integer> kills = playerMobKills.computeIfAbsent(playerId, k -> new HashMap<>());
        kills.put(baseMobType, kills.getOrDefault(baseMobType, 0) + 1);
    }

    /**
     * Handle "non-physical" progress for quests.
     * This is for quests that track progress without giving physical items.
     * Example: Walking Wood gives progress points for quest 2/7.
     *
     * @param player The player
     * @param progressType The progress type (e.g., "walking_wood_progress")
     * @param amount Amount of progress to add
     * @param isHard Whether this was from hard mode
     */
    public void handleQuestProgress(Player player, String progressType, int amount, boolean isHard) {
        UUID playerId = player.getUniqueId();

        boolean questCompleted = questManager.addQuestProgress(playerId, progressType, amount, isHard);
        if (questCompleted) {
            player.sendMessage("§a§l[New Moon] §aQuest completed!");
            player.sendTitle("§aQuest Complete!", "", 10, 40, 10);
        }
    }
    /**
     * Handle "non-physical" progress for quests WITH player feedback.
     * This checks which quests were updated and sends appropriate feedback messages.
     * Only shows feedback for quests that are actually accepted and not completed.
     *
     * @param player The player
     * @param progressType The progress type (e.g., "walking_wood", "nighty_witch_essence")
     * @param amount Amount of progress to add
     * @param isHard Whether this was from hard mode
     */
    public void handleQuestProgressWithFeedback(Player player, String progressType, int amount, boolean isHard) {
        UUID playerId = player.getUniqueId();

        // Find all quests that match this progress type and are accepted
        for (var quest : questManager.getAllQuests()) {
            if (!quest.targetMobType().equalsIgnoreCase(progressType)) continue;
            if (!questManager.isQuestUnlocked(playerId, quest.id())) continue;
            if (!questManager.isQuestAccepted(playerId, quest.id())) continue;
            if (questManager.isQuestCompleted(playerId, quest.id())) continue;

            // Check hard mode matching
            if (quest.isHardMode() && !isHard) continue; // Hard quest requires hard kill
            if (!quest.isHardMode() && isHard) continue; // Normal quest doesn't match hard kill

            // This quest is active and will receive progress
            int currentProgress = questManager.getQuestProgress(playerId, quest.id());
            int newProgress = Math.min(currentProgress + amount, quest.requiredKills());

            // Send feedback message with quest name and progress
            player.sendMessage("§a§l[New Moon] §a" + quest.description() + ": §f" + newProgress + "/" + quest.requiredKills());
        }

        // Now actually add the progress
        boolean questCompleted = questManager.addQuestProgress(playerId, progressType, amount, isHard);
        if (questCompleted) {
            player.sendMessage("§a§l[New Moon] §aQuest completed!");
            player.sendTitle("§aQuest Complete!", "", 10, 40, 10);
        }
    }
    /**
     * Get total kills of a mob type for a player.
     */
    public int getPlayerKills(UUID playerId, String mobType) {
        Map<String, Integer> kills = playerMobKills.get(playerId);
        if (kills == null) return 0;
        return kills.getOrDefault(mobType, 0);
    }

    // ==================== CAULDRON BUFF SYSTEM ====================

    /**
     * Activate cauldron buff for a player.
     * Costs 50 Fairy Wood, lasts 1 minute.
     * During this time, the player can damage the Lord.
     *
     * @param player The player
     * @return True if buff was activated successfully
     */
    public boolean activateCauldronBuff(Player player) {
        if (!PouchHelper.isAvailable()) {
            player.sendMessage("§c§l[New Moon] §cPouch system unavailable!");
            return false;
        }

        int cost = config.getSection("new_moon").getInt("cauldron_buff_cost", 50);

        if (!PouchHelper.hasEnough(player, "witch_wood", cost)) {
            player.sendMessage("§c§l[New Moon] §cYou need " + cost + " Fairy Wood to activate the cauldron!");
            return false;
        }

        // Consume Fairy Wood
        if (!PouchHelper.consumeItem(player, "witch_wood", cost)) {
            player.sendMessage("§c§l[New Moon] §cFailed to consume Fairy Wood!");
            return false;
        }

        // Activate buff for 1 minute (60 seconds = 60000 ms)
        long expirationTime = System.currentTimeMillis() + 60000;
        activeCauldronBuffs.put(player.getUniqueId(), expirationTime);

        player.sendMessage("§a§l[New Moon] §aYou can now damage the Lord for 1 minute!");
        player.sendTitle("§6§lLord's Weakness Active!", "§e1 minute remaining", 10, 40, 10);

        // Create boss bar for buff timer
        createCauldronBuffBossBar(player);

        return true;
    }

    /**
     * Create and start boss bar for cauldron buff timer.
     */
    private void createCauldronBuffBossBar(Player player) {
        UUID playerId = player.getUniqueId();

        // Remove existing boss bar if any
        removeCauldronBuffBossBar(playerId);

        // Create new boss bar
        BossBar bossBar = Bukkit.createBossBar(
                "§6§lLord's Weakness: §e60s",
                BarColor.YELLOW,
                BarStyle.SOLID
        );
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);
        cauldronBuffBossBars.put(playerId, bossBar);

        // Create repeating task to update boss bar every second
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int remaining = getCauldronBuffRemainingSeconds(playerId);

            if (remaining <= 0) {
                // Buff expired - remove boss bar
                removeCauldronBuffBossBar(playerId);
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c§l[New Moon] §cLord's Weakness has expired!");
                }
                return;
            }

            // Update boss bar
            bossBar.setTitle("§6§lLord's Weakness: §e" + remaining + "s");
            bossBar.setProgress((double) remaining / 60.0);

            // Change color based on time remaining
            if (remaining <= 10) {
                bossBar.setColor(BarColor.RED);
            } else if (remaining <= 30) {
                bossBar.setColor(BarColor.YELLOW);
            } else {
                bossBar.setColor(BarColor.GREEN);
            }
        }, 0L, 20L); // Run every second (20 ticks)

        bossBarTasks.put(playerId, task);
    }

    /**
     * Remove cauldron buff boss bar for a player.
     */
    private void removeCauldronBuffBossBar(UUID playerId) {
        // Cancel task
        BukkitTask task = bossBarTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        // Remove boss bar
        BossBar bossBar = cauldronBuffBossBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Clear player data (boss bars, buffs, etc.) when they disconnect or leave instance.
     * Public method for use by listeners.
     */
    public void clearPlayerBuffData(UUID playerId) {
        removeCauldronBuffBossBar(playerId);
        activeCauldronBuffs.remove(playerId);
    }

    /**
     * Check if a player has active cauldron buff.
     * Automatically removes expired buffs.
     *
     * @param playerId The player UUID
     * @return True if player has active buff
     */
    public boolean hasCauldronBuff(UUID playerId) {
        Long expirationTime = activeCauldronBuffs.get(playerId);
        if (expirationTime == null) {
            return false;
        }

        // Check if expired
        if (System.currentTimeMillis() > expirationTime) {
            activeCauldronBuffs.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * Remove cauldron buff from a player.
     */
    public void removeCauldronBuff(UUID playerId) {
        activeCauldronBuffs.remove(playerId);
    }

    /**
     * Get remaining time for cauldron buff in seconds.
     * Returns 0 if no active buff.
     */
    public int getCauldronBuffRemainingSeconds(UUID playerId) {
        Long expirationTime = activeCauldronBuffs.get(playerId);
        if (expirationTime == null) {
            return 0;
        }

        long remaining = expirationTime - System.currentTimeMillis();
        if (remaining <= 0) {
            activeCauldronBuffs.remove(playerId);
            return 0;
        }

        return (int) (remaining / 1000);
    }

    // ==================== LORD RESPAWN SYSTEM ====================

    /**
     * Get the current respawn count for an instance.
     * Returns 0 if no respawns have been used yet.
     *
     * @param instanceKey Unique instance identifier (e.g., "white_realm_normal_1")
     * @return Respawn count (0-3)
     */
    public int getLordRespawnCount(String instanceKey) {
        return lordRespawnCounts.getOrDefault(instanceKey, 0);
    }

    /**
     * Get the cost for the next lord respawn.
     * Returns -1 if max respawns reached (3).
     *
     * @param instanceKey Unique instance identifier
     * @return Cost in Fairy Wood, or -1 if max reached
     */
    public int getNextRespawnCost(String instanceKey) {
        int currentCount = getLordRespawnCount(instanceKey);

        if (currentCount >= 3) {
            return -1; // Max respawns reached
        }

        var costsSection = config.getSection("new_moon.lord_respawn_costs");
        switch (currentCount) {
            case 0: return costsSection.getInt("first", 100);
            case 1: return costsSection.getInt("second", 200);
            case 2: return costsSection.getInt("third", 300);
            default: return -1;
        }
    }

    /**
     * Attempt to respawn a lord.
     *
     * @param player The player attempting to respawn
     * @param instanceKey Unique instance identifier
     * @return True if respawn was successful
     */
    public boolean respawnLord(Player player, String instanceKey) {
        if (!PouchHelper.isAvailable()) {
            player.sendMessage("§c§l[New Moon] §cPouch system unavailable!");
            return false;
        }

        int cost = getNextRespawnCost(instanceKey);
        if (cost == -1) {
            player.sendMessage("§c§l[New Moon] §cMaximum respawns reached for this instance!");
            return false;
        }

        if (!PouchHelper.hasEnough(player, "witch_wood", cost)) {
            player.sendMessage("§c§l[New Moon] §cYou need " + cost + " Fairy Wood to respawn the Lord!");
            return false;
        }

        // Consume Fairy Wood
        if (!PouchHelper.consumeItem(player, "witch_wood", cost)) {
            player.sendMessage("§c§l[New Moon] §cFailed to consume Fairy Wood!");
            return false;
        }

        // Increment respawn count
        int newCount = lordRespawnCounts.getOrDefault(instanceKey, 0) + 1;
        lordRespawnCounts.put(instanceKey, newCount);

        player.sendMessage("§a§l[New Moon] §aLord respawned! (" + newCount + "/3 respawns used)");

        return true;
    }

    /**
     * Reset respawn count for an instance (when instance is cleaned up).
     */
    public void resetRespawnCount(String instanceKey) {
        lordRespawnCounts.remove(instanceKey);
    }

    // ==================== ADMIN FUNCTIONS ====================

    /**
     * Reset all player data for a specific player.
     * Clears quest progress, mob kills, difficulty, realm, buffs, etc.
     *
     * @param playerId Player UUID to reset
     */
    public void resetPlayerData(UUID playerId) {
        // Clear in-memory data
        playerDifficulty.remove(playerId);
        playerRealm.remove(playerId);
        playerMobKills.remove(playerId);
        activeCauldronBuffs.remove(playerId);

        // Clear quest progress from database
        questManager.resetPlayerQuests(playerId);

        plugin.getLogger().info("[New Moon] Reset all data for player: " + playerId);
    }

    /**
     * Reset all New Moon data.
     * Clears all player data, quest progress, and instance data.
     * Does NOT stop the event.
     */
    public void resetAllData() {
        // Clear all in-memory data
        playerDifficulty.clear();
        playerRealm.clear();
        playerMobKills.clear();
        activeCauldronBuffs.clear();
        lordRespawnCounts.clear();

        // Reset all quest data in database
        questManager.resetAllQuests();

        plugin.getLogger().info("[New Moon] Reset all New Moon data");
    }
}
