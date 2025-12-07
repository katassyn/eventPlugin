package org.maks.eventPlugin.newmoon.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.map2.Map2Instance;

/**
 * Listener for Map 2 player events (disconnect, death).
 * Handles cleanup when player leaves or dies in the New Moon Realm instances.
 */
public class Map2PlayerListener implements Listener {

    private final NewMoonManager newMoonManager;

    public Map2PlayerListener(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    /**
     * Handle player disconnect - cleanup their Map 2 instance.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Map2Instance instance = newMoonManager.getMap2InstanceManager().getInstance(player.getUniqueId());

        if (instance != null) {
            String realmName = instance.getRealmType().equals("white") ? "White Realm" : "Black Realm";
            Bukkit.getLogger().info("[New Moon] Player " + player.getName() + " disconnected, cleaning up " + realmName + " instance");

            // Cancel any scheduled cleanup tasks
            instance.cancelScheduledCleanup();

            // Remove instance immediately
            newMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());
        }

        // Clear player buff data (boss bars, buffs)
        newMoonManager.clearPlayerBuffData(player.getUniqueId());
    }

    /**
     * Handle player death in Map 2 - cancel countdown and cleanup instance after delay.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Map2Instance instance = newMoonManager.getMap2InstanceManager().getInstance(player.getUniqueId());

        if (instance == null) return;

        // Player died in their Map 2 instance
        String realmName = instance.getRealmType().equals("white") ? "White Realm" : "Black Realm";
        player.sendMessage("§c§l[New Moon] §cYou have died in the " + realmName + "!");
        player.sendMessage("§7Your instance will be removed shortly...");

        // Cancel any scheduled cleanup tasks
        instance.cancelScheduledCleanup();

        // Schedule immediate cleanup
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    // Cleanup instance immediately
                    newMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());
                },
                5 * 20L  // 5 seconds (time to click respawn)
        );
    }
}
