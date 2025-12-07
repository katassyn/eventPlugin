package org.maks.eventPlugin.newmoon.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.gui.PortalConfirmationGUI;

import java.util.*;

/**
 * Handles portal interactions for New Moon event.
 *
 * Four portals lead to Map 2:
 * - White Realm (Normal)
 * - White Realm (Hard)
 * - Black Realm (Normal)
 * - Black Realm (Hard)
 *
 * When a player enters a portal area, they get a confirmation GUI
 * showing the cost and asking to confirm entry.
 */
public class PortalListener implements Listener {

    private final NewMoonManager newMoonManager;
    private final ConfigManager config;
    private PortalConfirmationGUI portalGUI;
    private final Map<UUID, Long> lockedPortalCooldowns = new HashMap<>();
    private final Map<UUID, Long> guiOpenCooldowns = new HashMap<>(); // Cooldown after opening GUI

    private static final long COOLDOWN_MS = 5000; // 5 seconds
    private static final long GUI_OPEN_COOLDOWN_MS = 3000; // 3 seconds cooldown after opening GUI

    public PortalListener(NewMoonManager newMoonManager, PortalConfirmationGUI portalGUI) {
        this.newMoonManager = newMoonManager;
        this.config = newMoonManager.getConfig();
        this.portalGUI = portalGUI;
    }

    /**
     * Set the portal GUI reference (called after initialization).
     */
    public void setPortalGUI(PortalConfirmationGUI portalGUI) {
        this.portalGUI = portalGUI;
    }

    /**
     * Check if player can show locked portal message (not on cooldown).
     */
    private boolean canShowLockedMessage(Player player) {
        long now = System.currentTimeMillis();
        Long cooldown = lockedPortalCooldowns.get(player.getUniqueId());
        if (cooldown == null || now >= cooldown) {
            lockedPortalCooldowns.put(player.getUniqueId(), now + COOLDOWN_MS);
            return true;
        }
        return false;
    }

    /**
     * Clear GUI cooldown when player confirms entry (teleports away).
     */
    public void clearGUICooldown(UUID playerId) {
        guiOpenCooldowns.remove(playerId);
    }

    /**
     * Set GUI cooldown when player declines entry.
     * This sets a 5 second cooldown from NOW.
     */
    public void setDeclineCooldown(UUID playerId) {
        guiOpenCooldowns.put(playerId, System.currentTimeMillis() + COOLDOWN_MS);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!newMoonManager.isEventActive()) {
            return;
        }

        // Only check when player moves to a new block
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check which portal the player is NOW in
        PortalType portalNow = detectPortal(to);

        // Check if player already has GUI open
        if (portalGUI != null && portalGUI.hasOpenGUI(playerId)) {
            return; // Don't open GUI again
        }

        // Check if player is on GUI open cooldown
        long now = System.currentTimeMillis();
        Long guiCooldown = guiOpenCooldowns.get(playerId);
        if (guiCooldown != null && now < guiCooldown) {
            return; // Still on cooldown, don't open GUI again
        }

        // If player is in a portal area (regardless of where they came from)
        if (portalNow != null) {
            // Check if player already has an active instance (prevents double spawn)
            if (newMoonManager.getMap2InstanceManager().getInstance(playerId) != null) {
                return; // Player already has an active instance, don't open GUI
            }

            // Check if player has unlocked the portal
            if (!checkPortalUnlock(player, portalNow)) {
                if (canShowLockedMessage(player)) {
                    player.sendMessage("§c§l[New Moon] §cYou must complete Quest " +
                            (portalNow.realm.equals("white") ? "3" : "8") + " to unlock this portal!");
                }
                return;
            }

            // Check if portal GUI is available
            if (portalGUI == null) {
                player.sendMessage("§c§l[New Moon] §cPortal GUI is not available! Contact an administrator.");
                return;
            }

            // Set cooldown for reopening GUI
            guiOpenCooldowns.put(playerId, now + GUI_OPEN_COOLDOWN_MS);

            // Open confirmation GUI
            try {
                portalGUI.open(player, portalNow.realm, portalNow.difficulty, to);
            } catch (Exception e) {
                // If GUI opening fails, clean up cooldown
                guiOpenCooldowns.remove(playerId);
                player.sendMessage("§c§l[New Moon] §cFailed to open portal GUI! Contact an administrator.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Detect which portal the player is at based on their location.
     * Returns null if not at any New Moon portal.
     */
    private PortalType detectPortal(Location location) {
        var portalsSection = config.getSection("new_moon.coordinates.portals");

        // White Realm Normal
        if (isInsidePortalArea(location, portalsSection, "white_realm_normal")) {
            return new PortalType("white", "normal");
        }

        // White Realm Hard
        if (isInsidePortalArea(location, portalsSection, "white_realm_hard")) {
            return new PortalType("white", "hard");
        }

        // Black Realm Normal
        if (isInsidePortalArea(location, portalsSection, "black_realm_normal")) {
            return new PortalType("black", "normal");
        }

        // Black Realm Hard
        if (isInsidePortalArea(location, portalsSection, "black_realm_hard")) {
            return new PortalType("black", "hard");
        }

        return null;
    }

    /**
     * Check if a location is inside a portal's bounding box area.
     * Portal areas are defined by min/max coordinates in config.
     */
    private boolean isInsidePortalArea(Location location, org.bukkit.configuration.ConfigurationSection portalsSection, String portalKey) {
        if (portalsSection == null) return false;

        var portalSection = portalsSection.getConfigurationSection(portalKey);
        if (portalSection == null) return false;

        try {
            String worldName = portalSection.getString("world", "world");

            // Check if player is in correct world
            if (!location.getWorld().getName().equals(worldName)) {
                return false;
            }

            // Get bounding box coordinates (expanded by 1 block in all directions for better detection)
            double minX = portalSection.getDouble("min_x") - 1;
            double minY = portalSection.getDouble("min_y") - 1;
            double minZ = portalSection.getDouble("min_z") - 1;
            double maxX = portalSection.getDouble("max_x") + 1;
            double maxY = portalSection.getDouble("max_y") + 1;
            double maxZ = portalSection.getDouble("max_z") + 1;

            // Check if location is inside the bounding box
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();

            return x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if player has unlocked the portal.
     */
    private boolean checkPortalUnlock(Player player, PortalType portal) {
        if (portal.realm.equals("white")) {
            // White realm requires quest 3 completed and claimed
            return newMoonManager.getQuestManager().hasUnlockedWhitePortal(player.getUniqueId());
        } else {
            // Black realm requires quest 8 completed and claimed
            return newMoonManager.getQuestManager().hasUnlockedBlackPortal(player.getUniqueId());
        }
    }

    /**
     * Helper class to represent a portal type.
     */
    private static class PortalType {
        final String realm; // "white" or "black"
        final String difficulty; // "normal" or "hard"

        PortalType(String realm, String difficulty) {
            this.realm = realm;
            this.difficulty = difficulty;
        }
    }
}