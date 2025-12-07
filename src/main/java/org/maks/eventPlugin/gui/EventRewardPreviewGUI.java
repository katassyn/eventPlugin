package org.maks.eventPlugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GUI for displaying event showcase rewards (read-only preview).
 * Players can view but cannot take items.
 */
public class EventRewardPreviewGUI implements InventoryHolder, Listener {
    private final JavaPlugin plugin;
    private final String eventId;
    private final Inventory inventory;

    public EventRewardPreviewGUI(JavaPlugin plugin, String eventId, Inventory inventory) {
        this.plugin = plugin;
        this.eventId = eventId;
        this.inventory = inventory;
    }

    /**
     * Open the showcase preview GUI for a player
     * @param player The player to show the GUI to
     */
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Get the event ID for this GUI
     * @return The event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Cancel all clicks in this GUI to prevent item taking
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if this is our inventory (can't use getHolder() because it's null from deserialization)
        if (!event.getInventory().equals(inventory)) return;

        // Cancel all clicks - this is a read-only preview
        event.setCancelled(true);

        // Optionally play sound or send message
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != org.bukkit.Material.AIR) {
            Player player = (Player) event.getWhoClicked();
            if (event.getCurrentItem().hasItemMeta() && event.getCurrentItem().getItemMeta().hasDisplayName()) {
                String displayName = event.getCurrentItem().getItemMeta().getDisplayName();
                player.sendMessage("§8» §7Viewing: " + displayName);
            }
        }
    }

    /**
     * Cancel dragging items into this GUI
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Check if this is our inventory (can't use getHolder() because it's null from deserialization)
        if (event.getInventory().equals(inventory)) {
            event.setCancelled(true);
        }
    }

    /**
     * Unregister listener when GUI is closed to prevent memory leaks
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if this is our inventory
        if (event.getInventory().equals(inventory)) {
            // Unregister this listener
            HandlerList.unregisterAll(this);
        }
    }
}
