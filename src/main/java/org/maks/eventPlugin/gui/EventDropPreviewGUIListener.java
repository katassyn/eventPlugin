package org.maks.eventPlugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener for EventDropPreviewGUI.
 * Handles click events and prevents item manipulation.
 */
public class EventDropPreviewGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // Check if this is our drop preview GUI
        if (!(holder instanceof EventDropPreviewGUI)) {
            return;
        }

        EventDropPreviewGUI gui = (EventDropPreviewGUI) holder;
        Player player = (Player) event.getWhoClicked();

        // Cancel all clicks to prevent item manipulation
        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Handle clicks (close button, etc.)
        gui.handleClick(slot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // Block dragging items in our GUI
        if (holder instanceof EventDropPreviewGUI) {
            event.setCancelled(true);
        }
    }
}
