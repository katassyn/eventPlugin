package org.maks.eventPlugin.newmoon.listener;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.map2.Map2Instance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Lord respawn blocks in Map 2 (White/Black Realms).
 *
 * Three END_PORTAL_FRAME blocks allow respawning the Lord:
 * - First respawn: 100 Fairy Wood
 * - Second respawn: 200 Fairy Wood
 * - Third respawn: 300 Fairy Wood
 * - Maximum 3 respawns per instance
 * - Has 3 second cooldown to prevent double-click spam
 *
 * Blocks should have holograms above them showing cost and usage.
 */
public class LordRespawnListener implements Listener {

    private final NewMoonManager newMoonManager;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 3000; // 3 seconds

    public LordRespawnListener(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    @EventHandler
    public void onRespawnBlockClick(PlayerInteractEvent event) {
        if (!newMoonManager.isEventActive()) {
            return;
        }

        // Check if player right-clicked a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Cancel the event
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

        // Check if player is in a Map2 instance
        Map2Instance instance = newMoonManager.getMap2InstanceManager().getInstance(player.getUniqueId());
        if (instance == null) {
            // Player clicked END_PORTAL_FRAME outside of instance - ignore
            return;
        }

        // Check if this block is one of the lord respawn blocks in this instance
        Location clickedLoc = block.getLocation();
        Location[] respawnLocs = instance.getLordRespawnLocations();

        int blockIndex = -1;
        for (int i = 0; i < 3; i++) {
            if (respawnLocs[i] != null && respawnLocs[i].getBlock().equals(block)) {
                blockIndex = i;
                break;
            }
        }

        if (blockIndex == -1) {
            // This END_PORTAL_FRAME is not a lord respawn block
            return;
        }

        // Get current respawn count and cost
        int currentCount = instance.getLordRespawnCount();

        // Check if player is clicking the correct block in sequence
        if (blockIndex != currentCount) {
            if (blockIndex < currentCount) {
                player.sendMessage("§c§l[New Moon] §cThis respawn block has already been used!");
            } else {
                player.sendMessage("§c§l[New Moon] §cYou must use the respawn blocks in order!");
                player.sendMessage("§7Next block: §e#" + (currentCount + 1) + " §7(Cost: §6" + getCostForRespawn(currentCount) + " Fairy Wood§7)");
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if max respawns reached
        if (currentCount >= 3) {
            player.sendMessage("§c§l[New Moon] §cMaximum respawns reached for this instance! (3/3)");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int cost = getCostForRespawn(currentCount);

        // Check if player has enough Fairy Wood
        if (!PouchHelper.isAvailable()) {
            player.sendMessage("§c§l[New Moon] §cPouch system unavailable!");
            return;
        }

        if (!PouchHelper.hasEnough(player, "witch_wood", cost)) {
            player.sendMessage("§c§l[New Moon] §cYou need §6" + cost + " Fairy Wood §cto respawn the Lord!");
            player.sendMessage("§7Your Fairy Wood: §e" + PouchHelper.getItemQuantity(player, "witch_wood"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Consume Fairy Wood
        if (!PouchHelper.consumeItem(player, "witch_wood", cost)) {
            player.sendMessage("§c§l[New Moon] §cFailed to consume Fairy Wood!");
            return;
        }

        // Determine lord type based on realm
        String realmType = instance.getRealmType(); // "white" or "black"
        String lordType = realmType.equals("white") ? "lord_silvanus" : "lord_malachai";
        boolean isHard = instance.isHard();

        // Spawn the lord using Map2MobSpawner
        boolean success = newMoonManager.getMap2MobSpawner().spawnLord(instance, lordType, isHard);

        if (success) {
            // Increment respawn count
            instance.incrementLordRespawnCount();

            // Update holograms to reflect new respawn count
            newMoonManager.getHologramManager().updateHolograms(instance);

            player.sendMessage("§a§l[New Moon] §aLord respawned successfully!");
            player.sendMessage("§7Respawns used: §e" + instance.getLordRespawnCount() + "§7/§e3");

            // Play effects
            Location spawnLoc = instance.getLordSpawnLocation();
            if (spawnLoc != null) {
                player.playSound(spawnLoc, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                player.playSound(spawnLoc, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.2f);

                // Particle effect
                player.getWorld().spawnParticle(
                        org.bukkit.Particle.EXPLOSION_HUGE,
                        spawnLoc,
                        5,
                        1.0, 1.0, 1.0,
                        0.1
                );

                player.getWorld().spawnParticle(
                        org.bukkit.Particle.PORTAL,
                        spawnLoc,
                        100,
                        1.5, 1.5, 1.5,
                        0.5
                );
            }
        } else {
            player.sendMessage("§c§l[New Moon] §cFailed to respawn Lord! Please contact an administrator.");
            // Refund Fairy Wood
            PouchHelper.addItem(player, "witch_wood", cost);
        }
    }

    /**
     * Get the cost for a specific respawn count.
     * @param respawnCount 0-2 (0 = first respawn, 1 = second, 2 = third)
     * @return Cost in Fairy Wood
     */
    private int getCostForRespawn(int respawnCount) {
        return switch (respawnCount) {
            case 0 -> 100;  // First respawn
            case 1 -> 200;  // Second respawn
            case 2 -> 300;  // Third respawn
            default -> -1;
        };
    }
}
