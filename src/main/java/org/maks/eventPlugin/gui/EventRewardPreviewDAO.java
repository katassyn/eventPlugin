package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.db.ItemSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for event showcase rewards (preview GUI).
 * Manages showcase items in the database for each event.
 */
public class EventRewardPreviewDAO {
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;

    private static final int DEFAULT_GUI_SIZE = 27; // 3 rows

    public EventRewardPreviewDAO(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Save or update showcase rewards for an event
     * @param eventId The event ID (e.g., "full_moon", "new_moon")
     * @param inventory The inventory containing showcase items
     */
    public void saveShowcaseRewards(String eventId, Inventory inventory) {
        String guiTitle = getShowcaseTitle(eventId);
        String serializedInventory = ItemSerializer.inventoryToBase64(inventory);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO event_showcase_rewards (event_id, gui_title, serialized_inventory) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE gui_title = ?, serialized_inventory = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, eventId);
                stmt.setString(2, guiTitle);
                stmt.setString(3, serializedInventory);
                stmt.setString(4, guiTitle);
                stmt.setString(5, serializedInventory);

                stmt.executeUpdate();

                plugin.getLogger().info("[EventPlugin] Saved showcase rewards for event: " + eventId);
            } catch (SQLException e) {
                plugin.getLogger().severe("[EventPlugin] Failed to save showcase rewards: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Get showcase rewards inventory for an event
     * @param eventId The event ID
     * @return The inventory containing showcase items, or null if not found
     */
    public Inventory getShowcaseInventory(String eventId) {
        String sql = "SELECT gui_title, serialized_inventory FROM event_showcase_rewards WHERE event_id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, eventId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String guiTitle = rs.getString("gui_title");
                String serializedInventory = rs.getString("serialized_inventory");

                return ItemSerializer.inventoryFromBase64(serializedInventory, guiTitle, DEFAULT_GUI_SIZE);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[EventPlugin] Failed to get showcase rewards: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Check if showcase rewards exist for an event
     * @param eventId The event ID
     * @return True if showcase rewards exist, false otherwise
     */
    public boolean hasShowcaseRewards(String eventId) {
        String sql = "SELECT 1 FROM event_showcase_rewards WHERE event_id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, eventId);
            ResultSet rs = stmt.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("[EventPlugin] Failed to check showcase rewards: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Delete showcase rewards for an event
     * @param eventId The event ID
     */
    public void deleteShowcaseRewards(String eventId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM event_showcase_rewards WHERE event_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, eventId);
                stmt.executeUpdate();

                plugin.getLogger().info("[EventPlugin] Deleted showcase rewards for event: " + eventId);
            } catch (SQLException e) {
                plugin.getLogger().severe("[EventPlugin] Failed to delete showcase rewards: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Open showcase rewards GUI for a player
     * @param player The player
     * @param eventId The event ID
     */
    public void openShowcasePreview(Player player, String eventId) {
        EventRewardPreviewGUI gui = getShowcasePreviewGUI(eventId);
        if (gui != null) {
            // Register listener to prevent item extraction
            plugin.getServer().getPluginManager().registerEvents(gui, plugin);
            gui.open(player);
        } else {
            player.sendMessage("§c§lNo rewards preview available for this event!");
        }
    }

    /**
     * Get EventRewardPreviewGUI for an event
     * @param eventId The event ID
     * @return EventRewardPreviewGUI instance, or null if not found
     */
    public EventRewardPreviewGUI getShowcasePreviewGUI(String eventId) {
        Inventory inventory = getShowcaseInventory(eventId);
        if (inventory != null) {
            return new EventRewardPreviewGUI(plugin, eventId, inventory);
        }
        return null;
    }

    /**
     * Get formatted showcase title for an event
     * @param eventId The event ID
     * @return Formatted title
     */
    private String getShowcaseTitle(String eventId) {
        String formattedName = formatEventName(eventId);
        return "§6§lRewards: §r§e" + formattedName;
    }

    /**
     * Format event ID to display name
     * @param eventId The event ID
     * @return Formatted name
     */
    private String formatEventName(String eventId) {
        switch (eventId.toLowerCase()) {
            case "full_moon":
                return "Full Moon";
            case "new_moon":
                return "New Moon";
            case "monster_hunt":
                return "Monster Hunt";
            default:
                // Convert snake_case to Title Case
                String[] words = eventId.split("_");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                    if (result.length() > 0) result.append(" ");
                    result.append(word.substring(0, 1).toUpperCase())
                          .append(word.substring(1).toLowerCase());
                }
                return result.toString();
        }
    }
}
