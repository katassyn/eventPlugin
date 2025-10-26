package org.maks.eventPlugin.fullmoon.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.maks.eventPlugin.fullmoon.CursedAmphoryManager;

/**
 * Listener for Cursed Amphory interactions.
 * When a player opens the Cursed Amphory (black shulker box),
 * the Crystallized Curse boss is summoned.
 */
public class CursedAmphoryListener implements Listener {

    private final CursedAmphoryManager amphoryManager;

    public CursedAmphoryListener(CursedAmphoryManager amphoryManager) {
        this.amphoryManager = amphoryManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Check if it's a black shulker box
        if (clickedBlock.getType() != Material.BLACK_SHULKER_BOX) return;

        // Check if this is the active Cursed Amphory
        if (!amphoryManager.isAmphoryActive()) return;

        // Check if location matches
        if (amphoryManager.getCurrentAmphoryLocation() == null) return;
        if (!clickedBlock.getLocation().equals(amphoryManager.getCurrentAmphoryLocation())) return;

        // Cancel the normal shulker open
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Handle amphory opening (spawns boss)
        if (amphoryManager.handleAmphoryOpened(clickedBlock.getLocation())) {
            player.sendMessage("§5§l[Full Moon] §dYou have unleashed the §5Crystallized Curse§d!");
        }
    }
}
