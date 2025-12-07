package org.maks.eventPlugin.fullmoon.map2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.config.ConfigManager;

import java.util.List;
import java.util.UUID;

/**
 * Manages the boss sequence for Map 2 instances.
 * Handles spawning of 3 mini-bosses and the final boss using
 * marker locations scanned from the schematic during paste.
 */
public class Map2BossSequenceManager {

    private final ConfigManager config;

    // Scoreboard tags for tracking
    private static final String MINI_BOSS_TAG = "fullmoon_miniboss";
    private static final String FINAL_BOSS_TAG = "fullmoon_finalboss";

    public Map2BossSequenceManager(ConfigManager config) {
        this.config = config;
    }

    /**
     * Initialize the boss sequence for an instance.
     * Uses pre-scanned marker locations from the instance.
     *
     * @param instance The Map2 instance
     * @param player The player
     */
    public void initializeBossSequence(Map2Instance instance, Player player) {
        boolean isHard = instance.isHard();

        // Spawn normal mobs using marker locations from instance
        spawnNormalMobs(instance, player, isHard);

        // Get mini-boss spawn locations from instance
        List<Location> miniBossLocations = instance.getMiniBossSpawnLocations();

        if (miniBossLocations.size() < 3) {
            player.sendMessage("§c§l[Full Moon] §cWarning: Less than 3 mini-boss spawn points found!");
            Bukkit.getLogger().warning("[Full Moon] Instance " + instance.getInstanceId() + " has only " + miniBossLocations.size() + " mini-boss markers!");
        }

        // Spawn up to 3 mini-bosses
        int spawned = 0;
        String mobType = isHard ? "werewolf_blood_mage_disciple_hard" : "werewolf_blood_mage_disciple_normal";

        for (int i = 0; i < Math.min(3, miniBossLocations.size()); i++) {
            Location spawnLoc = miniBossLocations.get(i).clone();

            // Spawn via command - entity tracking happens in MythicMobSpawnEvent
            spawnMythicMob(mobType, spawnLoc);
            spawned++;
        }

        // Silent initialization - no messages

        config.debug("[Full Moon] Spawned " + spawned + " mini-bosses in instance " + instance.getInstanceId());
    }

    /**
     * Spawn normal mobs using marker locations from instance.
     * Only spawns 2/10 (20%) of available markers to avoid overwhelming players.
     *
     * @param instance The Map2 instance
     * @param player The player
     * @param isHard Whether this is hard mode
     */
    private void spawnNormalMobs(Map2Instance instance, Player player, boolean isHard) {
        // Get mob spawn locations from instance
        List<Location> mobLocations = instance.getMobSpawnLocations();

        if (mobLocations.isEmpty()) {
            Bukkit.getLogger().warning("[Full Moon] No mob spawn markers found in instance " + instance.getInstanceId() + "!");
            return;
        }

        // Calculate how many mobs to spawn (2/10 = 20% of markers)
        int totalMarkers = mobLocations.size();
        int mobsToSpawn = Math.max(1, (totalMarkers * 2) / 10); // At least 1 mob

        int spawnedBloodyWerewolf = 0;
        int spawnedBloodSludgeling = 0;

        // Spawn mobs at evenly distributed marker locations (every 5th marker)
        for (int i = 0; i < totalMarkers && (i / 5) < mobsToSpawn; i += 5) {
            Location spawnLoc = mobLocations.get(i).clone();

            // Alternate between Bloody Werewolf and Blood Sludgeling
            String mobType;
            if ((i / 5) % 2 == 0) {
                mobType = isHard ? "bloody_werewolf_hard" : "bloody_werewolf_normal";
                spawnedBloodyWerewolf++;
            } else {
                // blood_sludgeling - using existing mob from bloodborne.yml (same for both normal/hard)
                mobType = "blood_sludgeling";
                spawnedBloodSludgeling++;
            }

            // Spawn via command - entity tracking happens in MythicMobSpawnEvent
            spawnMythicMob(mobType, spawnLoc);
        }

        config.debug("[Full Moon] Spawned " + spawnedBloodyWerewolf + " Bloody Werewolves and "
                + spawnedBloodSludgeling + " Blood Sludgelings (2/10 of " + totalMarkers + " markers) in instance " + instance.getInstanceId());
    }

