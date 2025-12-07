package org.maks.eventPlugin.winterevent.wintercave.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.eventPlugin.winterevent.wintercave.WinterCaveManager;

/**
 * Listens for player actions in Winter Cave.
 * Handles player_head clicks, quits, and world changes.
 */
public class WinterCavePlayerListener implements Listener {
    private final WinterCaveManager caveManager;

    public WinterCavePlayerListener(WinterCaveManager caveManager) {
        this.caveManager = caveManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check if clicking player_head
        if (event.getClickedBlock().getType() != Material.PLAYER_HEAD &&
            event.getClickedBlock().getType() != Material.PLAYER_WALL_HEAD) {
            return;
        }

        // Check if player has active instance
        if (!caveManager.isInstanceLocked() || !player.getUniqueId().equals(caveManager.getActivePlayerId())) {
            return;
        }

        // Check if mob was killed
        if (!caveManager.isMobKilled(player.getUniqueId())) {
            player.sendMessage("§f§l[Winter Event] §cYou must defeat the creature first!");
            return;
        }

        // Complete instance and give reward
        caveManager.completeInstance(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        caveManager.handlePlayerQuit(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // If player left the cave world, release instance
        if (caveManager.isInstanceLocked() && player.getUniqueId().equals(caveManager.getActivePlayerId())) {
            caveManager.releaseInstance();
        }
    }
}
