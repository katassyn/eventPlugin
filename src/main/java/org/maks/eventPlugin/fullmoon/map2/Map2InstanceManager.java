package org.maks.eventPlugin.fullmoon.map2;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.maks.eventPlugin.config.ConfigManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Blood Moon Arena (Map 2) solo instances.
 * Handles FAWE schematic loading, instance creation, and space allocation.
 */
public class Map2InstanceManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SchematicHandler schematicHandler;

    // Active instances: Player UUID -> Instance
    private final Map<UUID, Map2Instance> activeInstances = new ConcurrentHashMap<>();

    // Occupied coordinate ranges (to prevent overlap)
    private final Set<CoordinateRange> occupiedRanges = new HashSet<>();

    // Instance spacing and limits from config
    private int spacing = 100;
    private int maxInstances = 4;
    private String schematicName = "blood_moon_arena";

    // Spawn area from config
    private int minX = 547;
    private int minY = -37;
    private int minZ = -863;
    private int maxX = 1154;
    private int maxZ = -2100;
    private String worldName = "world";

    // Marker materials from config
    private Material mobMarker = Material.GRASS_BLOCK;
    private Material miniBossMarker = Material.OBSIDIAN;
    private Material finalBossMarker = Material.DIAMOND_BLOCK;
    private Material playerSpawnMarker = Material.GOLD_BLOCK;

    public Map2InstanceManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.schematicHandler = new WorldEditSchematicHandler(config);
        loadConfig();
    }

    private void loadConfig() {
        var schematicSection = config.getSection("full_moon.schematic");
        if (schematicSection != null) {
            schematicName = schematicSection.getString("file_name", "blood_moon_arena");
            spacing = schematicSection.getInt("spacing", 100);
            maxInstances = schematicSection.getInt("max_instances", 4);

            // Load spawn area
            var spawnAreaSection = schematicSection.getConfigurationSection("spawn_area");
            if (spawnAreaSection != null) {
                minX = spawnAreaSection.getInt("min_x", 547);
                minY = spawnAreaSection.getInt("min_y", -37);
                minZ = spawnAreaSection.getInt("min_z", -863);
                maxX = spawnAreaSection.getInt("max_x", 1154);
                maxZ = spawnAreaSection.getInt("max_z", -2100);
                worldName = spawnAreaSection.getString("world", "world");
            }

            // Load marker materials
            var scanBlocksSection = schematicSection.getConfigurationSection("scan_blocks");
            if (scanBlocksSection != null) {
                String mobMarkerName = scanBlocksSection.getString("mob_spawn", "GRASS_BLOCK");
                String miniBossMarkerName = scanBlocksSection.getString("mini_boss", "OBSIDIAN");
                String finalBossMarkerName = scanBlocksSection.getString("final_boss", "DIAMOND_BLOCK");
                String playerSpawnMarkerName = scanBlocksSection.getString("player_spawn", "GOLD_BLOCK");

                mobMarker = Material.getMaterial(mobMarkerName.toUpperCase());
                miniBossMarker = Material.getMaterial(miniBossMarkerName.toUpperCase());
                finalBossMarker = Material.getMaterial(finalBossMarkerName.toUpperCase());
                playerSpawnMarker = Material.getMaterial(playerSpawnMarkerName.toUpperCase());

                if (mobMarker == null) mobMarker = Material.GRASS_BLOCK;
                if (miniBossMarker == null) miniBossMarker = Material.OBSIDIAN;
                if (finalBossMarker == null) finalBossMarker = Material.DIAMOND_BLOCK;
                if (playerSpawnMarker == null) playerSpawnMarker = Material.GOLD_BLOCK;
            }
        }

        config.debug("[Full Moon] Map2 area: X(" + minX + " to " + maxX + ") Z(" + minZ + " to " + maxZ + ")");
        config.debug("[Full Moon] Max instances: " + maxInstances + ", Spacing: " + spacing);
        config.debug("[Full Moon] Markers: Mob=" + mobMarker + ", MiniBoss=" + miniBossMarker +
                ", FinalBoss=" + finalBossMarker + ", PlayerSpawn=" + playerSpawnMarker);
    }

    /**
     * Create a new Map 2 instance for a player.
     *
     * @param player The player
     * @param isHard Whether this is hard mode
     * @return The created instance, or null if failed
     */
    public Map2Instance createInstance(Player player, boolean isHard) {
        // Check if player already has an active instance
        if (activeInstances.containsKey(player.getUniqueId())) {
            player.sendMessage("§c§l[Full Moon] §cYou already have an active arena instance!");
            return activeInstances.get(player.getUniqueId());
        }

        // Check max instances limit
        if (activeInstances.size() >= maxInstances) {
            player.sendMessage("§c§l[Full Moon] §cAll arena slots are currently occupied!");
            player.sendMessage("§7Please try again later. (" + activeInstances.size() + "/" + maxInstances + " active)");
            return null;
        }

        // Load schematic file
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schem");
        if (!schematicFile.exists()) {
            schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            player.sendMessage("§c§l[Full Moon] §cArena schematic not found!");
            plugin.getLogger().warning("[Full Moon] Schematic not found: " + schematicFile.getPath());
            return null;
        }

        // Get world
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§c§l[Full Moon] §cArena world not found!");
            plugin.getLogger().warning("[Full Moon] World not found: " + worldName);
            return null;
        }

        // Find free location for the instance
        Location origin = findFreeLocation(world);
        if (origin == null) {
            player.sendMessage("§c§l[Full Moon] §cNo space available for arena instance!");
            return null;
        }

        // Paste schematic and get result with marker locations
        SchematicHandler.PasteResult pasteResult;
        try {
            SchematicHandler.MarkerConfiguration markerConfig = new SchematicHandler.MarkerConfiguration(
                    mobMarker,
                    miniBossMarker,
                    finalBossMarker,
                    playerSpawnMarker
            );

            pasteResult = schematicHandler.pasteSchematic(schematicFile, world, origin, markerConfig);
        } catch (Exception e) {
            player.sendMessage("§c§l[Full Moon] §cFailed to create arena instance!");
            plugin.getLogger().warning("[Full Moon] Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        // Create instance with paste result and difficulty
        Map2Instance instance = new Map2Instance(config, player.getUniqueId(), origin, pasteResult, isHard);

        // Calculate occupied space using actual region size
        Vector regionSize = pasteResult.regionSize();
        CoordinateRange range = new CoordinateRange(
                instance.getOrigin().getBlockX(),
                instance.getOrigin().getBlockZ(),
                instance.getOrigin().getBlockX() + (int) Math.ceil(regionSize.getX()) + spacing,
                instance.getOrigin().getBlockZ() + (int) Math.ceil(regionSize.getZ()) + spacing
        );
        occupiedRanges.add(range);

        // Register instance
        activeInstances.put(player.getUniqueId(), instance);

        // Start auto-cleanup timer (15 minutes)
        instance.startAutoCleanupTimer(() -> {
            removeInstance(player.getUniqueId());
            config.debug("[Full Moon] Instance auto-cleaned after 15 minutes for " + player.getName());
        });

        config.debug("[Full Moon] Created Map2 instance for " + player.getName() + " at " + instance.getOrigin());
        return instance;
    }

    /**
     * Find a free location for a new instance within the configured spawn area.
     * Uses a simple grid-based allocation system.
     */
    private Location findFreeLocation(World world) {
        // Calculate grid dimensions based on max instances
        int gridSize = (int) Math.ceil(Math.sqrt(maxInstances));

        // Handle negative Z coordinates
        int availableX = maxX - minX;
        int availableZ = Math.abs(maxZ - minZ);

        // Calculate spacing between grid slots
        int slotSpacingX = availableX / gridSize;
        int slotSpacingZ = availableZ / gridSize;

        // Try each possible grid slot
        for (int slotX = 0; slotX < gridSize; slotX++) {
            for (int slotZ = 0; slotZ < gridSize; slotZ++) {
                int x = minX + (slotX * slotSpacingX);
                int z;

                // Ensure Z is calculated correctly (area goes from -863 to -2100)
                if (maxZ < minZ) {
                    z = minZ - (slotZ * slotSpacingZ);
                } else {
                    z = minZ + (slotZ * slotSpacingZ);
                }

                // Check if this location is already occupied
                Location testLocation = new Location(world, x, minY, z);
                boolean occupied = false;

                for (CoordinateRange range : occupiedRanges) {
                    if (range.contains(x, z)) {
                        occupied = true;
                        break;
                    }
                }

                if (!occupied) {
                    return testLocation;
                }
            }
        }

        return null; // No free location found
    }


    /**
     * Get the active instance for a player.
     */
    public Map2Instance getInstance(UUID playerId) {
        return activeInstances.get(playerId);
    }

    /**
     * Remove and cleanup an instance.
     */
    public void removeInstance(UUID playerId) {
        Map2Instance instance = activeInstances.remove(playerId);
        if (instance != null) {
            instance.cleanup();

            // Free up space
            occupiedRanges.removeIf(range ->
                    range.minX == instance.getOrigin().getBlockX() &&
                            range.minZ == instance.getOrigin().getBlockZ()
            );

            config.debug("[Full Moon] Removed Map2 instance for player " + playerId);
        }
    }

    /**
     * Get all active instances.
     */
    public Collection<Map2Instance> getAllInstances() {
        return activeInstances.values();
    }

    /**
     * Cleanup all instances.
     */
    public void cleanupAll() {
        for (UUID playerId : new HashSet<>(activeInstances.keySet())) {
            removeInstance(playerId);
        }
        occupiedRanges.clear();
    }

    /**
     * Represents a rectangular coordinate range in the world.
     */
    private static class CoordinateRange {
        final int minX, minZ, maxX, maxZ;

        CoordinateRange(int minX, int minZ, int maxX, int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
        }

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean intersects(CoordinateRange other) {
            return !(maxX < other.minX || minX > other.maxX ||
                    maxZ < other.minZ || minZ > other.maxZ);
        }
    }
}