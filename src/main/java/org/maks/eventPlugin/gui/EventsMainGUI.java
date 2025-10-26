package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.gui.MapSelectionGUI;
import org.maks.eventPlugin.fullmoon.gui.QuestGUI;

import java.util.*;

/**
 * Main events hub GUI that shows all active events.
 * Players can click on an event to access its specific features.
 */
public class EventsMainGUI implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, EventManager> eventManagers;
    private final PlayerProgressGUI progressGUI;
    private final FullMoonManager fullMoonManager;
    private final MapSelectionGUI mapSelectionGUI;
    private final QuestGUI questGUI;

    private final Map<UUID, Inventory> openGUIs = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> slotToEventId = new HashMap<>();
    private final Map<UUID, PermissionAttachment> tempPermissions = new HashMap<>();

    public EventsMainGUI(
            JavaPlugin plugin,
            Map<String, EventManager> eventManagers,
            PlayerProgressGUI progressGUI,
            FullMoonManager fullMoonManager,
            MapSelectionGUI mapSelectionGUI,
            QuestGUI questGUI
    ) {
        this.plugin = plugin;
        this.eventManagers = eventManagers;
        this.progressGUI = progressGUI;
        this.fullMoonManager = fullMoonManager;
        this.mapSelectionGUI = mapSelectionGUI;
        this.questGUI = questGUI;
    }

    /**
     * Open the main events hub GUI for a player.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lActive Events");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        Map<Integer, String> eventSlots = new HashMap<>();
        int slot = 10; // Start position for event icons

        // Check all events and display active ones
        for (Map.Entry<String, EventManager> entry : eventManagers.entrySet()) {
            String eventId = entry.getKey();
            EventManager manager = entry.getValue();

            if (!manager.isActive()) continue;

            // Special handling for Full Moon event
            if (eventId.equalsIgnoreCase("full_moon")) {
                ItemStack fullMoonItem = createFullMoonItem(player, manager);
                inv.setItem(slot, fullMoonItem);
                eventSlots.put(slot, eventId);
                slot++;
            } else {
                // Regular event
                ItemStack eventItem = createRegularEventItem(player, manager, eventId);
                inv.setItem(slot, eventItem);
                eventSlots.put(slot, eventId);
                slot++;
            }

            // Move to next row if needed
            if (slot % 9 >= 7) {
                slot = ((slot / 9) + 1) * 9 + 1;
            }
        }

        // If no active events
        if (eventSlots.isEmpty()) {
            ItemStack noEvents = new ItemStack(Material.BARRIER);
            ItemMeta noEventsMeta = noEvents.getItemMeta();
            noEventsMeta.setDisplayName("§c§lNo Active Events");
            List<String> noEventsLore = new ArrayList<>();
            noEventsLore.add("");
            noEventsLore.add("§7There are currently no active events.");
            noEventsLore.add("§7Check back later!");
            noEventsMeta.setLore(noEventsLore);
            noEvents.setItemMeta(noEventsMeta);
            inv.setItem(22, noEvents);
        }

        openGUIs.put(player.getUniqueId(), inv);
        slotToEventId.put(player.getUniqueId(), eventSlots);
        player.openInventory(inv);
    }

    /**
     * Create item for Full Moon event.
     */
    private ItemStack createFullMoonItem(Player player, EventManager manager) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lFull Moon Event");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7The full moon rises over Warhlom!");
        lore.add("§7Battle werewolves and blood mages.");
        lore.add("");
        lore.add("§7Features:");
        lore.add("§7  • §eNormal §7and §cHard §7difficulty");
        lore.add("§7  • Quest system with rewards");
        lore.add("§7  • Solo Blood Moon Arena");
        lore.add("");

        int progress = manager.getProgress(player);
        int max = manager.getMaxProgress();
        int percentage = (int) ((progress / (double) max) * 100);
        lore.add("§7Your Progress: §e" + progress + "§7/§e" + max + " §7(" + percentage + "%)");
        lore.add("");

        // Check if player has unlocked Map 2
        if (fullMoonManager.getQuestManager().hasUnlockedMap2(player.getUniqueId())) {
            lore.add("§a✔ Blood Moon Arena Unlocked!");
        } else {
            lore.add("§7Complete quests to unlock arena");
        }

        lore.add("");
        lore.add("§e§lCLICK to select difficulty!");
        lore.add("§7Right-click to view quests");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create item for regular event (like Monster Hunt).
     */
    private ItemStack createRegularEventItem(Player player, EventManager manager, String eventId) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l" + manager.getName());

        List<String> lore = new ArrayList<>();
        lore.add("");

        String[] descLines = manager.getDescription().split("\\n");
        for (String line : descLines) {
            lore.add("§7" + line);
        }

        lore.add("");

        int progress = manager.getProgress(player);
        int max = manager.getMaxProgress();
        int percentage = (int) ((progress / (double) max) * 100);
        lore.add("§7Your Progress: §e" + progress + "§7/§e" + max + " §7(" + percentage + "%)");
        lore.add("");
        lore.add("§e§lCLICK to view rewards!");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = openGUIs.get(player.getUniqueId());

        if (inv == null || !event.getInventory().equals(inv)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        Map<Integer, String> eventSlots = slotToEventId.get(player.getUniqueId());
        if (eventSlots == null) return;

        String eventId = eventSlots.get(slot);
        if (eventId == null) return;

        EventManager manager = eventManagers.get(eventId);
        if (manager == null || !manager.isActive()) {
            player.sendMessage("§c§lThis event is no longer active!");
            player.closeInventory();
            return;
        }

        // Special handling for Full Moon
        if (eventId.equalsIgnoreCase("full_moon")) {
            player.closeInventory();

            if (event.isRightClick()) {
                // Right click = Open quest GUI
                // Grant temporary permission for quest GUI
                grantTemporaryPermission(player, "eventplugin.fullmoon.quests");
                questGUI.open(player);
                // Schedule permission removal check after GUI closes
                schedulePermissionRemovalCheck(player);
            } else {
                // Left click = Open map selection
                // Grant temporary permission for map selection GUI
                grantTemporaryPermission(player, "eventplugin.fullmoon.gui");
                mapSelectionGUI.open(player);
                // Schedule permission removal check after GUI closes
                schedulePermissionRemovalCheck(player);
            }
        } else {
            // Regular event - open progress GUI
            player.closeInventory();
            progressGUI.open(player, manager);
        }
    }

    /**
     * Grant temporary permission to a player.
     * This permission will be automatically removed when they close the GUI.
     */
    private void grantTemporaryPermission(Player player, String permission) {
        // Remove any existing temporary permission first
        removeTemporaryPermission(player);

        // Create new permission attachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission(permission, true);
        tempPermissions.put(player.getUniqueId(), attachment);
    }

    /**
     * Remove temporary permission from a player.
     */
    public void removeTemporaryPermission(Player player) {
        PermissionAttachment attachment = tempPermissions.remove(player.getUniqueId());
        if (attachment != null) {
            attachment.remove();
        }
    }

    /**
     * Schedule a task to check if player has closed the GUI and remove temporary permission.
     * This runs periodically until the GUI is closed.
     */
    private void schedulePermissionRemovalCheck(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            private int checks = 0;
            private final int maxChecks = 100; // Max 5 seconds (100 ticks)

            @Override
            public void run() {
                checks++;

                // If player is offline or max checks reached, remove permission
                if (!player.isOnline() || checks >= maxChecks) {
                    removeTemporaryPermission(player);
                    return;
                }

                // If player has no open inventory, remove permission
                if (player.getOpenInventory().getTopInventory().getSize() == 0) {
                    removeTemporaryPermission(player);
                    return;
                }

                // Otherwise, check again in 1 tick
                Bukkit.getScheduler().runTaskLater(plugin, this, 1L);
            }
        }, 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openGUIs.remove(playerId);
        slotToEventId.remove(playerId);
    }
}
