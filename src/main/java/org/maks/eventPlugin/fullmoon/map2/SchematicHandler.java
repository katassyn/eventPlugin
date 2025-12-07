package org.maks.eventPlugin.fullmoon.map2;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Interface for handling schematic operations.
 */
public interface SchematicHandler {

    /**
     * Paste a schematic at the given origin location.
     *
     * @param schematicFile The schematic file
     * @param world The target world
     * @param origin The origin location
     * @param markerConfiguration Configuration for marker blocks
     * @return Result containing paste information and marker offsets
     * @throws Exception if pasting fails
     */
    PasteResult pasteSchematic(File schematicFile,
                               World world,
                               Location origin,
                               MarkerConfiguration markerConfiguration) throws Exception;

    /**
     * Clear a region in the world.
     *
     * @param world The world
     * @param origin The origin location
     * @param size The size of the region
     */
    void clearRegion(World world, Location origin, Vector size);

    /**
     * Configuration for marker blocks to scan.
     */
    record MarkerConfiguration(Material mobMarker,
                               Material miniBossMarker,
                               Material finalBossMarker,
                               Material playerSpawnMarker) {

        public MarkerConfiguration {
            Objects.requireNonNull(mobMarker, "mobMarker");
            Objects.requireNonNull(miniBossMarker, "miniBossMarker");
            Objects.requireNonNull(finalBossMarker, "finalBossMarker");
            Objects.requireNonNull(playerSpawnMarker, "playerSpawnMarker");
        }
    }

    /**
     * Block offset relative to schematic origin.
     */
    record BlockOffset(int x, int y, int z) {
        // No validation needed; values originate from schematic coordinates.
    }

    /**
     * Result of pasting a schematic.
     */
    record PasteResult(Vector minimumOffset,
                       Vector maximumOffset,
                       Vector regionSize,
                       Vector appliedOffset,
                       List<BlockOffset> mobMarkerOffsets,
                       List<BlockOffset> miniBossMarkerOffsets,
                       List<BlockOffset> finalBossMarkerOffsets,
                       List<BlockOffset> playerSpawnMarkerOffsets) {

        public PasteResult {
            if (minimumOffset != null) {
                minimumOffset = minimumOffset.clone();
            }
            if (maximumOffset != null) {
                maximumOffset = maximumOffset.clone();
            }
            if (regionSize != null) {
                regionSize = regionSize.clone();
            }
            if (appliedOffset != null) {
                appliedOffset = appliedOffset.clone();
            }
            mobMarkerOffsets = mobMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(mobMarkerOffsets);
            miniBossMarkerOffsets = miniBossMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(miniBossMarkerOffsets);
            finalBossMarkerOffsets = finalBossMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(finalBossMarkerOffsets);
            playerSpawnMarkerOffsets = playerSpawnMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(playerSpawnMarkerOffsets);
        }

        @Override
        public Vector minimumOffset() {
            return minimumOffset == null ? null : minimumOffset.clone();
        }

        @Override
        public Vector maximumOffset() {
            return maximumOffset == null ? null : maximumOffset.clone();
        }

        @Override
        public Vector regionSize() {
            return regionSize == null ? null : regionSize.clone();
        }

        @Override
        public Vector appliedOffset() {
            return appliedOffset == null ? null : appliedOffset.clone();
        }

        @Override
        public List<BlockOffset> mobMarkerOffsets() {
            return Collections.unmodifiableList(mobMarkerOffsets);
        }

        @Override
        public List<BlockOffset> miniBossMarkerOffsets() {
            return Collections.unmodifiableList(miniBossMarkerOffsets);
        }

        @Override
        public List<BlockOffset> finalBossMarkerOffsets() {
            return Collections.unmodifiableList(finalBossMarkerOffsets);
        }

        @Override
        public List<BlockOffset> playerSpawnMarkerOffsets() {
            return Collections.unmodifiableList(playerSpawnMarkerOffsets);
        }
    }
}
