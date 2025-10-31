package org.maks.eventPlugin.fullmoon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.maks.eventPlugin.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the Cursed Amphory spawn system.
 * Every hour, there's a chance for a Cursed Amphory (black shulker box) to spawn.
 * The amphory persists until opened or the next hour cycle.
 */
public class CursedAmphoryManager {

    private final JavaPlugin plugin;
    private final FullMoonManager fullMoonManager;
    private final ConfigManager config;

    private BukkitTask spawnTask;
    private BukkitTask hologramUpdateTask;
    private Location currentNormalAmphoryLocation;
    private Location currentHardAmphoryLocation;
    private boolean normalAmphoryActive = false;
    private boolean hardAmphoryActive = false;
    private long nextSpawnTime = 0;

    // Hologram entities for each amphora
    private final Map<String, List<ArmorStand>> holograms = new HashMap<>();

    // 1 hour in ticks (20 ticks = 1 second)
    private static final long NORMAL_SPAWN_INTERVAL = 20L * 60L * 60L; // 72000 ticks = 1 hour
    private static final long DEBUG_SPAWN_INTERVAL = 20L * 60L; // 1200 ticks = 1 minute (for testing)
    private static final double SPAWN_CHANCE = 0.05; // 5% chance to spawn each hour

    public CursedAmphoryManager(JavaPlugin plugin, FullMoonManager fullMoonManager, ConfigManager config) {
        this.plugin = plugin;
        this.fullMoonManager = fullMoonManager;
        this.config = config;
    }

