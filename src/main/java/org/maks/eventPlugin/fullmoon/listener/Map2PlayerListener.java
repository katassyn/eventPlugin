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
            fullMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());
        }
    }

    /**
     * Handle player death in Map 2 - teleport to spawn and cleanup instance after delay.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Map2Instance instance = fullMoonManager.getMap2InstanceManager().getInstance(player.getUniqueId());

        if (instance == null) return;

        // Player died in their Map 2 instance
        player.sendMessage("§c§l[Full Moon] §cYou have died in the Blood Moon Arena!");
        player.sendMessage("§7Your instance will be removed shortly...");

        // Schedule teleport to spawn and cleanup
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    // --- POCZĄTEK POPRAWKI: Usunięcie zbędnego teleportu ---
                    // Gracz już jest martwy i sam się zrespi na spawn.
                    // Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
                    // --- KONIEC POPRAWKI ---

                    // Cleanup instance
                    fullMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());

                    // Wyślij wiadomość dopiero gdy gracz jest online (po respawnie)
                    if (player.isOnline()) {
                        player.sendMessage("§e§l[Full Moon] §7Your arena instance has been removed.");
                    }
                },
                5 * 20L  // 5 seconds (czas na kliknięcie respawn)
        );
    }
}