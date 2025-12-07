package org.maks.eventPlugin.newmoon.map2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.map2.SchematicHandler;
import org.maks.eventPlugin.fullmoon.map2.WorldEditSchematicHandler;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages New Moon Realm (Map 2) solo instances.
 * Handles two types of realms:
 * - White Realm (Lord Silvanus)
 * - Black Realm (Lord Malachai)
 *
 * Each realm has its own schematic, spawn area, and configuration.
 */
public class Map2InstanceManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SchematicHandler schematicHandler;
    private final LordRespawnHologramManager hologramManager;

    // Active instances: Player UUID -> Instance
    private final Map<UUID, Map2Instance> activeInstances = new ConcurrentHashMap<>();

    // Occupied coordinate ranges (to prevent overlap)
    private final Set<CoordinateRange> occupiedRanges = new HashSet<>();

    // Instance spacing and limits from config
    private int spacing = 100;
    private int maxInstancesPerRealm = 4;

    // White Realm configuration
    private String whiteRealmSchematic = "new_moon_white_realm";
    private int whiteMinX = 2000;
    private int whiteMinY = -37;
    private int whiteMinZ = -863;
    private int whiteMaxX = 2500;
    private int whiteMaxZ = -2100;

    // Black Realm configuration
    private String blackRealmSchematic = "new_moon_black_realm";
    private int blackMinX = 3000;
    private int blackMinY = -37;
    private int blackMinZ = -863;
    private int blackMaxX = 3500;
    private int blackMaxZ = -2100;

    private String worldName = "world";

    // Marker materials from config (separate for each realm)
    private Material whiteMobMarker = Material.CALCITE;  // White realm uses CALCITE
    private Material blackMobMarker = Material.COARSE_DIRT;  // Black realm uses COARSE_DIRT
    private Material miniBossMarker = Material.OBSIDIAN;
    private Material lordMarker = Material.DIAMOND_BLOCK;
    private Material playerSpawnMarker = Material.GOLD_BLOCK;
    private Material cauldronMarker = Material.CAULDRON;
    private Material lordRespawnMarker = Material.END_PORTAL_FRAME;

    public Map2InstanceManager(JavaPlugin plugin, ConfigManager config, LordRespawnHologramManager hologramManager) {
        this.plugin = plugin;
        this.config = config;
        this.schematicHandler = new WorldEditSchematicHandler(config);
        this.hologramManager = hologramManager;
        loadConfig();
    }

    private void loadConfig() {
        var newMoonSection = config.getSection("new_moon.schematic");
        if (newMoonSection == null) {
            config.debug("[New Moon] No schematic config found, using defaults");
            return;
        }

        // White Realm configuration
        var whiteSection = newMoonSection.getConfigurationSection("white_realm");
        if (whiteSection != null) {
            whiteRealmSchematic = whiteSection.getString("file_name", "new_moon_white_realm");
            spacing = whiteSection.getInt("spacing", 100);
            maxInstancesPerRealm = whiteSection.getInt("max_instances", 4);

            var whiteSpawnArea = whiteSection.getConfigurationSection("spawn_area");
            if (whiteSpawnArea != null) {
                whiteMinX = whiteSpawnArea.getInt("min_x", 2000);
                whiteMinY = whiteSpawnArea.getInt("min_y", -37);
                whiteMinZ = whiteSpawnArea.getInt("min_z", -863);
                whiteMaxX = whiteSpawnArea.getInt("max_x", 2500);
                whiteMaxZ = whiteSpawnArea.getInt("max_z", -2100);
                worldName = whiteSpawnArea.getString("world", "world");
            }
        }

        // Black Realm configuration
        var blackSection = newMoonSection.getConfigurationSection("black_realm");
        if (blackSection != null) {
            blackRealmSchematic = blackSection.getString("file_name", "new_moon_black_realm");

            var blackSpawnArea = blackSection.getConfigurationSection("spawn_area");
            if (blackSpawnArea != null) {
                blackMinX = blackSpawnArea.getInt("min_x", 3000);
                blackMinY = blackSpawnArea.getInt("min_y", -37);
                blackMinZ = blackSpawnArea.getInt("min_z", -863);
                blackMaxX = blackSpawnArea.getInt("max_x", 3500);
                blackMaxZ = blackSpawnArea.getInt("max_z", -2100);
            }
        }

        // Load marker materials for White Realm
        var whiteRealmScanBlocks = newMoonSection.getConfigurationSection("white_realm_scan_blocks");
        if (whiteRealmScanBlocks != null) {
            String whiteMobMarkerName = whiteRealmScanBlocks.getString("mob_spawn", "CALCITE");
            String miniBossMarkerName = whiteRealmScanBlocks.getString("mini_boss", "OBSIDIAN");
            String lordMarkerName = whiteRealmScanBlocks.getString("final_boss", "DIAMOND_BLOCK");
            String playerSpawnMarkerName = whiteRealmScanBlocks.getString("player_spawn", "GOLD_BLOCK");
            String cauldronMarkerName = whiteRealmScanBlocks.getString("cauldron", "CAULDRON");
            String lordRespawnMarkerName = whiteRealmScanBlocks.getString("lord_respawn_1", "END_PORTAL_FRAME");

            whiteMobMarker = Material.getMaterial(whiteMobMarkerName.toUpperCase());
            miniBossMarker = Material.getMaterial(miniBossMarkerName.toUpperCase());
            lordMarker = Material.getMaterial(lordMarkerName.toUpperCase());
            playerSpawnMarker = Material.getMaterial(playerSpawnMarkerName.toUpperCase());
            cauldronMarker = Material.getMaterial(cauldronMarkerName.toUpperCase());
            lordRespawnMarker = Material.getMaterial(lordRespawnMarkerName.toUpperCase());

            if (whiteMobMarker == null) whiteMobMarker = Material.CALCITE;
            if (miniBossMarker == null) miniBossMarker = Material.OBSIDIAN;
            if (lordMarker == null) lordMarker = Material.DIAMOND_BLOCK;
            if (playerSpawnMarker == null) playerSpawnMarker = Material.GOLD_BLOCK;
            if (cauldronMarker == null) cauldronMarker = Material.CAULDRON;
            if (lordRespawnMarker == null) lordRespawnMarker = Material.END_PORTAL_FRAME;
        }

        // Load marker materials for Black Realm
        var blackRealmScanBlocks = newMoonSection.getConfigurationSection("black_realm_scan_blocks");
        if (blackRealmScanBlocks != null) {
            String blackMobMarkerName = blackRealmScanBlocks.getString("mob_spawn", "COARSE_DIRT");

            blackMobMarker = Material.getMaterial(blackMobMarkerName.toUpperCase());

            if (blackMobMarker == null) blackMobMarker = Material.COARSE_DIRT;
        }

        config.debug("[New Moon] White Realm area: X(" + whiteMinX + " to " + whiteMaxX + ") Z(" + whiteMinZ + " to " + whiteMaxZ + ")");
        config.debug("[New Moon] Black Realm area: X(" + blackMinX + " to " + blackMaxX + ") Z(" + blackMinZ + " to " + blackMaxZ + ")");
        config.debug("[New Moon] Max instances per realm: " + maxInstancesPerRealm + ", Spacing: " + spacing);
    }

    /**
     * Create a new Map 2 instance for a player.
     *
     * @param player The player
     * @param isHard Whether this is hard mode
     * @param realmType The realm type: "white" or "black"
     * @return The created instance, or null if failed
     */
    public Map2Instance createInstance(Player player, boolean isHard, String realmType) {
        // Check if player already has an active instance
        if (activeInstances.containsKey(player.getUniqueId())) {
            player.sendMessage("§c§l[New Moon] §cYou already have an active realm instance!");
            return activeInstances.get(player.getUniqueId());
        }

        // Count instances of this realm type
        long realmCount = activeInstances.values().stream()
                .filter(inst -> inst.getRealmType().equals(realmType))
                .count();

        // Check max instances limit for this realm
        if (realmCount >= maxInstancesPerRealm) {
            String realmName = realmType.equals("white") ? "White Realm" : "Black Realm";
            player.sendMessage("§c§l[New Moon] §cAll " + realmName + " slots are currently occupied!");
            player.sendMessage("§7Please try again later. (" + realmCount + "/" + maxInstancesPerRealm + " active)");
            return null;
        }

        // Get schematic and spawn area based on realm type
        String schematicName;
        int minX, minY, minZ, maxX, maxZ;
        if (realmType.equals("white")) {
            schematicName = whiteRealmSchematic;
            minX = whiteMinX;
            minY = whiteMinY;
            minZ = whiteMinZ;
            maxX = whiteMaxX;
            maxZ = whiteMaxZ;
        } else if (realmType.equals("black")) {
            schematicName = blackRealmSchematic;
            minX = blackMinX;
            minY = blackMinY;
            minZ = blackMinZ;
            maxX = blackMaxX;
            maxZ = blackMaxZ;
        } else {
            player.sendMessage("§c§l[New Moon] §cInvalid realm type!");
            return null;
        }

        // Load schematic file
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schem");
        if (!schematicFile.exists()) {
            schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            player.sendMessage("§c§l[New Moon] §cRealm schematic not found!");
            plugin.getLogger().warning("[New Moon] Schematic not found: " + schematicFile.getPath());
            return null;
        }

        // Get world
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§c§l[New Moon] §cRealm world not found!");
            plugin.getLogger().warning("[New Moon] World not found: " + worldName);
            return null;
        }

        // Find free location for the instance
        Location origin = findFreeLocation(world, minX, minY, minZ, maxX, maxZ);
        if (origin == null) {
            player.sendMessage("§c§l[New Moon] §cNo space available for realm instance!");
            return null;
        }

        // Paste schematic and get result with marker locations
        // Scan for realm-specific mob spawn markers (CALCITE for white, COARSE_DIRT for black)
        Material mobSpawnMarker = realmType.equals("white") ? whiteMobMarker : blackMobMarker;

        SchematicHandler.PasteResult pasteResult;
        try {
            SchematicHandler.MarkerConfiguration markerConfig = new SchematicHandler.MarkerConfiguration(
                    mobSpawnMarker,  // Scan for realm-specific mob spawn marker
                    miniBossMarker,
                    lordMarker,
                    playerSpawnMarker
            );

            pasteResult = schematicHandler.pasteSchematic(schematicFile, world, origin, markerConfig);
        } catch (Exception e) {
            player.sendMessage("§c§l[New Moon] §cFailed to create realm instance!");
            plugin.getLogger().warning("[New Moon] Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        // Create instance with paste result, difficulty, and realm type
        Map2Instance instance = new Map2Instance(config, player.getUniqueId(), origin, pasteResult, isHard, realmType);

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

        // Scan for special blocks (CAULDRON and END_PORTAL_FRAME) in the instance
        scanSpecialBlocks(instance);

        // Start auto-cleanup timer (15 minutes)
        instance.startAutoCleanupTimer(() -> {
            removeInstance(player.getUniqueId());
            config.debug("[New Moon] Instance auto-cleaned after 15 minutes for " + player.getName());
        });

        String realmName = realmType.equals("white") ? "White Realm" : "Black Realm";
        config.debug("[New Moon] Created " + realmName + " instance for " + player.getName() + " at " + instance.getOrigin());
        return instance;
    }

    /**
     * Find a free location for a new instance within the configured spawn area.
     * Uses a simple grid-based allocation system.
     */
    private Location findFreeLocation(World world, int minX, int minY, int minZ, int maxX, int maxZ) {
        // Calculate grid dimensions based on max instances
        int gridSize = (int) Math.ceil(Math.sqrt(maxInstancesPerRealm));

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

                // Ensure Z is calculated correctly
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
            // Remove holograms first
            hologramManager.removeHolograms(instance.getInstanceId());

            // Then cleanup instance
            instance.cleanup();

            // Free up space
            occupiedRanges.removeIf(range ->
                    range.minX == instance.getOrigin().getBlockX() &&
                            range.minZ == instance.getOrigin().getBlockZ()
            );

            config.debug("[New Moon] Removed " + instance.getRealmType() + " realm instance for player " + playerId);
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
     * Replace marker blocks with target blocks in the instance.
     * Used to replace GRASS_BLOCK markers with realm-specific blocks (CALCITE or COARSE_DIRT).
     *
     * @param instance The instance
     * @param fromMaterial The marker block to replace
     * @param toMaterial The target block to replace with
     */
    private void replaceMarkerBlocks(Map2Instance instance, Material fromMaterial, Material toMaterial) {
        com.sk89q.worldedit.regions.CuboidRegion region = instance.getRegion();
        World world = instance.getWorld();
        int replacedCount = 0;

        // Scan region and replace marker blocks
        for (com.sk89q.worldedit.math.BlockVector3 vec : region) {
            Location loc = new Location(world, vec.getX(), vec.getY(), vec.getZ());
            Material type = loc.getBlock().getType();

            if (type == fromMaterial) {
                loc.getBlock().setType(toMaterial);
                replacedCount++;
            }
        }

        config.debug("[New Moon] Replaced " + replacedCount + " " + fromMaterial + " blocks with " + toMaterial +
                " in " + instance.getRealmType() + " realm instance");
    }

    /**
     * Scan instance region for special blocks (CAULDRON and END_PORTAL_FRAME).
     * This is done after schematic paste to locate these blocks.
     */
    private void scanSpecialBlocks(Map2Instance instance) {
        com.sk89q.worldedit.regions.CuboidRegion region = instance.getRegion();
        World world = instance.getWorld();

        Location cauldronLoc = null;
        Location[] respawnLocs = new Location[3];
        int respawnIndex = 0;

        // Scan region for special blocks
        for (com.sk89q.worldedit.math.BlockVector3 vec : region) {
            Location loc = new Location(world, vec.getX(), vec.getY(), vec.getZ());
            Material type = loc.getBlock().getType();

            // Check for CAULDRON
            if (type == cauldronMarker && cauldronLoc == null) {
                cauldronLoc = loc.clone().add(0.5, 1.0, 0.5); // Center of block + 1 up
                config.debug("[New Moon] Found CAULDRON at " + loc);
            }

            // Check for END_PORTAL_FRAME (up to 3)
            if (type == lordRespawnMarker && respawnIndex < 3) {
                respawnLocs[respawnIndex] = loc.clone();
                respawnIndex++;
                config.debug("[New Moon] Found END_PORTAL_FRAME #" + respawnIndex + " at " + loc);
            }
        }

        // Set locations in instance
        if (cauldronLoc != null) {
            instance.setCauldronLocation(cauldronLoc);
        } else {
            Bukkit.getLogger().warning("[New Moon] No CAULDRON found in instance " + instance.getInstanceId());
        }

        if (respawnIndex == 3) {
            instance.setLordRespawnLocations(respawnLocs[0], respawnLocs[1], respawnLocs[2]);
        } else {
            Bukkit.getLogger().warning("[New Moon] Found only " + respawnIndex + "/3 END_PORTAL_FRAME blocks in instance " + instance.getInstanceId());
            // Set what we found
            instance.setLordRespawnLocations(
                    respawnLocs[0],
                    respawnIndex > 1 ? respawnLocs[1] : null,
                    respawnIndex > 2 ? respawnLocs[2] : null
            );
        }

        config.debug("[New Moon] Special block scan complete for instance " + instance.getInstanceId());
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
    }
}
