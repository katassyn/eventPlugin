package org.maks.eventPlugin.fullmoon.map2;

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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.maks.eventPlugin.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a solo Blood Moon Arena instance for a player.
 * Manages the instance lifecycle, entity tracking, and cleanup.
 */
public class Map2Instance {

    private final ConfigManager config;
    private final UUID playerId;
    private final UUID instanceId;
    private final Location origin;
    private final CuboidRegion region;
    private final World world;
    private final boolean isHard; // Difficulty: hard mode or normal mode

    // Marker locations from schematic
    private final List<Location> mobSpawnLocations = new ArrayList<>();
    private final List<Location> miniBossSpawnLocations = new ArrayList<>();
    private final List<Location> finalBossSpawnLocations = new ArrayList<>();
    private Location playerSpawnLocation;

    // Boss sequence tracking
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Set<UUID> killedMiniBosses = new HashSet<>();
    private boolean finalBossSpawned = false;
    private boolean completed = false;

    // Lifecycle
    private BukkitTask cleanupTask;
    private BukkitTask autoCleanupTimer;
    private final List<BukkitTask> countdownTasks = new ArrayList<>(); // For countdown messages
    private long createdAt;

    // Auto-cleanup after 15 minutes
    private static final long AUTO_CLEANUP_TIME = 15 * 60 * 20L; // 15 minutes in ticks

    /**
     * Create a new Map2 instance.
     *
     * @param config The config manager for debug logging
     * @param playerId The player who owns this instance
     * @param origin The original paste origin location
     * @param pasteResult The result from pasting the schematic
     * @param isHard Whether this is hard mode (true) or normal mode (false)
     */
    public Map2Instance(ConfigManager config, UUID playerId, Location origin, SchematicHandler.PasteResult pasteResult, boolean isHard) {
        this.config = config;
        this.playerId = playerId;
        this.instanceId = UUID.randomUUID();
        this.world = origin.getWorld();
        this.createdAt = System.currentTimeMillis();
        this.isHard = isHard;

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

        config.debug("[Full Moon] Map2Instance created with actual bounds: " +
                "min(" + min.getX() + "," + min.getY() + "," + min.getZ() + ") " +
                "max(" + max.getX() + "," + max.getY() + "," + max.getZ() + ")");
    }

    /**
     * Convert marker offsets from the paste result to actual world locations.
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

        // Final boss spawn locations
        for (SchematicHandler.BlockOffset offset : pasteResult.finalBossMarkerOffsets()) {
            Location loc = origin.clone().add(offset.x(), offset.y(), offset.z()).add(0.5, 1.0, 0.5);
            finalBossSpawnLocations.add(loc);
        }

        // Player spawn location
        if (!pasteResult.playerSpawnMarkerOffsets().isEmpty()) {
            SchematicHandler.BlockOffset offset = pasteResult.playerSpawnMarkerOffsets().get(0);
            playerSpawnLocation = origin.clone().add(offset.x(), offset.y(), offset.z()).add(0.5, 1.0, 0.5);
        }

        config.debug("[Full Moon] Markers found: " +
                mobSpawnLocations.size() + " mobs, " +
                miniBossSpawnLocations.size() + " mini-bosses, " +
                finalBossSpawnLocations.size() + " final bosses, " +
                (playerSpawnLocation != null ? "1" : "0") + " player spawn");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Location getOrigin() {
        return origin;
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
     * Get final boss spawn locations from schematic markers.
     */
    public List<Location> getFinalBossSpawnLocations() {
        return new ArrayList<>(finalBossSpawnLocations);
    }

    /**
     * Get player spawn location from schematic marker.
     */
    public Location getPlayerSpawnLocation() {
        return playerSpawnLocation != null ? playerSpawnLocation.clone() : null;
    }

    /**
     * Get a location relative to the origin.
     *
     * @param relX Relative X coordinate
     * @param relY Relative Y coordinate
     * @param relZ Relative Z coordinate
     * @return The absolute location
     */
    public Location getRelativeLocation(int relX, int relY, int relZ) {
        return origin.clone().add(relX, relY, relZ);
    }

    /**
     * Track a spawned entity in this instance.
     */
    public void trackEntity(UUID entityId) {
        spawnedEntities.add(entityId);
    }

    /**
     * Mark a mini-boss as killed.
     */
    public void markMiniBossKilled(UUID miniBossId) {
        killedMiniBosses.add(miniBossId);
    }

    /**
     * Check if all 3 mini-bosses have been killed.
     */
    public boolean areAllMiniBossesKilled() {
        return killedMiniBosses.size() >= 3;
    }

    /**
     * Get the number of killed mini-bosses.
     */
    public int getKilledMiniBossCount() {
        return killedMiniBosses.size();
    }

    /**
     * Mark final boss as spawned.
     */
    public void setFinalBossSpawned(boolean spawned) {
        this.finalBossSpawned = spawned;
    }

    public boolean isFinalBossSpawned() {
        return finalBossSpawned;
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
     * Start auto-cleanup timer (15 minutes).
     * This prevents players from blocking slots indefinitely.
     *
     * @param onCleanup Callback to execute when timer expires
     */
    public void startAutoCleanupTimer(Runnable onCleanup) {
        if (autoCleanupTimer != null) {
            autoCleanupTimer.cancel();
        }

        // Silent expiration warnings removed

        // Final cleanup after 15 minutes
        autoCleanupTimer = Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        // Silent cleanup - no messages
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
                    }
                    onCleanup.run();
                },
                AUTO_CLEANUP_TIME
        );

        config.debug("[Full Moon] Auto-cleanup timer started for instance " + instanceId + " (15 minutes)");
    }

    /**
     * Schedule cleanup task after completion.
     *
     * @param delaySeconds Delay in seconds before cleanup
     */
    public void scheduleCleanup(long delaySeconds, Runnable onCleanup) {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        // Cancel auto-cleanup timer since we're scheduling manual cleanup
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
     * Cancel any scheduled cleanup tasks (manual cleanup or auto-cleanup).
     * Useful when player dies or leaves before cleanup completes.
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

        // Cancel all countdown message tasks
        for (BukkitTask task : countdownTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        countdownTasks.clear();

        config.debug("[Full Moon] Cancelled scheduled cleanup for instance " + instanceId);
    }

    /**
     * Add a countdown task to be tracked (so it can be cancelled later).
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
            Bukkit.getLogger().warning("[Full Moon] Failed to clear instance blocks: " + e.getMessage());
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

        config.debug("[Full Moon] Instance " + instanceId + " cleaned up");
    }

    /**
     * Get the time this instance has been active (in milliseconds).
     */
    public long getActiveTime() {
        return System.currentTimeMillis() - createdAt;
    }
}
