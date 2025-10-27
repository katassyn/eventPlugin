package org.maks.eventPlugin.fullmoon.map2;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the boss sequence for Map 2 instances.
 * Handles spawning of 3 mini-bosses on obsidian blocks,
 * then the final boss on diamond block after all mini-bosses are killed.
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
     * Scans for marker blocks and spawns mini-bosses.
     *
     * @param instance The Map2 instance
     * @param player The player
     * @param isHard Whether this is hard mode
     */
    public void initializeBossSequence(Map2Instance instance, Player player, boolean isHard) {
        // Spawn normal mobs on grass blocks
        spawnNormalMobs(instance, player, isHard);

        // --- POCZĄTEK POPRAWKI (Skanowanie Bloków) ---
        // Scan for mini-boss spawns (np. OBSIDIAN) z configu
        List<Location> miniBossLocations = scanForBlocks(instance, "mini_boss");
        // --- KONIEC POPRAWKI ---

        if (miniBossLocations.size() < 3) {
            player.sendMessage("§c§l[Full Moon] §cWarning: Less than 3 mini-boss spawn points found!");
            Bukkit.getLogger().warning("[Full Moon] Instance " + instance.getInstanceId() + " has only " + miniBossLocations.size() + " mini-boss blocks!");
        }

        // Spawn up to 3 mini-bosses
        int spawned = 0;
        String mobType = isHard ? "werewolf_blood_mage_disciple_hard" : "werewolf_blood_mage_disciple_normal";

        for (int i = 0; i < Math.min(3, miniBossLocations.size()); i++) {
            Location spawnLoc = miniBossLocations.get(i).clone().add(0.5, 1, 0.5);

            // Spawn via command - entity tracking happens in MythicMobSpawnEvent
            spawnMythicMob(mobType, spawnLoc);
            spawned++;
        }

        player.sendMessage("§c§l[Full Moon] §eBlood Moon Arena initialized!");
        player.sendMessage("§7Defeat the §53 Blood Mage Disciples §7to summon the final boss!");

        Bukkit.getLogger().info("[Full Moon] Spawned " + spawned + " mini-bosses in instance " + instance.getInstanceId());
    }

    /**
     * Spawn normal mobs on grass blocks in the arena.
     *
     * @param instance The Map2 instance
     * @param player The player
     * @param isHard Whether this is hard mode
     */
    private void spawnNormalMobs(Map2Instance instance, Player player, boolean isHard) {
        // --- POCZĄTEK POPRAWKI (Skanowanie Bloków) ---
        // Scan for mob spawns (np. GRASS_BLOCK) z configu
        List<Location> grassLocations = scanForBlocks(instance, "mob_spawn");
        // --- KONIEC POPRAWKI ---

        if (grassLocations.isEmpty()) {
            Bukkit.getLogger().warning("[Full Moon] No mob spawn blocks found in instance " + instance.getInstanceId() + " for normal mob spawns!");
            return;
        }

        int spawnedBloodyWerewolf = 0;
        int spawnedBloodSludgeling = 0;

        // Spawn mobs on grass blocks (alternating between types)
        for (int i = 0; i < grassLocations.size(); i++) {
            Location spawnLoc = grassLocations.get(i).clone().add(0.5, 1, 0.5);

            // Alternate between Bloody Werewolf and Blood Sludgeling
            String mobType;
            if (i % 2 == 0) {
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

        Bukkit.getLogger().info("[Full Moon] Spawned " + spawnedBloodyWerewolf + " Bloody Werewolves and "
                + spawnedBloodSludgeling + " Blood Sludgelings in instance " + instance.getInstanceId());
    }

    /**
     * Handle a mini-boss death in an instance.
     * If all 3 are killed, spawn the final boss.
     *
     * @param instance The instance
     * @param miniBossId The UUID of the killed mini-boss
     * @param player The player
     * @param isHard Whether this is hard mode
     */
    public void handleMiniBossDeath(Map2Instance instance, UUID miniBossId, Player player, boolean isHard) {
        instance.markMiniBossKilled(miniBossId);

        int remaining = 3 - instance.getKilledMiniBossCount();

        if (remaining > 0) {
            player.sendMessage("§5§l[Full Moon] §dBlood Mage Disciple defeated! §7(" + remaining + " remaining)");
            player.sendTitle("§5§lDisciple Slain", "§7" + remaining + " remaining", 10, 40, 10);
        } else {
            // All mini-bosses killed - spawn final boss
            spawnFinalBoss(instance, player, isHard);
        }
    }

    /**
     * Spawn the final boss (Sanguis) on the diamond block.
     */
    private void spawnFinalBoss(Map2Instance instance, Player player, boolean isHard) {
        // --- POCZĄTEK POPRAWKI (Skanowanie Bloków) ---
        // Find final boss block (np. DIAMOND_BLOCK) z configu
        List<Location> diamondLocations = scanForBlocks(instance, "final_boss");
        // --- KONIEC POPRAWKI ---

        if (diamondLocations.isEmpty()) {
            player.sendMessage("§c§l[Full Moon] §cError: No final boss spawn block found!");
            Bukkit.getLogger().warning("[Full Moon] No final boss spawn block found in instance " + instance.getInstanceId());
            return;
        }

        Location spawnLoc = diamondLocations.get(0).clone().add(0.5, 1, 0.5);
        String mobType = isHard ? "sanguis_hard" : "sanguis_normal";

        // Spawn via command - entity tracking happens in MythicMobSpawnEvent
        spawnMythicMob(mobType, spawnLoc);
        instance.setFinalBossSpawned(true);

        // Epic announcement
        player.sendMessage("§4§l[Full Moon] §cSANGUIS THE BLOOD MAGE HAS RISEN!");
        player.sendTitle("§4§lSANGUIS AWAKENS", "§cThe Blood Mage emerges!", 10, 60, 20);

        Bukkit.getLogger().info("[Full Moon] Spawned final boss in instance " + instance.getInstanceId());
    }

    /**
     * Handle final boss death - triggers cleanup sequence.
     */
    public void handleFinalBossDeath(Map2Instance instance, Player player) {
        instance.setCompleted(true);

        player.sendMessage("§a§l[Full Moon] §aYOU HAVE DEFEATED SANGUIS THE BLOOD MAGE!");
        player.sendTitle("§a§lVICTORY!", "§eBlood Moon Arena Complete", 10, 80, 20);

        // Start 60 second countdown
        startCleanupCountdown(instance, player);
    }

    /**
     * Start 60-second countdown before teleport and cleanup.
     */
    private void startCleanupCountdown(Map2Instance instance, Player player) {
        // Messages at specific intervals
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> player.sendMessage("§e§l[Full Moon] §7Arena will close in §e60 seconds§7..."),
                0L
        );

        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> player.sendMessage("§e§l[Full Moon] §7Arena will close in §e30 seconds§7..."),
                30L * 20L
        );

        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> player.sendMessage("§e§l[Full Moon] §7Arena will close in §e10 seconds§7..."),
                50L * 20L
        );

        // Countdown 5,4,3,2,1
        for (int i = 5; i >= 1; i--) {
            final int count = i;
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("EventPlugin"),
                    () -> {
                        player.sendMessage("§c§l" + count);
                        player.sendTitle("§c§l" + count, "", 0, 20, 5);
                    },
                    (60L - count) * 20L
            );
        }

        // Teleport to spawn at 60 seconds
        instance.scheduleCleanup(60L, () -> {
            if (player.isOnline()) {
                // Execute /spawn as console (player doesn't have permission)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
                player.sendMessage("§a§l[Full Moon] §aYou have been returned to spawn.");
            }

            // Cleanup instance
            instance.cleanup();
            Bukkit.getLogger().info("[Full Moon] Instance " + instance.getInstanceId() + " completed and cleaned up");
        });
    }

    // --- POCZĄTEK POPRAWKI (Skanowanie Bloków) ---

    /**
     * Gets the player spawn location by scanning for the configured block.
     * @param instance The instance to scan.
     * @return The Location of the spawn block, or null if not found.
     */
    public Location getPlayerSpawn(Map2Instance instance) {
        List<Location> spawnLocations = scanForBlocks(instance, "player_spawn");
        if (spawnLocations.isEmpty()) {
            Bukkit.getLogger().warning("[Full Moon] No player_spawn block found in instance " + instance.getInstanceId());
            return null;
        }
        if (spawnLocations.size() > 1) {
            Bukkit.getLogger().warning("[Full Moon] Multiple player_spawn blocks found in instance " + instance.getInstanceId() + ". Using the first one.");
        }
        return spawnLocations.get(0);
    }

    /**
     * Scan an instance region for specific block types based on config key.
     * @param instance The instance to scan.
     * @param configKey The key from config.yml (e.g., "player_spawn", "mini_boss")
     * @return List of locations where the block was found.
     */
    private List<Location> scanForBlocks(Map2Instance instance, String configKey) {
        List<Location> locations = new ArrayList<>();

        // Get material name from config
        String materialName = config.getSection("full_moon.schematic.scan_blocks").getString(configKey);
        if (materialName == null) {
            Bukkit.getLogger().severe("[Full Moon] Config missing for full_moon.schematic.scan_blocks." + configKey);
            return locations;
        }

        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null) {
            Bukkit.getLogger().severe("[Full Moon] Invalid material name in config: " + materialName + " for key " + configKey);
            return locations;
        }

        Region region = instance.getRegion();
        World world = instance.getWorld();

        if (region == null) {
            Bukkit.getLogger().severe("[Full Moon] Instance region is null during scan! Instance ID: " + instance.getInstanceId());
            return locations;
        }

        try {
            for (BlockVector3 vec : region) {
                Block block = world.getBlockAt(vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
                if (block.getType() == material) {
                    locations.add(block.getLocation());
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Full Moon] Error during block scan for " + configKey + ": " + e.getMessage());
            e.printStackTrace();
        }

        return locations;
    }
    // --- KONIEC POPRAWKI ---

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