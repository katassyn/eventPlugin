package org.maks.eventPlugin.winterevent.summit.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.winterevent.WinterEventManager;
import org.maks.eventPlugin.winterevent.summit.WinterSummitManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles portal-style entry to Winter Summit bosses (Bear/Krampus) for three difficulties.
 *
 * When the player steps into a configured portal cuboid, a confirmation GUI is shown.
 */
public class WinterPortalListener implements Listener {

    private final WinterEventManager winterEventManager;
    private final WinterSummitManager summitManager;
    private final ConfigManager config;
    private org.maks.eventPlugin.winterevent.summit.gui.WinterPortalConfirmationGUI portalGUI;

    private final Map<UUID, Long> guiOpenCooldowns = new HashMap<>();
    private final Set<UUID> playersWithOpenGUI = new HashSet<>(); // Track players with GUI open
    private static final long COOLDOWN_MS = 5000; // 5s before reopening GUI after decline/close
    private static final long GUI_OPEN_COOLDOWN_MS = 3000; // 3s basic debounce when opening

    public WinterPortalListener(WinterEventManager winterEventManager,
                                WinterSummitManager summitManager,
                                org.maks.eventPlugin.winterevent.summit.gui.WinterPortalConfirmationGUI portalGUI) {
        this.winterEventManager = winterEventManager;
        this.summitManager = summitManager;
        this.config = winterEventManager.getConfig();
        this.portalGUI = portalGUI;
    }

    public void setPortalGUI(org.maks.eventPlugin.winterevent.summit.gui.WinterPortalConfirmationGUI portalGUI) {
        this.portalGUI = portalGUI;
    }

    /**
     * Clear GUI cooldown when player confirms entry (teleports away)
     */
    public void clearGUICooldown(UUID playerId) {
        guiOpenCooldowns.remove(playerId);
        playersWithOpenGUI.remove(playerId);
    }

    /**
     * Set GUI cooldown when player declines or closes the GUI without confirming.
     */
    public void setDeclineCooldown(UUID playerId) {
        guiOpenCooldowns.put(playerId, System.currentTimeMillis() + COOLDOWN_MS);
        playersWithOpenGUI.remove(playerId);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!winterEventManager.isEventActive()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Only when entering a new block
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // CRITICAL: If player has GUI open, completely ignore ALL portal detection
        if (playersWithOpenGUI.contains(playerId)) {
            return; // Don't process anything if GUI is open
        }

        // Check which portal the player is NOW in
        PortalTarget portal = detectPortal(to);

        // Double check with GUI itself
        if (portalGUI != null && portalGUI.hasOpenGUI(playerId)) {
            playersWithOpenGUI.add(playerId); // Add to tracking
            return;
        }

        // Check if player is on GUI open cooldown
        long now = System.currentTimeMillis();
        Long guiCooldown = guiOpenCooldowns.get(playerId);
        if (guiCooldown != null && now < guiCooldown) {
            return; // Still on cooldown, don't open GUI again
        }

        // If player is in a portal area
        if (portal != null) {
            // Check if player already has an active instance (prevents double spawn)
            if (summitManager.hasActiveInstance(playerId)) {
                return; // Player already has an active instance, don't open GUI
            }

            // Check if portal GUI is available
            if (portalGUI == null) {
                Bukkit.getLogger().warning("[Winter Portal] Portal GUI is not available!");
                player.sendMessage("§c§l[Winter Event] §cPortal GUI is not available! Contact an administrator.");
                return;
            }

            // Set longer cooldown to prevent spam (10 seconds)
            guiOpenCooldowns.put(playerId, now + 10000L);

            // TELEPORT PLAYER OUT OF PORTAL FIRST to prevent Nether Portal from closing GUI
            Location safeLocation = findSafeLocationOutsidePortal(player.getLocation(), portal);
            if (safeLocation != null) {
                player.teleport(safeLocation);
            }

            // Open GUI after teleport (with small delay to let teleport settle)
            Bukkit.getScheduler().runTaskLater(winterEventManager.getPlugin(), () -> {
                try {
                    playersWithOpenGUI.add(playerId); // Mark as having GUI open BEFORE opening
                    portalGUI.open(player, portal.bossType, portal.difficulty, to);
                } catch (Exception e) {
                    guiOpenCooldowns.remove(playerId);
                    playersWithOpenGUI.remove(playerId);
                    Bukkit.getLogger().severe("[Winter Portal] Failed to open GUI: " + e.getMessage());
                    player.sendMessage("§c§l[Winter Event] §cFailed to open portal GUI! Contact an administrator.");
                    e.printStackTrace();
                }
            }, 3L); // 3 ticks delay
        }
    }

