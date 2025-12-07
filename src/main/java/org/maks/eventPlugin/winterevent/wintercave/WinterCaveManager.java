package org.maks.eventPlugin.winterevent.wintercave;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.winterevent.WinterEventManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Manager for Winter Cave daily gift system.
 * Handles instance locking, timer, and reward distribution.
 */
public class WinterCaveManager {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;
    private final WinterEventManager winterEventManager;
    private final WinterCaveDailyRewardDAO rewardDAO;

    private WinterCaveInstance activeInstance;

    public WinterCaveManager(JavaPlugin plugin, DatabaseManager database, ConfigManager config, WinterEventManager winterEventManager) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.winterEventManager = winterEventManager;
        this.rewardDAO = new WinterCaveDailyRewardDAO(database, "winter_event");
        this.activeInstance = null;
    }

    /**
     * Check if player can enter cave today (hasn't claimed yet).
     */
    public boolean canEnterToday(UUID playerId) {
        if (!winterEventManager.isEventActive()) {
            return false;
        }

        int currentDay = winterEventManager.getEventManager().getCurrentDay();
        return !rewardDAO.hasClaimed(playerId, currentDay);
    }

    /**
     * Check if cave instance is locked (someone is inside).
     */
    public boolean isInstanceLocked() {
        return activeInstance != null;
    }

    /**
     * Get the player currently in the cave (if any).
     */
    public UUID getActivePlayerId() {
        return activeInstance != null ? activeInstance.getPlayerId() : null;
    }

    /**
     * Start cave instance for player.
     * Locks instance, warps player, spawns mob, starts timer.
     */
    public void startInstance(Player player) {
        if (isInstanceLocked()) {
            player.sendMessage("§c§l[Winter Event] §cSomeone is already in the Winter Cave!");
            return;
        }

        if (!canEnterToday(player.getUniqueId())) {
            player.sendMessage("§c§l[Winter Event] §cYou already claimed today's reward!");
            return;
        }

        // Create instance
        activeInstance = new WinterCaveInstance(player.getUniqueId(), System.currentTimeMillis());

        // Lock in database
        lockInstanceInDatabase(player.getUniqueId());

        // Warp player
        String warpName = config.getSection("winter_event.winter_cave").getString("warp_name", "winter_cave");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warp " + warpName + " " + player.getName());

        player.sendMessage("§f§l[Winter Event] §aTeleporting to Winter Cave...");

        // Spawn mob after short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnCaveMob(player), 20L); // 1 second

        // Start 5-minute timer
        int timerMinutes = config.getSection("winter_event.winter_cave").getInt("timer_minutes", 5);
        int timerTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> forceExit(player), timerMinutes * 60 * 20L).getTaskId();
        activeInstance.setTimerTaskId(timerTaskId);

        player.sendMessage("§f§l[Winter Event] §eA mysterious creature appears! Defeat it to claim your reward.");
        player.sendMessage("§f§l[Winter Event] §cTime limit: " + timerMinutes + " minutes");
    }

    /**
     * Spawn the cave mob at configured location.
     */
    private void spawnCaveMob(Player player) {
        String world = config.getSection("winter_event.winter_cave.spawn_location").getString("world", "world");
        int x = config.getSection("winter_event.winter_cave.spawn_location").getInt("x");
        int y = config.getSection("winter_event.winter_cave.spawn_location").getInt("y") + 2; // +2 blocks higher to avoid spawning in floor
        int z = config.getSection("winter_event.winter_cave.spawn_location").getInt("z");
        String mobName = config.getSection("winter_event.winter_cave").getString("mob_name", "winter_cave_mob");

        String spawnCommand = "mm mobs spawn " + mobName + " 1 " + world + "," + x + "," + y + "," + z;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), spawnCommand);
    }

    /**
     * Complete cave instance - give reward and unlock.
     */
    public void completeInstance(Player player) {
        if (activeInstance == null || !activeInstance.getPlayerId().equals(player.getUniqueId())) {
            return;
        }

        // Cancel timer
        if (activeInstance.getTimerTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(activeInstance.getTimerTaskId());
        }

        // Get reward for current day
        int currentDay = winterEventManager.getEventManager().getCurrentDay();
        org.bukkit.inventory.ItemStack reward = rewardDAO.getDayReward(currentDay);

        if (reward != null) {
            player.getInventory().addItem(reward);
            player.sendMessage("§f§l[Winter Event] §aYou claimed your daily reward! Come back tomorrow.");
        } else {
            player.sendMessage("§f§l[Winter Event] §cNo reward configured for day " + currentDay + "!");
        }

        // Record claim
        rewardDAO.recordClaim(player.getUniqueId(), currentDay);

        // Unlock instance
        releaseInstance();
    }

    /**
     * Force player exit after timer expires.
     */
    public void forceExit(Player player) {
        if (activeInstance == null || !activeInstance.getPlayerId().equals(player.getUniqueId())) {
            return;
        }

        String spawnCommand = config.getSection("winter_event.winter_cave").getString("spawn_command", "spawn");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), spawnCommand + " " + player.getName());

        player.sendMessage("§f§l[Winter Event] §cTime's up! You were removed from the Winter Cave.");

        releaseInstance();
    }

    /**
     * Release instance (unlock and cleanup).
     */
    public void releaseInstance() {
        if (activeInstance != null) {
            if (activeInstance.getTimerTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(activeInstance.getTimerTaskId());
            }
            unlockInstanceInDatabase();
            activeInstance = null;
        }
    }

    /**
     * Handle player quit/disconnect - release instance.
     */
    public void handlePlayerQuit(UUID playerId) {
        if (activeInstance != null && activeInstance.getPlayerId().equals(playerId)) {
            releaseInstance();
        }
    }

    /**
     * Check if player killed the mob in their instance.
     */
    public boolean isMobKilled(UUID playerId) {
        return activeInstance != null && activeInstance.getPlayerId().equals(playerId) && activeInstance.isMobKilled();
    }

    /**
     * Mark mob as killed.
     */
    public void setMobKilled(UUID playerId) {
        if (activeInstance != null && activeInstance.getPlayerId().equals(playerId)) {
            activeInstance.setMobKilled(true);
        }
    }

    /**
     * Lock instance in database.
     */
    private void lockInstanceInDatabase(UUID playerId) {
        String sql = "REPLACE INTO winter_cave_active_instance (event_id, player_uuid, entry_time) VALUES (?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "winter_event");
            ps.setString(2, playerId.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to lock instance: " + e.getMessage());
        }
    }

    /**
     * Unlock instance in database.
     */
    private void unlockInstanceInDatabase() {
        String sql = "DELETE FROM winter_cave_active_instance WHERE event_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "winter_event");
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to unlock instance: " + e.getMessage());
        }
    }

    /**
     * Cleanup leftover instance from database (on plugin load).
     */
    public void cleanupLeftoverInstance() {
        unlockInstanceInDatabase();
        activeInstance = null;
    }

    /**
     * Get reward DAO for GUI access.
     */
    public WinterCaveDailyRewardDAO getRewardDAO() {
        return rewardDAO;
    }

    /**
     * Cleanup all instances and data.
     */
    public void cleanup() {
        releaseInstance();
    }
}
