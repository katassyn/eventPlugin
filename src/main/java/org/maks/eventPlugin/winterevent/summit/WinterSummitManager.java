package org.maks.eventPlugin.winterevent.summit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.map2.SchematicHandler;
import org.maks.eventPlugin.fullmoon.map2.WorldEditSchematicHandler;
import org.maks.eventPlugin.winterevent.WinterEventManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Winter Summit boss instances (Bear and Krampus).
 * Handles FAWE schematic pasting and instance lifecycle.
 */
public class WinterSummitManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final WinterEventManager winterEventManager;
    private final SchematicHandler schematicHandler;

    // Active instances: Instance ID -> Instance
    private final Map<UUID, WinterSummitInstance> activeInstances = new ConcurrentHashMap<>();

    // Player -> Instance mapping
    private final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();

    public WinterSummitManager(JavaPlugin plugin, ConfigManager config, WinterEventManager winterEventManager) {
        this.plugin = plugin;
        this.config = config;
        this.winterEventManager = winterEventManager;
        this.schematicHandler = new WorldEditSchematicHandler(config);
    }

    /**
     * Create a new boss instance for player.
     *
     * @param player     The player
     * @param bossType   "bear" or "krampus"
     * @param difficulty "infernal", "hell", or "blood"
     * @return The created instance, or null if failed
     */
    public WinterSummitInstance createBossInstance(Player player, String bossType, String difficulty) {
        // Check if player already has active instance
        if (playerToInstance.containsKey(player.getUniqueId())) {
            player.sendMessage("§c§l[Winter Event] §cYou already have an active boss instance!");
            return null;
        }

        // Load schematic
        String configPath = "winter_event.summit.schematics." + bossType;
        String schematicName = config.getSection(configPath).getString("file_name");
        File schematicFile = new File("plugins/FastAsyncWorldEdit/schematics/" + schematicName + ".schem");
        if (!schematicFile.exists()) {
            schematicFile = new File("plugins/FastAsyncWorldEdit/schematics/" + schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            player.sendMessage("§c§l[Winter Event] §cBoss arena schematic not found!");
            plugin.getLogger().warning("[Winter Event] Schematic not found: " + schematicFile.getPath());
            return null;
        }

        // Get spawn area
        String worldName = config.getSection(configPath + ".spawn_area").getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§c§l[Winter Event] §cBoss world not found!");
            return null;
        }

        // Find free location
        Location origin = findFreeLocation(bossType, world);
        if (origin == null) {
            player.sendMessage("§c§l[Winter Event] §cNo space available for boss instance!");
            return null;
        }

        // Paste schematic
        try {
            Material playerSpawnMarker = Material.GOLD_BLOCK;
            Material bossSpawnMarker = Material.DIAMOND_BLOCK;

            SchematicHandler.MarkerConfiguration markerConfig = new SchematicHandler.MarkerConfiguration(
                    Material.AIR, Material.AIR, bossSpawnMarker, playerSpawnMarker
            );

            SchematicHandler.PasteResult result = schematicHandler.pasteSchematic(
                    schematicFile, world, origin, markerConfig);

            // Create region
            org.bukkit.util.Vector minOffset = result.minimumOffset();
            org.bukkit.util.Vector maxOffset = result.maximumOffset();
            BlockVector3 min = com.sk89q.worldedit.math.BlockVector3.at(
                    origin.getBlockX() + (int) Math.floor(minOffset.getX()),
                    origin.getBlockY() + (int) Math.floor(minOffset.getY()),
                    origin.getBlockZ() + (int) Math.floor(minOffset.getZ())
            );
            BlockVector3 max = com.sk89q.worldedit.math.BlockVector3.at(
                    origin.getBlockX() + (int) Math.floor(maxOffset.getX()),
                    origin.getBlockY() + (int) Math.floor(maxOffset.getY()),
                    origin.getBlockZ() + (int) Math.floor(maxOffset.getZ())
            );
            CuboidRegion region = new CuboidRegion(min, max);

            // Create instance
            WinterSummitInstance instance = new WinterSummitInstance(
                    player.getUniqueId(), bossType, difficulty, origin, region);

            // Set spawn locations
            if (!result.playerSpawnMarkerOffsets().isEmpty()) {
                SchematicHandler.BlockOffset offset = result.playerSpawnMarkerOffsets().get(0);
                Location playerSpawn = origin.clone().add(offset.x(), offset.y(), offset.z());
                instance.setPlayerSpawnLocation(playerSpawn);
            }

            if (!result.finalBossMarkerOffsets().isEmpty()) {
                SchematicHandler.BlockOffset offset = result.finalBossMarkerOffsets().get(0);
                Location bossSpawn = origin.clone().add(offset.x(), offset.y(), offset.z());
                instance.setBossSpawnLocation(bossSpawn);
            }

            // Store instance
            activeInstances.put(instance.getInstanceId(), instance);
            playerToInstance.put(player.getUniqueId(), instance.getInstanceId());

            // Teleport player
            if (instance.getPlayerSpawnLocation() != null) {
                player.teleport(instance.getPlayerSpawnLocation());
            } else {
                player.teleport(origin);
            }

            // Spawn boss
            spawnBoss(instance);

            // Auto-cleanup after 15 minutes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeInstances.containsKey(instance.getInstanceId())) {
                    cleanupInstance(instance.getInstanceId());
                }
            }, 15 * 60 * 20L);

            player.sendMessage("§f§l[Winter Event] §aBoss arena created! Defeat the boss to earn rewards.");

            return instance;

        } catch (Exception e) {
            player.sendMessage("§c§l[Winter Event] §cFailed to create boss instance!");
            plugin.getLogger().severe("[Winter Event] Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Find free location for instance.
     */
    private Location findFreeLocation(String bossType, World world) {
        String configPath = "winter_event.summit.schematics." + bossType + ".spawn_area";
        int minX = config.getSection(configPath).getInt("min_x");
        int minY = config.getSection(configPath).getInt("min_y");
        int minZ = config.getSection(configPath).getInt("min_z");
        int maxX = config.getSection(configPath).getInt("max_x");
        int maxZ = config.getSection(configPath).getInt("max_z");
        int spacing = config.getSection("winter_event.summit.schematics." + bossType).getInt("spacing", 100);

        for (int x = minX; x <= maxX; x += spacing) {
            for (int z = minZ; z <= maxZ; z += spacing) {
                Location candidate = new Location(world, x, minY, z);

                // Check if location is free
                boolean isFree = true;
                for (WinterSummitInstance instance : activeInstances.values()) {
                    if (instance.contains(candidate)) {
                        isFree = false;
                        break;
                    }
                }

                if (isFree) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * Spawn boss in instance.
     */
    private void spawnBoss(WinterSummitInstance instance) {
        if (instance.getBossSpawnLocation() == null) {
            plugin.getLogger().warning("[Winter Event] No boss spawn location found!");
            return;
        }

        String bossId = config.getSection("winter_event.summit.bosses." + instance.getBossType())
                .getString(instance.getDifficulty());

        if (bossId == null) {
            plugin.getLogger().warning("[Winter Event] No boss ID for " + instance.getBossType() + " " + instance.getDifficulty());
            return;
        }

        Location loc = instance.getBossSpawnLocation();
        String command = "mm mobs spawn " + bossId + " 1 " +
                loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Cleanup instance and remove from tracking.
     */
    public void cleanupInstance(UUID instanceId) {
        WinterSummitInstance instance = activeInstances.remove(instanceId);
        if (instance != null) {
            playerToInstance.remove(instance.getPlayerId());

            // Clear blocks from world
            try {
                com.sk89q.worldedit.math.BlockVector3 min = instance.getRegion().getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 max = instance.getRegion().getMaximumPoint();

                int width = max.getX() - min.getX() + 1;
                int height = max.getY() - min.getY() + 1;
                int depth = max.getZ() - min.getZ() + 1;

                org.bukkit.util.Vector size = new org.bukkit.util.Vector(width, height, depth);
                schematicHandler.clearRegion(instance.getPasteOrigin().getWorld(), instance.getPasteOrigin(), size);

                plugin.getLogger().info("[Winter Event] Cleaned up instance " + instanceId + " and cleared blocks");
            } catch (Exception e) {
                plugin.getLogger().warning("[Winter Event] Failed to clear blocks for instance " + instanceId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Get instance by player.
     */
    public WinterSummitInstance getInstanceByPlayer(UUID playerId) {
        UUID instanceId = playerToInstance.get(playerId);
        return instanceId != null ? activeInstances.get(instanceId) : null;
    }

    /**
     * Get instance by location.
     */
    public WinterSummitInstance getInstanceByLocation(Location location) {
        for (WinterSummitInstance instance : activeInstances.values()) {
            if (instance.contains(location)) {
                return instance;
            }
        }
        return null;
    }

    /**
     * Check if player has active instance.
     */
    public boolean hasActiveInstance(UUID playerId) {
        return playerToInstance.containsKey(playerId);
    }

    /**
     * Cleanup all instances (on plugin disable).
     */
    public void cleanupLeftoverInstances() {
        activeInstances.clear();
        playerToInstance.clear();
    }
}
