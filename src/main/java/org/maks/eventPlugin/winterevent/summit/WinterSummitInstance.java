package org.maks.eventPlugin.winterevent.summit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Represents a Winter Summit boss instance (FAWE).
 * Similar to Map2Instance from Full/New Moon events.
 */
public class WinterSummitInstance {
    private final UUID instanceId;
    private final UUID playerId;
    private final String bossType;      // "bear" or "krampus"
    private final String difficulty;    // "infernal", "hell", "blood"
    private final Location pasteOrigin;
    private final CuboidRegion region;
    private final long createdAt;

    private Location bossSpawnLocation;
    private Location playerSpawnLocation;
    private UUID bossEntityId;

    public WinterSummitInstance(UUID playerId, String bossType, String difficulty,
                                Location pasteOrigin, CuboidRegion region) {
        this.instanceId = UUID.randomUUID();
        this.playerId = playerId;
        this.bossType = bossType;
        this.difficulty = difficulty;
        this.pasteOrigin = pasteOrigin;
        this.region = region;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getBossType() {
        return bossType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public Location getPasteOrigin() {
        return pasteOrigin;
    }

    public CuboidRegion getRegion() {
        return region;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Location getBossSpawnLocation() {
        return bossSpawnLocation;
    }

    public void setBossSpawnLocation(Location bossSpawnLocation) {
        this.bossSpawnLocation = bossSpawnLocation;
    }

    public Location getPlayerSpawnLocation() {
        return playerSpawnLocation;
    }

    public void setPlayerSpawnLocation(Location playerSpawnLocation) {
        this.playerSpawnLocation = playerSpawnLocation;
    }

    public UUID getBossEntityId() {
        return bossEntityId;
    }

    public void setBossEntityId(UUID bossEntityId) {
        this.bossEntityId = bossEntityId;
    }

    /**
     * Check if a location is within this instance.
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(pasteOrigin.getWorld())) {
            return false;
        }

        BlockVector3 point = BlockVector3.at(location.getX(), location.getY(), location.getZ());
        return region.contains(point);
    }
}
