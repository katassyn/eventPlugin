package org.maks.eventPlugin.newmoon.map2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.config.ConfigManager;

import java.util.List;

/**
 * Manages mob spawning for New Moon Map 2 instances.
 * Handles spawning of normal mobs, mini-bosses, and Lords using
 * marker locations scanned from the schematic during paste.
 *
 * Lords spawn automatically at DIAMOND_BLOCK markers on instance creation (max 4 per instance).
 * Players can respawn Lords using the lord respawn blocks (END_PORTAL_FRAME) for up to 3 additional spawns.
 */
public class Map2MobSpawner {

    private final ConfigManager config;

    public Map2MobSpawner(ConfigManager config) {
        this.config = config;
    }

    /**
     * Initialize mob spawning for an instance.
     * Spawns normal mobs, mini-bosses, and the initial Lord based on marker locations.
     *
     * @param instance The Map2 instance
     * @param player The player
     */
    public void initializeMobSpawning(Map2Instance instance, Player player) {
        boolean isHard = instance.isHard();
        String realmType = instance.getRealmType(); // "white" or "black"

        // Spawn normal mobs using marker locations
        spawnNormalMobs(instance, player, isHard);

        // Spawn mini-bosses using marker locations
        spawnMiniBosses(instance, player, isHard);

        // Spawn initial Lord at DIAMOND_BLOCK location
        spawnInitialLord(instance, realmType, isHard);

        config.debug("[New Moon] Initialized mob spawning for " + realmType + " realm instance " + instance.getInstanceId());
    }

    /**
     * Spawn normal mobs using marker locations from instance.
     * Only spawns 24% of available markers to avoid overwhelming players.
     * Checks for empty space above spawn location before spawning.
     *
     * Mobs spawned (Map 2):
     * - The Lord's Squire (50%)
     * - Lord's Legionnaire (50%)
     *
     * @param instance The Map2 instance
     * @param player The player
     * @param isHard Whether this is hard mode
     */
    private void spawnNormalMobs(Map2Instance instance, Player player, boolean isHard) {
        // Get mob spawn locations from instance
        List<Location> mobLocations = instance.getMobSpawnLocations();

        if (mobLocations.isEmpty()) {
            Bukkit.getLogger().warning("[New Moon] No mob spawn markers found in instance " + instance.getInstanceId() + "!");
            return;
        }

        // Calculate how many mobs to spawn (24% of markers, increased from 20%)
        int totalMarkers = mobLocations.size();
        int mobsToSpawn = Math.max(1, (int) (totalMarkers * 0.24)); // At least 1 mob

        int spawnedSquires = 0;
        int spawnedLegionnaires = 0;
        int skippedDueToNoSpace = 0;

        // Spawn mobs at evenly distributed marker locations
        int step = Math.max(1, totalMarkers / mobsToSpawn);
        for (int i = 0; i < totalMarkers && (spawnedSquires + spawnedLegionnaires) < mobsToSpawn; i += step) {
            Location spawnLoc = mobLocations.get(i).clone();

            // Check if there is enough empty space above the spawn location (3 blocks high)
            if (!hasEmptySpaceAbove(spawnLoc, 3)) {
                skippedDueToNoSpace++;
                continue;
            }

            // Alternate between The Lord's Squire and Lord's Legionnaire
            String mobType;
            if ((spawnedSquires + spawnedLegionnaires) % 2 == 0) {
                // 50% - The Lord's Squire
                mobType = isHard ? "lords_squire_hard" : "lords_squire_normal";
                spawnedSquires++;
            } else {
                // 50% - Lord's Legionnaire
                mobType = isHard ? "lords_legionnaire_hard" : "lords_legionnaire_normal";
                spawnedLegionnaires++;
            }

            // Spawn via command - entity tracking happens in MythicMobSpawnEvent
            spawnMythicMob(mobType, spawnLoc);
        }

        config.debug("[New Moon] Spawned " + spawnedSquires + " Lord's Squires and "
                + spawnedLegionnaires + " Lord's Legionnaires "
                + "(24% of " + totalMarkers + " markers, " + skippedDueToNoSpace + " skipped due to no space) "
                + "in instance " + instance.getInstanceId());
    }

