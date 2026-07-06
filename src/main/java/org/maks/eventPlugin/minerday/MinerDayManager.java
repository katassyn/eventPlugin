package org.maks.eventPlugin.minerday;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;

/**
 * Manager for Miner Day event.
 * This event provides:
 * - Progress (+1) for each fully mined ore/plant/bone/crystal
 * - x2 ore sell prices during the event
 */
public class MinerDayManager {
    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final EventManager eventManager;

    public MinerDayManager(Plugin plugin, DatabaseManager databaseManager,
                           ConfigManager configManager, EventManager eventManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.eventManager = eventManager;
    }

    /**
     * Check if the Miner Day event is currently active.
     */
    public boolean isActive() {
        return eventManager != null && eventManager.isActive();
    }

    /**
     * Add progress to a player (called when they mine something).
     */
    public void addProgress(Player player, int amount) {
        if (!isActive()) {
            return;
        }
        // Each mined block = 1 progress
        eventManager.addProgress(player, amount, 1.0);
    }

    /**
     * Get the event manager for this event.
     */
    public EventManager getEventManager() {
        return eventManager;
    }

    /**
     * Get current progress for a player.
     */
    public int getProgress(Player player) {
        return eventManager.getProgress(player);
    }

    /**
     * Get max progress required.
     */
    public int getMaxProgress() {
        return eventManager.getMaxProgress();
    }

    /**
     * Check if player has completed the event.
     */
    public boolean isCompleted(Player player) {
        return getProgress(player) >= getMaxProgress();
    }
}