    /**
     * Start the amphory spawn cycle.
     */
    public void start() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }

        // Check if debug mode is enabled
        boolean debugMode = config.getBoolean("full_moon.debug", false);
        long spawnInterval = debugMode ? DEBUG_SPAWN_INTERVAL : NORMAL_SPAWN_INTERVAL;

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fullMoonManager.isEventActive()) {
                return;
            }

            trySpawnAmphory();
        }, spawnInterval, spawnInterval); // Pierwsza próba po spawnInterval, potem co spawnInterval

        if (debugMode) {
            plugin.getLogger().info("[Full Moon] Cursed Amphory spawn system started in DEBUG MODE (1 minute interval)");
        } else {
            plugin.getLogger().info("[Full Moon] Cursed Amphory spawn system started (1 hour interval)");
        }
    }

    /**
     * Stop the amphory spawn cycle.
     */
    public void stop() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }

        if (hologramUpdateTask != null) {
            hologramUpdateTask.cancel();
            hologramUpdateTask = null;
        }

        removeCurrentAmphory();
        plugin.getLogger().info("[Full Moon] Cursed Amphory spawn system stopped");
    }

    /**
     * Try to spawn Cursed Amphoras (both normal and hard).
     */
    private void trySpawnAmphory() {
        // Remove old amphoras if still present
        removeCurrentAmphory();

        // Check if debug mode is enabled for next spawn time calculation
        boolean debugMode = config.getBoolean("full_moon.debug", false);
        long spawnInterval = debugMode ? DEBUG_SPAWN_INTERVAL : NORMAL_SPAWN_INTERVAL;

        // Update next spawn time (for hologram display)
        nextSpawnTime = System.currentTimeMillis() + (spawnInterval * 50); // Convert ticks to milliseconds

        // --- POCZĄTEK POPRAWKI: Dwa oddzielne losowania ---

        // Losowanie dla "normal"
        if (ThreadLocalRandom.current().nextDouble() <= SPAWN_CHANCE) {
            spawnAmphoryForDifficulty("normal");
        } else {
            plugin.getLogger().info("[Full Moon] Cursed Amphora (Normal) did not spawn (failed roll)");
        }

        // Losowanie dla "hard"
        if (ThreadLocalRandom.current().nextDouble() <= SPAWN_CHANCE) {
            spawnAmphoryForDifficulty("hard");
        } else {
            plugin.getLogger().info("[Full Moon] Cursed Amphora (Hard) did not spawn (failed roll)");
        }
        // --- KONIEC POPRAWKI ---
    }

    /**
     * Spawn amphora for a specific difficulty.
     */
    private void spawnAmphoryForDifficulty(String difficulty) {
        // Get spawn location from config
        var amphoraSection = config.getSection("full_moon.coordinates.map1." + difficulty + ".cursed_amphora");
        if (amphoraSection == null) {
            plugin.getLogger().warning("[Full Moon] Cursed Amphory coordinates not configured for " + difficulty + "!");
            return;
        }

        int x = amphoraSection.getInt("x");
        int y = amphoraSection.getInt("y");
        int z = amphoraSection.getInt("z");
        String worldName = amphoraSection.getString("world", "world");

        Location location = new Location(
                Bukkit.getWorld(worldName),
                x, y, z
        );

        if (location.getWorld() == null) {
            plugin.getLogger().warning("[Full Moon] Cursed Amphora world not found: " + worldName);
            return;
        }

        // Place black shulker box
        Block block = location.getBlock();
        block.setType(Material.BLACK_SHULKER_BOX);

        // Set as active
        if (difficulty.equalsIgnoreCase("normal")) {
            currentNormalAmphoryLocation = location;
            normalAmphoryActive = true;
        } else {
            currentHardAmphoryLocation = location;
            hardAmphoryActive = true;
        }

        // Create hologram above the amphora
        createHologram(location, difficulty);

        plugin.getLogger().info("[Full Moon] Cursed Amphora spawned at " + x + ", " + y + ", " + z + " (" + difficulty + ")");

        // NO MESSAGES - amphora spawns silently
    }

    /**
     * Create a hologram above the amphora using armor stands.
     */
    private void createHologram(Location location, String difficulty) {
        List<ArmorStand> stands = new ArrayList<>();

        // Location above the amphora
        Location hologramLoc = location.clone().add(0.5, 2.5, 0.5);

        // Line 1: Title (top)
        ArmorStand line1 = spawnHologramLine(hologramLoc, "§5§lCursed Amphora");
        stands.add(line1);

        // Line 2: Timer (will be updated)
        hologramLoc.subtract(0, 0.3, 0);
        ArmorStand line2 = spawnHologramLine(hologramLoc, getTimeUntilNextSpawn());
        stands.add(line2);

        // Store hologram entities
        holograms.put(difficulty, stands);

        // Start hologram update task if not running
        if (hologramUpdateTask == null) {
            hologramUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHolograms, 20L, 20L);
        }
    }

    /**
     * Spawn a single hologram line (armor stand).
     */
    private ArmorStand spawnHologramLine(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        return stand;
    }

    /**
     * Update all hologram timers.
     */
    private void updateHolograms() {
        String timeText = getTimeUntilNextSpawn();

        for (List<ArmorStand> stands : holograms.values()) {
            if (stands.size() >= 2) {
                ArmorStand timerLine = stands.get(1);
                if (timerLine != null && timerLine.isValid()) {
                    timerLine.setCustomName(timeText);
                }
            }
        }
    }

    /**
     * Get formatted time until next spawn.
     */
    private String getTimeUntilNextSpawn() {
        long remaining = nextSpawnTime - System.currentTimeMillis();
        if (remaining <= 0) {
            return "§e§lRespawning soon...";
        }

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("§7Next spawn: §e%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("§7Next spawn: §e%dm %ds", minutes, seconds);
        } else {
            return String.format("§7Next spawn: §e%ds", seconds);
        }
    }

    /**
     * Handle amphora being opened (spawns Crystallized Curse boss).
     *
     * @param location The location where the amphora was opened
     * @return True if this was a valid amphora opening
     */
    public boolean handleAmphoryOpened(Location location) {
        boolean isNormal = normalAmphoryActive && currentNormalAmphoryLocation != null && location.equals(currentNormalAmphoryLocation);
        boolean isHard = hardAmphoryActive && currentHardAmphoryLocation != null && location.equals(currentHardAmphoryLocation);

        if (!isNormal && !isHard) {
            return false;
        }

        // Spawn Crystallized Curse boss
        if (!spawnCrystallizedCurse(location)) {
            plugin.getLogger().warning("[Full Moon] Failed to spawn Crystallized Curse!");
            return false;
        }

        // Remove the amphora and hologram
        location.getBlock().setType(Material.AIR);

        if (isNormal) {
            removeHologram("normal");
            normalAmphoryActive = false;
            currentNormalAmphoryLocation = null;
        } else {
            removeHologram("hard");
            hardAmphoryActive = false;
            currentHardAmphoryLocation = null;
        }

        // NO MESSAGES - amphora opens silently, boss spawns

        return true;
    }

    /**
     * Spawn Crystallized Curse boss using MythicMobs command.
     * Boss spawns exactly where the amphora was.
     *
     * @param location The spawn location (where amphora was)
     * @return True if successful
     */
    private boolean spawnCrystallizedCurse(Location location) {
        try {
            // Spawn at center of block, slightly above
            Location spawnLoc = location.clone().add(0.5, 1.0, 0.5);

            // Build spawn command: mm m spawn <mob> <amount> <world>,<x>,<y>,<z>,<yaw>,<pitch>
            String command = String.format("mm m spawn crystallized_curse 1 %s,%.2f,%.2f,%.2f,0,0",
                    spawnLoc.getWorld().getName(),
                    spawnLoc.getX(),
                    spawnLoc.getY(),
                    spawnLoc.getZ()
            );

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Remove all current amphoras if they exist.
     */
    private void removeCurrentAmphory() {
        // Remove normal amphora
        if (normalAmphoryActive && currentNormalAmphoryLocation != null) {
            Block block = currentNormalAmphoryLocation.getBlock();
            if (block.getType() == Material.BLACK_SHULKER_BOX) {
                block.setType(Material.AIR);
            }
            removeHologram("normal");
            normalAmphoryActive = false;
            currentNormalAmphoryLocation = null;
        }

        // Remove hard amphora
        if (hardAmphoryActive && currentHardAmphoryLocation != null) {
            Block block = currentHardAmphoryLocation.getBlock();
            if (block.getType() == Material.BLACK_SHULKER_BOX) {
                block.setType(Material.AIR);
            }
            removeHologram("hard");
            hardAmphoryActive = false;
            currentHardAmphoryLocation = null;
        }
    }

    /**
     * Remove hologram by difficulty key.
     */
    private void removeHologram(String difficulty) {
        List<ArmorStand> stands = holograms.remove(difficulty);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
    }

    /**
     * Check if there's currently an active amphora.
     */
    public boolean isAmphoryActive() {
        return normalAmphoryActive || hardAmphoryActive;
    }

    /**
     * Get the current amphora location.
     * Returns the first active amphora location found.
     */
    public Location getCurrentAmphoryLocation() {
        if (normalAmphoryActive && currentNormalAmphoryLocation != null) {
            return currentNormalAmphoryLocation;
        }
        if (hardAmphoryActive && currentHardAmphoryLocation != null) {
            return currentHardAmphoryLocation;
        }
        return null;
    }

    /**
     * Check if a location matches EITHER amphora
     */
    public boolean isAmphoraLocation(Location location) {
        if (normalAmphoryActive && currentNormalAmphoryLocation != null && location.equals(currentNormalAmphoryLocation)) {
            return true;
        }
        if (hardAmphoryActive && currentHardAmphoryLocation != null && location.equals(currentHardAmphoryLocation)) {
            return true;
        }
        return false;
    }

    /**
     * Get next spawn time for hologram display.
     */
    public long getNextSpawnTime() {
        return nextSpawnTime;
    }
}