    /**
     * Spawn mini-bosses using marker locations from instance.
     * All OBSIDIAN markers spawn the same mini-boss type: Lord's Guard.
     *
     * Mini-boss:
     * - Lord's Guard (spawns at all OBSIDIAN markers, can be 1-5+)
     *
     * @param instance The Map2 instance
     * @param player The player
     * @param isHard Whether this is hard mode
     */
    private void spawnMiniBosses(Map2Instance instance, Player player, boolean isHard) {
        // Get mini-boss spawn locations from instance
        List<Location> miniBossLocations = instance.getMiniBossSpawnLocations();

        if (miniBossLocations.isEmpty()) {
            Bukkit.getLogger().warning("[New Moon] No mini-boss spawn markers found in instance " + instance.getInstanceId() + "!");
            player.sendMessage("§c§l[New Moon] §cWarning: No mini-boss spawn points found!");
            return;
        }

        // Spawn Lord's Guard at all OBSIDIAN markers
        String mobType = isHard ? "lords_guard_hard" : "lords_guard_normal";
        int spawned = 0;

        for (Location spawnLoc : miniBossLocations) {
            // Spawn via command - entity tracking happens in MythicMobSpawnEvent
            spawnMythicMob(mobType, spawnLoc.clone());
            spawned++;
        }

        config.debug("[New Moon] Spawned " + spawned + " Lord's Guard mini-bosses in instance " + instance.getInstanceId());
    }

    /**
     * Spawn initial Lord at the DIAMOND_BLOCK location when instance is created.
     * Max 4 Lords can spawn per instance (one at each DIAMOND_BLOCK marker).
     *
     * @param instance The instance
     * @param realmType "white" or "black"
     * @param isHard Whether this is hard mode
     */
    private void spawnInitialLord(Map2Instance instance, String realmType, boolean isHard) {
        Location lordSpawnLoc = instance.getLordSpawnLocation();

        if (lordSpawnLoc == null) {
            Bukkit.getLogger().warning("[New Moon] No lord spawn location (DIAMOND_BLOCK) found in instance " + instance.getInstanceId());
            return;
        }

        // Determine lord type based on realm
        String lordType = realmType.equals("white") ? "lord_silvanus" : "lord_malachai";
        String mobType = lordType + (isHard ? "_hard" : "_normal");

        // Spawn the Lord
        spawnMythicMob(mobType, lordSpawnLoc);
        instance.setLordSpawned(true);

        config.debug("[New Moon] Spawned initial Lord (" + mobType + ") in " + realmType + " realm instance " + instance.getInstanceId());
    }

    /**
     * Spawn a lord at the specified location.
     * Called when player uses lord respawn blocks (END_PORTAL_FRAME).
     *
     * @param instance The instance
     * @param lordType "lord_silvanus" or "lord_malachai"
     * @param isHard Whether to spawn hard mode
     * @return True if spawn was successful
     */
    public boolean spawnLord(Map2Instance instance, String lordType, boolean isHard) {
        Location lordSpawnLoc = instance.getLordSpawnLocation();

        if (lordSpawnLoc == null) {
            Bukkit.getLogger().warning("[New Moon] No lord spawn location found in instance " + instance.getInstanceId());
            return false;
        }

        String mobType = lordType + (isHard ? "_hard" : "_normal");
        spawnMythicMob(mobType, lordSpawnLoc);

        instance.setLordSpawned(true);
        config.debug("[New Moon] Spawned lord (" + mobType + ") in instance " + instance.getInstanceId());

        return true;
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
            Bukkit.getLogger().warning("[New Moon] Failed to spawn MythicMob: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if there is empty space above a location.
     * Checks if the specified number of blocks above the location are passable (air or transparent).
     *
     * @param location The location to check above
     * @param blocksHigh How many blocks above to check
     * @return true if all blocks above are passable, false otherwise
     */
    private boolean hasEmptySpaceAbove(Location location, int blocksHigh) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // Check each block above the spawn location
        for (int i = 0; i < blocksHigh; i++) {
            Location checkLoc = location.clone().add(0, i, 0);
            Material material = checkLoc.getBlock().getType();

            // If block is not passable (solid block), there's no space
            if (material.isSolid() && !material.isTransparent()) {
                return false;
            }
        }

        return true;
    }
}
