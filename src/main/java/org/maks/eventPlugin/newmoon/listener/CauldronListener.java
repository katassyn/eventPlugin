package org.maks.eventPlugin.newmoon.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.maks.eventPlugin.newmoon.NewMoonManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles cauldron interactions in New Moon event.
 *
 * When a player right-clicks a cauldron in Map 2 (White/Black Realms):
 * - Costs 50 Fairy Wood
 * - Grants 1 minute of damage buff against the Lord
 * - Without this buff, Lords are immune to player damage
 * - Has 3 second cooldown to prevent double-click spam
 */
public class CauldronListener implements Listener {

    private final NewMoonManager newMoonManager;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 3000; // 3 seconds

    public CauldronListener(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    @EventHandler
    public void onCauldronClick(PlayerInteractEvent event) {
        if (!newMoonManager.isEventActive()) {
            return;
        }

        // Check if player right-clicked a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAULDRON) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Cancel the event to prevent cauldron interaction
        event.setCancelled(true);

        // Check click cooldown to prevent double-click spam
        long now = System.currentTimeMillis();
        Long lastClick = clickCooldowns.get(playerId);
        if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN_MS) {
            // Player clicked too recently, silently ignore
            return;
        }

        // Set new click cooldown
        clickCooldowns.put(playerId, now);

        // Check if player already has active buff
        if (newMoonManager.hasCauldronBuff(playerId)) {
            int remaining = newMoonManager.getCauldronBuffRemainingSeconds(playerId);
            player.sendMessage("§c§l[New Moon] §cYou already have the Lord's Weakness active! (" + remaining + "s remaining)");
            return;
        }

        // Try to activate buff
        if (newMoonManager.activateCauldronBuff(player)) {
            // Success - buff activated
            player.sendMessage("§a§l[New Moon] §aLord's Weakness activated!");
            player.sendMessage("§7You can now damage the Lord for §e1 minute§7!");

            // Play sound effect
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

            // Particle effect
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.SPELL_WITCH,
                    block.getLocation().add(0.5, 1.0, 0.5),
                    50,
                    0.3, 0.5, 0.3,
                    0.1
            );
        } else {
            // Failed - not enough Fairy Wood or other error
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}
