package org.maks.eventPlugin.fullmoon.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for Blood Vial summon mechanic.
 * When a player right-clicks on the designated block with a Blood Vial in their pouch,
 * Amarok boss is summoned.
 */
public class BloodVialSummonListener implements Listener {

    private final FullMoonManager fullMoonManager;
    private final ConfigManager config;

    // Anti-spam cooldown per player (1 second to prevent double-click)
    private final Map<UUID, Long> playerClickCooldown = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 1000; // 1 second

    // Boss spawn cooldown per difficulty (60 seconds between spawns)
    private final Map<String, Long> difficultySpawnCooldown = new HashMap<>();
    private static final long SPAWN_COOLDOWN_MS = 60000; // 60 seconds

    public BloodVialSummonListener(FullMoonManager fullMoonManager, ConfigManager config) {
        this.fullMoonManager = fullMoonManager;
        this.config = config;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        // Event must be active
        if (!fullMoonManager.isEventActive()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location clickedLoc = event.getClickedBlock().getLocation();

        // Determine difficulty based on player's current mode
        String difficulty = fullMoonManager.getPlayerDifficulty(playerId);
        boolean isHard = difficulty.equalsIgnoreCase("hard");

        // Get blood vial block location for the player's difficulty
        String difficultyKey = isHard ? "hard" : "normal";
        var bloodVialSection = config.getSection("full_moon.coordinates.map1." + difficultyKey + ".blood_vial_block");
        if (bloodVialSection == null) return;

        int blockX = bloodVialSection.getInt("x");
        int blockY = bloodVialSection.getInt("y");
        int blockZ = bloodVialSection.getInt("z");
        String worldName = bloodVialSection.getString("world", "world");

        // Check if clicked block matches the configured blood vial block location
        if (!clickedLoc.getWorld().getName().equalsIgnoreCase(worldName)) return;
        if (clickedLoc.getBlockX() != blockX) return;
        if (clickedLoc.getBlockY() != blockY) return;
        if (clickedLoc.getBlockZ() != blockZ) return;

        // --- POCZĄTEK POPRAWKI (Podwójna wiadomość) ---

        // Check player click cooldown (prevent double-click spam)
        long now = System.currentTimeMillis();
        Long lastClick = playerClickCooldown.get(playerId);
        if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN_MS) {
            // Silently ignore - this is just anti-spam, no message needed
            event.setCancelled(true);
            return;
        }

        // Update player click cooldown IMMEDIATELY
        // To zapobiega podwójnemu wysłaniu wiadomości o cooldownie bossa
        playerClickCooldown.put(playerId, now);

        // --- KONIEC POPRAWKI ---

        // Check boss spawn cooldown (60s between spawns per difficulty)
        Long lastSpawn = difficultySpawnCooldown.get(difficultyKey);
        if (lastSpawn != null && (now - lastSpawn) < SPAWN_COOLDOWN_MS) {
            long remainingSeconds = (SPAWN_COOLDOWN_MS - (now - lastSpawn)) / 1000;
            player.sendMessage("§c§l[Full Moon] §cAmarok was recently summoned! Wait " + remainingSeconds + "s before summoning again.");
            event.setCancelled(true);
            return;
        }

        // Usunięto: playerClickCooldown.put(playerId, now); (przeniesione wyżej)


        // Check if player has Blood Vial in pouch
        if (!PouchHelper.isAvailable()) {
            player.sendMessage("§c§l[Full Moon] §cIngredientPouch plugin is not available!");
            return;
        }

        int bloodVials = PouchHelper.getItemQuantity(player, "blood_vial");
        if (bloodVials < 1) {
            player.sendMessage("§c§l[Full Moon] §cYou need a §4Blood Vial §cto summon Amarok!");
            player.sendMessage("§7Blood Vials can be obtained from mini-bosses.");
            return;
        }

        // Consume Blood Vial
        if (!PouchHelper.consumeBloodVial(player)) {
            player.sendMessage("§c§l[Full Moon] §cFailed to consume Blood Vial!");
            return;
        }

        // Get Amarok spawn location for the player's difficulty
        var amarokSpawnSection = config.getSection("full_moon.coordinates.map1." + difficultyKey + ".amarok_spawn");
        if (amarokSpawnSection == null) {
            player.sendMessage("§c§l[Full Moon] §cSpawn location not configured!");
            PouchHelper.addItem(player, "blood_vial", 1); // Refund
            return;
        }

        int spawnX = amarokSpawnSection.getInt("x");
        int spawnY = amarokSpawnSection.getInt("y");
        int spawnZ = amarokSpawnSection.getInt("z");
        String spawnWorldName = amarokSpawnSection.getString("world", "world");

        Location spawnLoc = new Location(
                clickedLoc.getWorld(),
                spawnX + 0.5,
                spawnY + 1.0, // Spawn 1 block above ground to prevent spawning in floor
                spawnZ + 0.5
        );

        // Spawn Amarok
        String mobType = isHard ? "amarok_hard" : "amarok_normal";
        if (!spawnAmarok(spawnLoc, mobType)) {
            player.sendMessage("§c§l[Full Moon] §cFailed to summon Amarok!");
            // Refund Blood Vial
            PouchHelper.addItem(player, "blood_vial", 1);
            return;
        }

        // Update difficulty spawn cooldown
        difficultySpawnCooldown.put(difficultyKey, now);

        // Success messages
        event.setCancelled(true);
        player.sendMessage("§c§l[Full Moon] §eYou have summoned §6Amarok, First Werewolf§e!");

        // Broadcast to nearby players
        for (Player nearbyPlayer : spawnLoc.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(spawnLoc) <= 50) {
                if (!nearbyPlayer.equals(player)) {
                    nearbyPlayer.sendMessage("§c§l[Full Moon] §6" + player.getName() + " §ehas summoned §6Amarok§e!");
                }
                nearbyPlayer.sendTitle(
                        "§c§lAMAROK AWAKENS",
                        "§eFirst Werewolf rises!",
                        10, 40, 20
                );
            }
        }
    }

    /**
     * Spawn Amarok boss using MythicMobs console command.
     *
     * @param location The exact location to spawn at (from config)
     * @param mobType The MythicMobs internal name (amarok_normal or amarok_hard)
     * @return True if successful
     */
    private boolean spawnAmarok(Location location, String mobType) {
        try {
            // Build spawn command: mm m spawn <mob> <amount> <world>,<x>,<y>,<z>,<yaw>,<pitch>
            String command = String.format("mm m spawn %s 1 %s,%.2f,%.2f,%.2f,0,0",
                    mobType,
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ()
            );

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}