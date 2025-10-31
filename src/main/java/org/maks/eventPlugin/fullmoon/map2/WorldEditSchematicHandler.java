package org.maks.eventPlugin.fullmoon.map2;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.maks.eventPlugin.config.ConfigManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WorldEdit-based schematic handler for eventPlugin.
 */
public class WorldEditSchematicHandler implements SchematicHandler {

    private final ConfigManager config;

    public WorldEditSchematicHandler(ConfigManager config) {
        this.config = config;
    }

    @Override
    public PasteResult pasteSchematic(File schematicFile,
                                      World world,
                                      Location origin,
                                      MarkerConfiguration markerConfiguration) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            throw new IOException("Unsupported schematic format for file " + schematicFile.getAbsolutePath());
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            clipboard = reader.read();
        }

        BlockVector3 clipboardOrigin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();

        Vector minimumOffset = toBukkitVector(min.subtract(clipboardOrigin));
        Vector maximumOffset = toBukkitVector(max.subtract(clipboardOrigin));
        Vector appliedOffset = computeAppliedOffset(world, origin, minimumOffset, maximumOffset);

        BlockVector3 to = BlockVector3.at(
                origin.getBlockX() + (int) Math.floor(appliedOffset.getX()),
                origin.getBlockY() + (int) Math.floor(appliedOffset.getY()),
                origin.getBlockZ() + (int) Math.floor(appliedOffset.getZ()));

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            flushEditSession(editSession);
        } catch (WorldEditException ex) {
            throw new IOException("Failed to paste schematic: " + ex.getMessage(), ex);
        }

        BlockVector3 dimensions = clipboard.getDimensions();
        Vector regionSize = new Vector(dimensions.getBlockX(), dimensions.getBlockY(), dimensions.getBlockZ());

        MarkerScan scan = scanMarkers(clipboard, markerConfiguration);

        return new PasteResult(minimumOffset,
                maximumOffset,
                regionSize,
                appliedOffset,
                scan.mobMarkerOffsets(),
                scan.miniBossMarkerOffsets(),
                scan.finalBossMarkerOffsets(),
                scan.playerSpawnOffsets());
    }

    @Override
    public void clearRegion(World world, Location origin, Vector size) {
        BlockVector3 min = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        int width = Math.max(1, (int) Math.ceil(size.getX()));
        int height = Math.max(1, (int) Math.ceil(size.getY()));
        int depth = Math.max(1, (int) Math.ceil(size.getZ()));
        BlockVector3 max = min.add(width - 1, height - 1, depth - 1);

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            CuboidRegion region = new CuboidRegion(min, max);
            editSession.setBlocks((Region) region, BlockTypes.AIR.getDefaultState());
            flushEditSession(editSession);
        } catch (WorldEditException ex) {
            throw new IllegalStateException("Failed to clear region: " + ex.getMessage(), ex);
        }
    }

    /**
     * Compute the offset to apply to the schematic to keep it within world bounds.
     */
    private Vector computeAppliedOffset(World world,
                                        Location origin,
                                        Vector minimumOffset,
                                        Vector maximumOffset) {
        int adjustY = 0;
        if (minimumOffset != null) {
            adjustY = computeAxisAdjustment(minimumOffset.getY());
        }

        int originY = origin.getBlockY();
        int minY = minimumOffset == null ? 0 : (int) Math.floor(minimumOffset.getY());
        int maxY = maximumOffset == null ? minY : (int) Math.floor(maximumOffset.getY());
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;

        adjustY = Math.max(adjustY, 0);

        int bottomY = originY + adjustY + minY;
        if (bottomY < worldMin) {
            adjustY += worldMin - bottomY;
        }

        int topY = originY + adjustY + maxY;
        if (topY > worldMax) {
            adjustY -= topY - worldMax;
            if (adjustY < 0) {
                adjustY = 0;
            }
        }

        bottomY = originY + adjustY + minY;
        if (bottomY < worldMin) {
            adjustY += worldMin - bottomY;
        }

        return new Vector(0, adjustY, 0);
    }

    private int computeAxisAdjustment(double minimumOffset) {
        int offset = (int) Math.floor(minimumOffset);
        return offset < 0 ? -offset : 0;
    }

    private Vector toBukkitVector(BlockVector3 vector) {
        return new Vector(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    /**
     * Flush the edit session to ensure all changes are applied immediately.
     */
    private void flushEditSession(EditSession editSession) {
        if (editSession == null) {
            return;
        }
        try {
            editSession.getClass().getMethod("flushQueue").invoke(editSession);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Fall through and try alternative methods.
        }
        try {
            editSession.getClass().getMethod("flushSession").invoke(editSession);
        } catch (ReflectiveOperationException ignored) {
            // No-op when the method is unavailable (vanilla WorldEdit).
        }
    }

    /**
     * Scan the clipboard for marker blocks.
     */
    private MarkerScan scanMarkers(Clipboard clipboard, MarkerConfiguration markerConfiguration) {
        List<BlockOffset> mobOffsets = new ArrayList<>();
        List<BlockOffset> miniBossOffsets = new ArrayList<>();
        List<BlockOffset> finalBossOffsets = new ArrayList<>();
        List<BlockOffset> playerSpawnOffsets = new ArrayList<>();

        String mobMarkerId = materialId(markerConfiguration.mobMarker());
        String miniBossMarkerId = materialId(markerConfiguration.miniBossMarker());
        String finalBossMarkerId = materialId(markerConfiguration.finalBossMarker());
        String playerSpawnId = materialId(markerConfiguration.playerSpawnMarker());

        BlockVector3 origin = clipboard.getOrigin();
        for (BlockVector3 position : clipboard.getRegion()) {
            BlockStateHolder<?> state = clipboard.getFullBlock(position);
            String stateId = state.getBlockType().getId();

            if (matches(stateId, mobMarkerId)) {
                mobOffsets.add(toOffset(position.subtract(origin)));
            } else if (matches(stateId, miniBossMarkerId)) {
                miniBossOffsets.add(toOffset(position.subtract(origin)));
            } else if (matches(stateId, finalBossMarkerId)) {
                finalBossOffsets.add(toOffset(position.subtract(origin)));
            } else if (matches(stateId, playerSpawnId)) {
                playerSpawnOffsets.add(toOffset(position.subtract(origin)));
            }
        }

        config.debug("[Full Moon] Scanned markers: " +
                mobOffsets.size() + " mobs, " +
                miniBossOffsets.size() + " mini-bosses, " +
                finalBossOffsets.size() + " final boss, " +
                playerSpawnOffsets.size() + " player spawns");

        return new MarkerScan(mobOffsets, miniBossOffsets, finalBossOffsets, playerSpawnOffsets);
    }

    private String materialId(Material material) {
        NamespacedKey key = material.getKey();
        return key.getNamespace().toLowerCase(Locale.ROOT) + ":" + key.getKey().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String stateId, String targetId) {
        if (stateId.equalsIgnoreCase(targetId)) {
            return true;
        }
        int separatorIndex = targetId.indexOf(':');
        if (separatorIndex >= 0) {
            String withoutNamespace = targetId.substring(separatorIndex + 1);
            return stateId.equalsIgnoreCase(withoutNamespace);
        }
        return false;
    }

    private BlockOffset toOffset(BlockVector3 vector) {
        return new BlockOffset(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    private record MarkerScan(List<BlockOffset> mobMarkerOffsets,
                              List<BlockOffset> miniBossMarkerOffsets,
                              List<BlockOffset> finalBossMarkerOffsets,
                              List<BlockOffset> playerSpawnOffsets) {
    }
}
