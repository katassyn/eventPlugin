package org.maks.eventPlugin.fullmoon.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.map2.Map2Instance;

/**
 * Listener for Map 2 player events (disconnect, death).
 * Handles cleanup when player leaves or dies in the Blood Moon Arena.
 */
public class Map2PlayerListener implements Listener {

    private final FullMoonManager fullMoonManager;

    public Map2PlayerListener(FullMoonManager fullMoonManager) {
        this.fullMoonManager = fullMoonManager;
    }

    /**
     * Handle player disconnect - cleanup their Map 2 instance.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Map2Instance instance = fullMoonManager.getMap2InstanceManager().getInstance(player.getUniqueId());

        if (instance != null) {
            Bukkit.getLogger().info("[Full Moon] Player " + player.getName() + " disconnected, cleaning up Map2 instance");

            // Cancel any scheduled cleanup tasks (60s countdown, etc.)
            instance.cancelScheduledCleanup();

            // Remove instance immediately
            fullMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());
        }
    }

    /**
     * Handle player death in Map 2 - cancel countdown and cleanup instance after delay.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Map2Instance instance = fullMoonManager.getMap2InstanceManager().getInstance(player.getUniqueId());

        if (instance == null) return;

        // Player died in their Map 2 instance
        player.sendMessage("§c§l[Full Moon] §cYou have died in the Blood Moon Arena!");
        player.sendMessage("§7Your instance will be removed shortly...");

        // IMPORTANT: Cancel any scheduled cleanup tasks (60s countdown after boss death)
        instance.cancelScheduledCleanup();

        // Schedule immediate cleanup
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    // Cleanup instance immediately (don't wait for countdown)
                    fullMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());

                    // Silent cleanup - no messages
                },
                5 * 20L  // 5 seconds (time to click respawn)
        );
    }
}