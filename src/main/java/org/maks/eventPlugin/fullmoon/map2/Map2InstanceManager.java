package org.maks.eventPlugin.fullmoon.map2;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Blood Moon Arena (Map 2) solo instances.
 * Handles FAWE schematic loading, instance creation, and space allocation.
 */
public class Map2InstanceManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;

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

    // Schematic size from config
    private int schematicSizeX = 172;
    private int schematicSizeY = 52;
    private int schematicSizeZ = 321;

    public Map2InstanceManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        loadConfig();
    }

    private void loadConfig() {
        var schematicSection = config.getSection("full_moon.schematic");
        if (schematicSection != null) {
            schematicName = schematicSection.getString("file_name", "blood_moon_arena");
            spacing = schematicSection.getInt("spacing", 100);
            maxInstances = schematicSection.getInt("max_instances", 4);

            // Load schematic size
            var sizeSection = schematicSection.getConfigurationSection("size");
            if (sizeSection != null) {
                schematicSizeX = sizeSection.getInt("x", 172);
                schematicSizeY = sizeSection.getInt("y", 52);
                schematicSizeZ = sizeSection.getInt("z", 321);
            }

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
        }

        plugin.getLogger().info("[Full Moon] Map2 area: X(" + minX + " to " + maxX + ") Z(" + minZ + " to " + maxZ + ")");
        plugin.getLogger().info("[Full Moon] Schematic size: " + schematicSizeX + "x" + schematicSizeY + "x" + schematicSizeZ);
        plugin.getLogger().info("[Full Moon] Max instances: " + maxInstances + ", Spacing: " + spacing);
    }

    /**
     * Create a new Map 2 instance for a player.
     *
     * @param player The player
     * @return The created instance, or null if failed
     */
    public Map2Instance createInstance(Player player) {
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

        // Load schematic
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schem");
        if (!schematicFile.exists()) {
            schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            player.sendMessage("§c§l[Full Moon] §cArena schematic not found!");
            plugin.getLogger().warning("[Full Moon] Schematic not found: " + schematicFile.getPath());
            return null;
        }

        Clipboard clipboard;
        BlockVector3 size;

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                player.sendMessage("§c§l[Full Moon] §cUnsupported schematic format!");
                return null;
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                clipboard = reader.read();
                size = clipboard.getRegion().getMaximumPoint().subtract(clipboard.getRegion().getMinimumPoint());
            }
        } catch (Exception e) {
            player.sendMessage("§c§l[Full Moon] §cFailed to load arena schematic!");
            plugin.getLogger().warning("[Full Moon] Failed to load schematic: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        // Get world
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§c§l[Full Moon] §cArena world not found!");
            plugin.getLogger().warning("[Full Moon] World not found: " + worldName);
            return null;
        }

        // Find free coordinates
        Location origin = findFreeLocation(world, size);
        if (origin == null) {
            player.sendMessage("§c§l[Full Moon] §cNo space available for arena instance!");
            return null;
        }

        // Paste schematic
        if (!pasteSchematic(clipboard, origin)) {
            player.sendMessage("§c§l[Full Moon] §cFailed to create arena instance!");
            return null;
        }

        // Create instance object
        Map2Instance instance = new Map2Instance(player.getUniqueId(), origin, size);

        // Mark space as occupied
        CoordinateRange range = new CoordinateRange(
                origin.getBlockX(),
                origin.getBlockZ(),
                origin.getBlockX() + size.getBlockX() + spacing,
                origin.getBlockZ() + size.getBlockZ() + spacing
        );
        occupiedRanges.add(range);

        // Register instance
        activeInstances.put(player.getUniqueId(), instance);

        // Start auto-cleanup timer (15 minutes)
        instance.startAutoCleanupTimer(() -> {
            removeInstance(player.getUniqueId());
            plugin.getLogger().info("[Full Moon] Instance auto-cleaned after 15 minutes for " + player.getName());
        });

        plugin.getLogger().info("[Full Moon] Created Map2 instance for " + player.getName() + " at " + origin);
        return instance;
    }

    /**
     * Find a free location for a new instance within the configured spawn area.
     */
    private Location findFreeLocation(World world, BlockVector3 size) {
        // Calculate available space in X and Z
        int availableX = maxX - minX;
        int availableZ = Math.abs(maxZ - minZ);  // Handle negative Z coordinates

        // Calculate number of slots in each direction
        int slotsX = availableX / (schematicSizeX + spacing);
        int slotsZ = availableZ / (schematicSizeZ + spacing);

        // Try each possible slot
        for (int slotX = 0; slotX < slotsX; slotX++) {
            for (int slotZ = 0; slotZ < slotsZ; slotZ++) {
                int x = minX + (slotX * (schematicSizeX + spacing));
                int z = minZ + (slotZ * (schematicSizeZ + spacing));

                // Ensure Z is calculated correctly (area goes from -863 to -2100)
                if (maxZ < minZ) {
                    z = minZ - (slotZ * (schematicSizeZ + spacing));
                }

                CoordinateRange testRange = new CoordinateRange(
                        x,
                        z,
                        x + schematicSizeX,
                        z + schematicSizeZ
                );

                if (!isOverlapping(testRange)) {
                    return new Location(world, x, minY, z);
                }
            }
        }

        return null; // No free location found
    }

    /**
     * Check if a coordinate range overlaps with any occupied ranges.
     */
    private boolean isOverlapping(CoordinateRange range) {
        for (CoordinateRange occupied : occupiedRanges) {
            if (range.intersects(occupied)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Paste a schematic at the given location.
     */
    private boolean pasteSchematic(Clipboard clipboard, Location origin) {
        try {
            BlockVector3 pastePos = BlockVector3.at(
                    origin.getBlockX(),
                    origin.getBlockY(),
                    origin.getBlockZ()
            );

            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(origin.getWorld()))
                    .build()) {

                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(pastePos)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Full Moon] Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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

            plugin.getLogger().info("[Full Moon] Removed Map2 instance for player " + playerId);
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
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        boolean intersects(CoordinateRange other) {
            return !(maxX < other.minX || minX > other.maxX ||
                     maxZ < other.minZ || minZ > other.maxZ);
        }
    }
}
