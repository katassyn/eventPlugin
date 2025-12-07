package org.maks.eventPlugin.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.gui.EventRewardPreviewDAO;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Command to set up showcase rewards for events.
 * Admins use this to configure which items are displayed in the rewards preview GUI.
 * Usage: /seteventshowcase <event_id>
 */
public class SetEventShowcaseCommand implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final EventRewardPreviewDAO previewDAO;

    // Map to track which players are currently editing which event showcase
    private final Map<UUID, String> editingSessions = new HashMap<>();

    // Default inventory size for showcase GUIs (3 rows)
    private static final int DEFAULT_GUI_SIZE = 27;

    public SetEventShowcaseCommand(JavaPlugin plugin, EventRewardPreviewDAO previewDAO) {
        this.plugin = plugin;
        this.previewDAO = previewDAO;

        // Register the listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Check if player has permission
        Player player = (Player) sender;
        if (!player.hasPermission("eventplugin.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Check if command has the correct number of arguments
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /seteventshowcase <event_id>");
            player.sendMessage(ChatColor.YELLOW + "Examples:");
            player.sendMessage(ChatColor.GRAY + "  /seteventshowcase full_moon");
            player.sendMessage(ChatColor.GRAY + "  /seteventshowcase new_moon");
            player.sendMessage(ChatColor.GRAY + "  /seteventshowcase monster_hunt");
            return true;
        }

        // Get the event ID from arguments
        String eventId = args[0].toLowerCase();

        // Validate event ID format
        if (!isValidEventId(eventId)) {
            player.sendMessage(ChatColor.RED + "Invalid event ID. Use lowercase with underscores (e.g., 'full_moon', 'new_moon').");
            return true;
        }

        // Create or get existing GUI
        Inventory showcaseGUI = previewDAO.getShowcaseInventory(eventId);
        boolean isNewShowcase = (showcaseGUI == null);

        if (showcaseGUI == null) {
            // Create a new GUI with white glass panes as default
            String title = ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "Set Showcase: " +
                          ChatColor.WHITE + formatEventName(eventId);
            showcaseGUI = Bukkit.createInventory(null, DEFAULT_GUI_SIZE, title);

            // Fill with white glass panes
            ItemStack filler = createFillerItem();
            for (int i = 0; i < DEFAULT_GUI_SIZE; i++) {
                showcaseGUI.setItem(i, filler);
            }
        }

        // Open the GUI for the player
        player.openInventory(showcaseGUI);

        // Store the editing session
        editingSessions.put(player.getUniqueId(), eventId);

        // Send instructions
        sendInstructions(player, eventId, isNewShowcase);

        return true;
    }

    private void sendInstructions(Player player, String eventId, boolean isNew) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "§l» Setting up showcase for " + formatEventName(eventId));
        player.sendMessage(ChatColor.YELLOW + "§l» §r§eArrange items as you want them displayed");
        player.sendMessage(ChatColor.YELLOW + "§l» §r§eYou have 3 rows (27 slots) available");
        player.sendMessage(ChatColor.YELLOW + "§l» §r§eEmpty slots will show as white glass");

        if (isNew) {
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "§l» §r§bLAYOUT TIPS:");
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "By Rarity: " +
                             ChatColor.GRAY + "Arrange from common to mythic");
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "Diamond: " +
                             ChatColor.GRAY + "Place best item in center (slot 13)");
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "Rows: " +
                             ChatColor.GRAY + "Group by type (weapons/armor/consumables)");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "§l» §r§aClose the inventory to save changes");
        player.sendMessage("");
    }

    private ItemStack createFillerItem() {
        ItemStack filler = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        return filler;
    }

    /**
     * Handle inventory close event to save showcase GUIs
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player was editing a showcase GUI
        if (editingSessions.containsKey(playerId)) {
            String eventId = editingSessions.get(playerId);
            Inventory inventory = event.getInventory();

            // Count items in the showcase
            int itemCount = 0;
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR &&
                    item.getType() != Material.WHITE_STAINED_GLASS_PANE) {
                    itemCount++;
                }
            }

            // Save the showcase GUI
            previewDAO.saveShowcaseRewards(eventId, inventory);

            // Remove the editing session
            editingSessions.remove(playerId);

            // Notify the player
            showSaveConfirmation(player, eventId, itemCount);
        }
    }

    private void showSaveConfirmation(Player player, String eventId, int itemCount) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "§l» §r§aShowcase rewards saved successfully!");
        player.sendMessage(ChatColor.YELLOW + "§l» §r§eEvent: " + formatEventName(eventId));

        if (itemCount > 0) {
            player.sendMessage(ChatColor.YELLOW + "§l» §r§eShowcase items: " + itemCount);
            player.sendMessage(ChatColor.GRAY + "§l» §r§7Players can now view these rewards via MMB in /event_hub!");
        } else {
            player.sendMessage(ChatColor.GRAY + "§l» §r§7Empty showcase saved (only glass panes)");
        }

        player.sendMessage("");
    }

    /**
     * Validate event ID format
     * @param eventId The event ID to validate
     * @return True if the event ID is valid, false otherwise
     */
    private boolean isValidEventId(String eventId) {
        // Allow alphanumeric with underscores
        return eventId.matches("[a-z0-9_]+");
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
