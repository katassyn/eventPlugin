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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a solo Blood Moon Arena instance for a player.
 * Manages the instance lifecycle, entity tracking, and cleanup.
 */
public class Map2Instance {

    private final UUID playerId;
    private final UUID instanceId;
    private final Location origin;
    private final CuboidRegion region;
    private final World world;

    // Boss sequence tracking
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Set<UUID> killedMiniBosses = new HashSet<>();
    private boolean finalBossSpawned = false;
    private boolean completed = false;

    // Lifecycle
    private BukkitTask cleanupTask;
    private BukkitTask autoCleanupTimer;
    private long createdAt;

    // Auto-cleanup after 15 minutes
    private static final long AUTO_CLEANUP_TIME = 15 * 60 * 20L; // 15 minutes in ticks

    /**
     * Create a new Map2 instance.
     *
     * @param playerId The player who owns this instance
     * @param origin The origin location (bottom corner of the schematic)
     * @param size The size of the schematic (width, height, depth)
     */
    public Map2Instance(UUID playerId, Location origin, BlockVector3 size) {
        this.playerId = playerId;
        this.instanceId = UUID.randomUUID();
        this.origin = origin;
        this.world = origin.getWorld();
        this.createdAt = System.currentTimeMillis();

        // Create region for this instance
        BlockVector3 min = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        BlockVector3 max = min.add(size);
        this.region = new CuboidRegion(BukkitAdapter.adapt(world), min, max);
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

    public CuboidRegion getRegion() {
        return region;
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

        // Warning at 5 minutes remaining
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§e§l[Full Moon] §7Your arena instance will expire in §c5 minutes§7!");
                    }
                },
                (AUTO_CLEANUP_TIME - (5 * 60 * 20L))  // 10 minutes in
        );

        // Warning at 1 minute remaining
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c§l[Full Moon] §7Your arena instance will expire in §c1 minute§7!");
                    }
                },
                (AUTO_CLEANUP_TIME - (1 * 60 * 20L))  // 14 minutes in
        );

        // Final cleanup after 15 minutes
        autoCleanupTimer = Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c§l[Full Moon] §cYour arena instance has expired (15 minutes)!");
                        player.sendMessage("§7Teleporting you to spawn...");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
                    }
                    onCleanup.run();
                },
                AUTO_CLEANUP_TIME
        );

        Bukkit.getLogger().info("[Full Moon] Auto-cleanup timer started for instance " + instanceId + " (15 minutes)");
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

        Bukkit.getLogger().info("[Full Moon] Instance " + instanceId + " cleaned up");
    }

    /**
     * Get the time this instance has been active (in milliseconds).
     */
    public long getActiveTime() {
        return System.currentTimeMillis() - createdAt;
    }
}
