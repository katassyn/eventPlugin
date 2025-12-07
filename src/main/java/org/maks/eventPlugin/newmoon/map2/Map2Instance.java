package org.maks.eventPlugin.newmoon.map2;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.map2.SchematicHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a solo New Moon Realm instance for a player.
 * Each instance can be either White Realm (Lord Silvanus) or Black Realm (Lord Malachai).
 * Manages the instance lifecycle, entity tracking, cauldron, lord respawn blocks, and cleanup.
 */
public class Map2Instance {

    private final ConfigManager config;
    private final UUID playerId;
    private final UUID instanceId;
    private final Location origin;
    private final CuboidRegion region;
    private final World world;
    private final boolean isHard; // Difficulty: hard mode or normal mode
    private final String realmType; // "white" or "black"

    // Marker locations from schematic
    private final List<Location> mobSpawnLocations = new ArrayList<>();
    private final List<Location> miniBossSpawnLocations = new ArrayList<>();
    private Location lordSpawnLocation; // Lord spawn (DIAMOND_BLOCK)
    private Location playerSpawnLocation; // Player spawn (GOLD_BLOCK)
    private Location cauldronLocation; // Cauldron for Fairy Wood buff (CAULDRON)

    // Lord respawn blocks (3x END_PORTAL_FRAME) with their costs
    private Location lordRespawn1Location; // 100 Fairy Wood
    private Location lordRespawn2Location; // 200 Fairy Wood
    private Location lordRespawn3Location; // 300 Fairy Wood

    // Boss tracking
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private boolean lordSpawned = false;
    private int lordRespawnCount = 0; // 0 = initial, 1, 2, 3 = respawned
    private boolean completed = false;

    // Lifecycle
    private BukkitTask cleanupTask;
    private BukkitTask autoCleanupTimer;
    private final List<BukkitTask> countdownTasks = new ArrayList<>();
    private long createdAt;

    // Auto-cleanup after 10 minutes
    private static final long AUTO_CLEANUP_TIME = 10 * 60 * 20L; // 10 minutes in ticks

    /**
     * Create a new New Moon Map2 instance.
     *
     * @param config The config manager for debug logging
     * @param playerId The player who owns this instance
     * @param origin The original paste origin location
     * @param pasteResult The result from pasting the schematic
     * @param isHard Whether this is hard mode (true) or normal mode (false)
     * @param realmType The realm type: "white" for White Realm (Silvanus), "black" for Black Realm (Malachai)
     */
    public Map2Instance(ConfigManager config, UUID playerId, Location origin,
                       SchematicHandler.PasteResult pasteResult, boolean isHard, String realmType) {
        this.config = config;
        this.playerId = playerId;
        this.instanceId = UUID.randomUUID();
        this.world = origin.getWorld();
        this.createdAt = System.currentTimeMillis();
        this.isHard = isHard;
        this.realmType = realmType;

        // Apply the offset from WorldEdit to get the actual paste location
        Vector appliedOffset = pasteResult.appliedOffset();
        if (appliedOffset != null) {
            this.origin = origin.clone().add(appliedOffset);
        } else {
            this.origin = origin.clone();
        }

        // Calculate actual region bounds using the minimum and maximum offsets
        Vector minOffset = pasteResult.minimumOffset();
        Vector maxOffset = pasteResult.maximumOffset();

        BlockVector3 min;
        BlockVector3 max;

        if (minOffset != null && maxOffset != null) {
            // Use actual offsets from schematic
            min = BlockVector3.at(
                    this.origin.getBlockX() + (int) Math.floor(minOffset.getX()),
                    this.origin.getBlockY() + (int) Math.floor(minOffset.getY()),
                    this.origin.getBlockZ() + (int) Math.floor(minOffset.getZ())
            );
            max = BlockVector3.at(
                    this.origin.getBlockX() + (int) Math.floor(maxOffset.getX()),
                    this.origin.getBlockY() + (int) Math.floor(maxOffset.getY()),
                    this.origin.getBlockZ() + (int) Math.floor(maxOffset.getZ())
            );
        } else {
            // Fallback: use region size
            Vector size = pasteResult.regionSize();
            min = BlockVector3.at(this.origin.getBlockX(), this.origin.getBlockY(), this.origin.getBlockZ());
            max = min.add(
                    (int) Math.ceil(size.getX()),
                    (int) Math.ceil(size.getY()),
                    (int) Math.ceil(size.getZ())
            );
        }

        this.region = new CuboidRegion(BukkitAdapter.adapt(world), min, max);

        // Convert marker offsets to world locations
        convertMarkersToLocations(pasteResult);

        config.debug("[New Moon] Map2Instance (" + realmType + " realm) created with bounds: " +
                "min(" + min.getX() + "," + min.getY() + "," + min.getZ() + ") " +
                "max(" + max.getX() + "," + max.getY() + "," + max.getZ() + ")");
    }