    private PortalTarget detectPortal(Location location) {
        ConfigurationSection base = config.getSection("winter_event.summit.portals");
        if (base == null) return null;
        String worldName = location.getWorld() != null ? location.getWorld().getName() : null;
        if (worldName == null) return null;

        String[] difficulties = {"infernal", "hell", "blood"};
        String[] bosses = {"bear", "krampus"};
        for (String diff : difficulties) {
            ConfigurationSection diffSec = base.getConfigurationSection(diff);
            if (diffSec == null) continue;
            for (String boss : bosses) {
                if (isInsidePortalArea(location, diffSec, boss, worldName)) {
                    return new PortalTarget(boss, diff);
                }
            }
        }
        return null;
    }

    private boolean isInsidePortalArea(Location location, ConfigurationSection diffSection, String bossKey, String worldName) {
        ConfigurationSection sec = diffSection.getConfigurationSection(bossKey);
        if (sec == null) return false;
        String world = sec.getString("world", worldName);
        if (!worldName.equals(world)) return false;
        int minX = sec.getInt("min_x");
        int minY = sec.getInt("min_y");
        int minZ = sec.getInt("min_z");
        int maxX = sec.getInt("max_x");
        int maxY = sec.getInt("max_y");
        int maxZ = sec.getInt("max_z");

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= Math.min(minX, maxX) && x <= Math.max(minX, maxX)
                && y >= Math.min(minY, maxY) && y <= Math.max(minY, maxY)
                && z >= Math.min(minZ, maxZ) && z <= Math.max(minZ, maxZ);
    }

    /**
     * Find a safe location outside the portal area to teleport the player before opening GUI.
     * This prevents the Nether Portal from auto-closing the GUI.
     */
    private Location findSafeLocationOutsidePortal(Location playerLocation, PortalTarget portal) {
        ConfigurationSection base = config.getSection("winter_event.summit.portals");
        if (base == null) return null;

        ConfigurationSection diffSec = base.getConfigurationSection(portal.difficulty);
        if (diffSec == null) return null;

        ConfigurationSection sec = diffSec.getConfigurationSection(portal.bossType);
        if (sec == null) return null;

        int minX = sec.getInt("min_x");
        int minZ = sec.getInt("min_z");
        int maxX = sec.getInt("max_x");
        int maxZ = sec.getInt("max_z");

        // Calculate portal center
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;

        // Calculate direction from portal center to player
        double dx = playerLocation.getX() - centerX;
        double dz = playerLocation.getZ() - centerZ;

        // Normalize and extend by 3 blocks away from portal center
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.1) {
            // Player is at center, pick a direction (north)
            dx = 0;
            dz = -1;
            length = 1;
        }

        // Move 3 blocks away from portal center
        double safeX = centerX + (dx / length) * 3.0;
        double safeZ = centerZ + (dz / length) * 3.0;

        return new Location(
            playerLocation.getWorld(),
            safeX,
            playerLocation.getY(),
            safeZ,
            playerLocation.getYaw(),
            playerLocation.getPitch()
        );
    }

    private static class PortalTarget {
        final String bossType; // "bear" or "krampus"
        final String difficulty; // "infernal", "hell", "blood"
        PortalTarget(String bossType, String difficulty) {
            this.bossType = bossType;
            this.difficulty = difficulty;
        }
    }
}
