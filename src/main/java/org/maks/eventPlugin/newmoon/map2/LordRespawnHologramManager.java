package org.maks.eventPlugin.newmoon.map2;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages holograms for lord respawn blocks (END_PORTAL_FRAME).
 * Creates floating text above each block showing cost and usage status.
 */
public class LordRespawnHologramManager {

    // Store holograms for each instance: Instance ID -> List of ArmorStands
    private final Map<UUID, List<ArmorStand>> instanceHolograms = new HashMap<>();

    /**
     * Create holograms for an instance's lord respawn blocks.
     *
     * @param instance The instance
     */
    public void createHolograms(Map2Instance instance) {
        Location[] respawnLocs = instance.getLordRespawnLocations();
        List<ArmorStand> holograms = new ArrayList<>();

        int[] costs = {100, 200, 300};

        for (int i = 0; i < 3; i++) {
            if (respawnLocs[i] == null) continue;

            // Location above the END_PORTAL_FRAME block
            Location hologramLoc = respawnLocs[i].clone().add(0.5, 2.0, 0.5);

            // Create hologram text
            String text = getHologramText(i, costs[i], false);

            // Spawn ArmorStand hologram
            ArmorStand stand = spawnHologramLine(hologramLoc, text);
            if (stand != null) {
                holograms.add(stand);
            }
        }

        // Store holograms
        instanceHolograms.put(instance.getInstanceId(), holograms);
    }

    /**
     * Update holograms to reflect current respawn count.
     *
     * @param instance The instance
     */
    public void updateHolograms(Map2Instance instance) {
        List<ArmorStand> holograms = instanceHolograms.get(instance.getInstanceId());
        if (holograms == null || holograms.isEmpty()) return;

        int currentCount = instance.getLordRespawnCount();
        int[] costs = {100, 200, 300};

        for (int i = 0; i < Math.min(3, holograms.size()); i++) {
            ArmorStand stand = holograms.get(i);
            if (stand == null || !stand.isValid()) continue;

            boolean used = i < currentCount;
            String text = getHologramText(i, costs[i], used);
            stand.setCustomName(text);
        }
    }

    /**
     * Remove all holograms for an instance.
     *
     * @param instanceId The instance ID
     */
    public void removeHolograms(UUID instanceId) {
        List<ArmorStand> holograms = instanceHolograms.remove(instanceId);
        if (holograms != null) {
            for (ArmorStand stand : holograms) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
    }

    /**
     * Get hologram text for a respawn block.
     *
     * @param index Block index (0-2)
     * @param cost Cost in Fairy Wood
     * @param used Whether this block has been used
     * @return Formatted hologram text
     */
    private String getHologramText(int index, int cost, boolean used) {
        if (used) {
            return "§7§l#" + (index + 1) + " - USED";
        } else {
            return "§a§l#" + (index + 1) + " §e- §6" + cost + " Fairy Wood";
        }
    }

    /**
     * Spawn a single hologram line (armor stand).
     */
    private ArmorStand spawnHologramLine(Location location, String text) {
        try {
            ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            stand.setGravity(false);
            stand.setVisible(false);
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
            stand.setMarker(true); // Makes it non-collidable
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            return stand;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