    /**
     * Handle a mini-boss death in an instance.
     * If all 3 are killed, spawn the final boss.
     *
     * @param instance The instance
     * @param miniBossId The UUID of the killed mini-boss
     * @param player The player
     */
    public void handleMiniBossDeath(Map2Instance instance, UUID miniBossId, Player player) {
        instance.markMiniBossKilled(miniBossId);

        int remaining = 3 - instance.getKilledMiniBossCount();

        if (remaining > 0) {
            // Silent - no messages
        } else {
            // All mini-bosses killed - spawn final boss
            spawnFinalBoss(instance, player);
        }
    }

    /**
     * Spawn the final boss (Sanguis) using marker location from instance.
     */
    private void spawnFinalBoss(Map2Instance instance, Player player) {
        boolean isHard = instance.isHard();
        // Get final boss spawn locations from instance
        List<Location> finalBossLocations = instance.getFinalBossSpawnLocations();

        if (finalBossLocations.isEmpty()) {
            player.sendMessage("§c§l[Full Moon] §cError: No final boss spawn marker found!");
            Bukkit.getLogger().warning("[Full Moon] No final boss spawn marker found in instance " + instance.getInstanceId());
            return;
        }

        Location spawnLoc = finalBossLocations.get(0).clone();
        String mobType = isHard ? "sanguis_hard" : "sanguis_normal";

        // Spawn via command - entity tracking happens in MythicMobSpawnEvent
        spawnMythicMob(mobType, spawnLoc);
        instance.setFinalBossSpawned(true);

        // Silent spawn - no messages

        config.debug("[Full Moon] Spawned final boss in instance " + instance.getInstanceId());
    }

    /**
     * Handle final boss death - triggers cleanup sequence.
     */
    public void handleFinalBossDeath(Map2Instance instance, Player player) {
        instance.setCompleted(true);

        // Silent victory - no messages

        // Start 60 second countdown
        startCleanupCountdown(instance, player);
    }

    /**
     * Start 60-second countdown before teleport and cleanup.
     */
    private void startCleanupCountdown(Map2Instance instance, Player player) {
        // Silent countdown - no messages

        // Teleport to spawn at 60 seconds
        instance.scheduleCleanup(60L, () -> {
            if (player.isOnline()) {
                // Execute /spawn as console (player doesn't have permission)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
            }

            // Cleanup instance
            instance.cleanup();
            config.debug("[Full Moon] Instance " + instance.getInstanceId() + " completed and cleaned up");
        });
    }

    /**
     * Gets the player spawn location from the instance markers.
     *
     * @param instance The instance to get spawn from.
     * @return The Location of the spawn marker, or null if not found.
     */
    public Location getPlayerSpawn(Map2Instance instance) {
        Location spawnLocation = instance.getPlayerSpawnLocation();
        if (spawnLocation == null) {
            Bukkit.getLogger().warning("[Full Moon] No player_spawn marker found in instance " + instance.getInstanceId());
        }
        return spawnLocation;
    }

    /**
     * Spawn a MythicMob at a location using console command.
     * Returns null since we can't directly track the entity via command spawn.
     */
    private LivingEntity spawnMythicMob(String mobType, Location location) {
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

            // Return null - entity will be tracked via MythicMobSpawnEvent listener
            return null;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Full Moon] Failed to spawn MythicMob: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if an entity is a mini-boss.
     */
    public boolean isMiniBoss(LivingEntity entity) {
        return entity.getScoreboardTags().contains(MINI_BOSS_TAG);
    }

    /**
     * Check if an entity is the final boss.
     */
    public boolean isFinalBoss(LivingEntity entity) {
        return entity.getScoreboardTags().contains(FINAL_BOSS_TAG);
    }

    /**
     * Get the instance ID from an entity's tags.
     */
    public UUID getInstanceIdFromEntity(LivingEntity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("fullmoon_instance_")) {
                try {
                    return UUID.fromString(tag.substring("fullmoon_instance_".length()));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}