    /**
     * Convert marker offsets from the paste result to actual world locations.
     * New Moon markers:
     * - GOLD_BLOCK: Player spawn
     * - GRASS_BLOCK: Mob spawns
     * - OBSIDIAN: Mini-boss spawns
     * - DIAMOND_BLOCK: Lord spawn
     * - CAULDRON: Fairy Wood buff location
     * - END_PORTAL_FRAME (3x): Lord respawn blocks
     */
    private void convertMarkersToLocations(SchematicHandler.PasteResult pasteResult) {
        // Mob spawn locations
        for (SchematicHandler.BlockOffset offset : pasteResult.mobMarkerOffsets()) {
            Location loc = origin.clone().add(offset.x(), offset.y(), offset.z()).add(0.5, 1.0, 0.5);
            mobSpawnLocations.add(loc);
        }

        // Mini-boss spawn locations
        for (SchematicHandler.BlockOffset offset : pasteResult.miniBossMarkerOffsets()) {
            Location loc = origin.clone().add(offset.x(), offset.y(), offset.z()).add(0.5, 1.0, 0.5);
            miniBossSpawnLocations.add(loc);
        }

        // Lord spawn location (first DIAMOND_BLOCK found)
        if (!pasteResult.finalBossMarkerOffsets().isEmpty()) {
            SchematicHandler.BlockOffset offset = pasteResult.finalBossMarkerOffsets().get(0);
            lordSpawnLocation = origin.clone().add(offset.x(), offset.y(), offset.z()).add(0.5, 1.0, 0.5);
        }

        // Player spawn location (first GOLD_BLOCK found)
        if (!pasteResult.playerSpawnMarkerOffsets().isEmpty()) {
            SchematicHandler.BlockOffset offset = pasteResult.playerSpawnMarkerOffsets().get(0);
            playerSpawnLocation = origin.clone().add(offset.x(), offset.y(), offset.z()).add(0.5, 1.0, 0.5);
        }

        // TODO: Add support for CAULDRON and END_PORTAL_FRAME markers in SchematicHandler
        // For now, these will need to be scanned manually or added to SchematicHandler interface

        config.debug("[New Moon] Markers found: " +
                mobSpawnLocations.size() + " mobs, " +
                miniBossSpawnLocations.size() + " mini-bosses, " +
                (lordSpawnLocation != null ? "1" : "0") + " lord spawn, " +
                (playerSpawnLocation != null ? "1" : "0") + " player spawn");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Location getOrigin() {
        return origin.clone();
    }

    public World getWorld() {
        return world;
    }

    /**
     * Check if this instance is hard mode.
     * @return true if hard mode, false if normal mode
     */
    public boolean isHard() {
        return isHard;
    }

    /**
     * Get the realm type.
     * @return "white" for White Realm (Lord Silvanus), "black" for Black Realm (Lord Malachai)
     */
    public String getRealmType() {
        return realmType;
    }

    public CuboidRegion getRegion() {
        return region;
    }

    /**
     * Get mob spawn locations from schematic markers.
     */
    public List<Location> getMobSpawnLocations() {
        return new ArrayList<>(mobSpawnLocations);
    }

    /**
     * Get mini-boss spawn locations from schematic markers.
     */
    public List<Location> getMiniBossSpawnLocations() {
        return new ArrayList<>(miniBossSpawnLocations);
    }

    /**
     * Get lord spawn location from schematic marker.
     */
    public Location getLordSpawnLocation() {
        return lordSpawnLocation != null ? lordSpawnLocation.clone() : null;
    }

    /**
     * Get player spawn location from schematic marker.
     */
    public Location getPlayerSpawnLocation() {
        return playerSpawnLocation != null ? playerSpawnLocation.clone() : null;
    }

    /**
     * Get cauldron location (for Fairy Wood buff).
     */
    public Location getCauldronLocation() {
        return cauldronLocation != null ? cauldronLocation.clone() : null;
    }

    /**
     * Set cauldron location (called after manual scanning or schematic parsing).
     */
    public void setCauldronLocation(Location location) {
        this.cauldronLocation = location;
    }

    /**
     * Get lord respawn block locations.
     * @return Array of 3 locations: [0] = 100 FW, [1] = 200 FW, [2] = 300 FW
     */
    public Location[] getLordRespawnLocations() {
        return new Location[] {
            lordRespawn1Location != null ? lordRespawn1Location.clone() : null,
            lordRespawn2Location != null ? lordRespawn2Location.clone() : null,
            lordRespawn3Location != null ? lordRespawn3Location.clone() : null
        };
    }

    /**
     * Set lord respawn locations (called after manual scanning or schematic parsing).
     */
    public void setLordRespawnLocations(Location loc1, Location loc2, Location loc3) {
        this.lordRespawn1Location = loc1;
        this.lordRespawn2Location = loc2;
        this.lordRespawn3Location = loc3;
    }

    /**
     * Get current lord respawn count (0 = initial, 1-3 = respawned).
     */
    public int getLordRespawnCount() {
        return lordRespawnCount;
    }

    /**
     * Increment lord respawn count (used when player respawns lord).
     */
    public void incrementLordRespawnCount() {
        this.lordRespawnCount++;
    }

    /**
     * Track a spawned entity in this instance.
     */
    public void trackEntity(UUID entityId) {
        spawnedEntities.add(entityId);
    }

    /**
     * Mark lord as spawned.
     */
    public void setLordSpawned(boolean spawned) {
        this.lordSpawned = spawned;
    }

    public boolean isLordSpawned() {
        return lordSpawned;
    }

    /**
     * Mark instance as completed.
     */
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * Start auto-cleanup timer (10 minutes) with time reminders.
     *
     * @param onCleanup Callback to execute when timer expires
     */
    public void startAutoCleanupTimer(Runnable onCleanup) {
        if (autoCleanupTimer != null) {
            autoCleanupTimer.cancel();
        }

        // Clear any existing countdown tasks
        for (BukkitTask task : countdownTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        countdownTasks.clear();

        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("EventPlugin");

        // 5 minutes remaining reminder (after 5 minutes)
        BukkitTask reminder5min = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§e§l[New Moon] §e⏰ 5 minutes remaining in the realm!");
                player.sendTitle("§e⏰ 5 Minutes", "§7Time remaining", 10, 40, 10);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
        }, 5 * 60 * 20L); // 5 minutes in ticks
        countdownTasks.add(reminder5min);

        // 3 minutes remaining reminder (after 7 minutes)
        BukkitTask reminder3min = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§6§l[New Moon] §6⏰ 3 minutes remaining in the realm!");
                player.sendTitle("§6⏰ 3 Minutes", "§7Time remaining", 10, 40, 10);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            }
        }, 7 * 60 * 20L); // 7 minutes in ticks
        countdownTasks.add(reminder3min);

        // 1 minute remaining reminder (after 9 minutes)
        BukkitTask reminder1min = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§c§l[New Moon] §c⏰ 1 MINUTE remaining in the realm!");
                player.sendTitle("§c⏰ 1 MINUTE", "§7Hurry up!", 10, 60, 10);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
        }, 9 * 60 * 20L); // 9 minutes in ticks
        countdownTasks.add(reminder1min);

        // 10 seconds remaining reminder (after 9 minutes 50 seconds)
        BukkitTask reminder10sec = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§4§l[New Moon] §4⏰ 10 SECONDS LEFT!");
                player.sendTitle("§4⏰ 10 SECONDS", "§c§lGET OUT NOW!", 5, 40, 5);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }
        }, (9 * 60 + 50) * 20L); // 9 minutes 50 seconds in ticks
        countdownTasks.add(reminder10sec);

        // Final cleanup after 10 minutes - teleport player to spawn and clean up instance
        autoCleanupTimer = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c§l[New Moon] §cTime's up! Returning to spawn...");
                        player.sendTitle("§c§lTIME'S UP!", "§7Teleporting to spawn...", 10, 60, 20);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
                    }
                    onCleanup.run();
                },
                AUTO_CLEANUP_TIME
        );

        config.debug("[New Moon] Auto-cleanup timer (10 minutes) started with reminders for " + realmType + " realm instance " + instanceId);
    }

    /**
     * Schedule cleanup task after completion.
     */
    public void scheduleCleanup(long delaySeconds, Runnable onCleanup) {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        // Cancel auto-cleanup timer
        if (autoCleanupTimer != null) {
            autoCleanupTimer.cancel();
            autoCleanupTimer = null;
        }

        cleanupTask = Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                onCleanup,
                delaySeconds * 20L
        );
    }

    /**
     * Cancel any scheduled cleanup tasks.
     */
    public void cancelScheduledCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (autoCleanupTimer != null) {
            autoCleanupTimer.cancel();
            autoCleanupTimer = null;
        }

        for (BukkitTask task : countdownTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        countdownTasks.clear();

        config.debug("[New Moon] Cancelled scheduled cleanup for " + realmType + " realm instance " + instanceId);
    }

    /**
     * Add a countdown task to be tracked.
     */
    public void addCountdownTask(BukkitTask task) {
        countdownTasks.add(task);
    }

    /**
     * Remove all entities spawned in this instance.
     */
    public void removeEntities() {
        for (UUID entityId : spawnedEntities) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        spawnedEntities.clear();

        // Also remove any remaining entities in the region
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;

            Location loc = entity.getLocation();
            if (region.contains(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))) {
                entity.remove();
            }
        }
    }

    /**
     * Clear all blocks in this instance (set to AIR).
     */
    public void clearBlocks() {
        try (EditSession session = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .build()) {

            for (BlockVector3 vec : region) {
                session.setBlock(vec, BlockTypes.AIR.getDefaultState());
            }

        } catch (Exception e) {
            Bukkit.getLogger().warning("[New Moon] Failed to clear instance blocks: " + e.getMessage());
        }
    }

    /**
     * Full cleanup: remove entities, clear blocks, cancel tasks.
     */
    public void cleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (autoCleanupTimer != null) {
            autoCleanupTimer.cancel();
            autoCleanupTimer = null;
        }

        removeEntities();
        clearBlocks();

        config.debug("[New Moon] " + realmType + " realm instance " + instanceId + " cleaned up");
    }

    /**
     * Get the time this instance has been active (in milliseconds).
     */
    public long getActiveTime() {
        return System.currentTimeMillis() - createdAt;
    }
}
