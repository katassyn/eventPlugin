package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.gui.MapSelectionGUI;
import org.maks.eventPlugin.fullmoon.gui.QuestGUI;
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.gui.Map1SelectionGUI;
import org.maks.eventPlugin.newmoon.gui.NewMoonQuestGUI;

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
    private final NewMoonManager newMoonManager;
    private final Map1SelectionGUI newMoonMapSelectionGUI;
    private final NewMoonQuestGUI newMoonQuestGUI;
    private final EventRewardPreviewDAO rewardPreviewDAO;

    // Winter Event components
    private final org.maks.eventPlugin.winterevent.WinterEventManager winterEventManager;
    private final org.maks.eventPlugin.winterevent.summit.gui.DifficultySelectionGUI winterDifficultyGUI;
    private final org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveGUI winterCaveGUI;

    private final Map<UUID, Inventory> openGUIs = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> slotToEventId = new HashMap<>();
    private final Map<UUID, PermissionAttachment> tempPermissions = new HashMap<>();

    public EventsMainGUI(
            JavaPlugin plugin,
            Map<String, EventManager> eventManagers,
            PlayerProgressGUI progressGUI,
            FullMoonManager fullMoonManager,
            MapSelectionGUI mapSelectionGUI,
            QuestGUI questGUI,
            NewMoonManager newMoonManager,
            Map1SelectionGUI newMoonMapSelectionGUI,
            NewMoonQuestGUI newMoonQuestGUI,
            org.maks.eventPlugin.winterevent.WinterEventManager winterEventManager,
            org.maks.eventPlugin.winterevent.summit.gui.DifficultySelectionGUI winterDifficultyGUI,
            org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveGUI winterCaveGUI,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.eventManagers = eventManagers;
        this.progressGUI = progressGUI;
        this.fullMoonManager = fullMoonManager;
        this.mapSelectionGUI = mapSelectionGUI;
        this.questGUI = questGUI;
        this.newMoonManager = newMoonManager;
        this.newMoonMapSelectionGUI = newMoonMapSelectionGUI;
        this.newMoonQuestGUI = newMoonQuestGUI;
        this.winterEventManager = winterEventManager;
        this.winterDifficultyGUI = winterDifficultyGUI;
        this.winterCaveGUI = winterCaveGUI;
        this.rewardPreviewDAO = new EventRewardPreviewDAO(plugin, databaseManager);
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
            }
            // Special handling for New Moon event
            else if (eventId.equalsIgnoreCase("new_moon")) {
                ItemStack newMoonItem = createNewMoonItem(player, manager);
                inv.setItem(slot, newMoonItem);
                eventSlots.put(slot, eventId);
                slot++;
            }
            // Special handling for Winter Event
            else if (eventId.equalsIgnoreCase("winter_event")) {
                ItemStack winterItem = createWinterEventItem(player, manager);
                inv.setItem(slot, winterItem);
                eventSlots.put(slot, eventId);
                slot++;
            }
            else {
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
        lore.add("§7MMB -> possible rewards");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create item for New Moon event.
     */
    private ItemStack createNewMoonItem(Player player, EventManager manager) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5§lNew Moon Event");
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7The new moon brings darkness to the Black Bog!");
        lore.add("§7Face mythical creatures and powerful lords.");
        lore.add("");
        lore.add("§7Features:");
        lore.add("§7  • §eNormal §7and §cHard §7difficulty");
        lore.add("§7  • Dual quest chains (White & Black)");
        lore.add("§7  • Solo lord realm challenges");
        lore.add("§7  • Draken currency system");
        lore.add("");

        int progress = manager.getProgress(player);
        int max = manager.getMaxProgress();
        int percentage = (int) ((progress / (double) max) * 100);
        lore.add("§7Your Progress: §e" + progress + "§7/§e" + max + " §7(" + percentage + "%)");
        lore.add("");

        // Check if player has unlocked portals (only if newMoonManager is available)
        if (newMoonManager != null) {
            boolean whiteUnlocked = newMoonManager.getQuestManager().hasUnlockedWhitePortal(player.getUniqueId());
            boolean blackUnlocked = newMoonManager.getQuestManager().hasUnlockedBlackPortal(player.getUniqueId());

            if (whiteUnlocked && blackUnlocked) {
                lore.add("§a✔ White Realm Unlocked!");
                lore.add("§a✔ Black Realm Unlocked!");
            } else if (whiteUnlocked) {
                lore.add("§a✔ White Realm Unlocked!");
                lore.add("§7Complete Black Chain to unlock Black Realm");
            } else if (blackUnlocked) {
                lore.add("§a✔ Black Realm Unlocked!");
                lore.add("§7Complete White Chain to unlock White Realm");
            } else {
                lore.add("§7Complete quests to unlock realms");
            }
        }

        lore.add("");
        lore.add("§e§lCLICK to select difficulty!");
        lore.add("§7Right-click to view quests");
        lore.add("§7MMB -> possible rewards");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create item for Winter Event.
     */
    private ItemStack createWinterEventItem(Player player, EventManager manager) {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lWinter Event");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Celebrate winter with gifts and challenges!");
        lore.add("§7Face the cold and collect rare rewards.");
        lore.add("");
        lore.add("§7Features:");
        lore.add("§7  • §aGlobal gift drops §7(6 rarities)");
        lore.add("§7  • §eWinter Cave §7(daily gifts)");
        lore.add("§7  • §cWinter Summit §7(3 difficulties)");
        lore.add("§7  • §6Epic boss battles");
        lore.add("");

        int progress = manager.getProgress(player);
        int max = manager.getMaxProgress();
        int percentage = (int) ((progress / (double) max) * 100);
        lore.add("§7Your Progress: §e" + progress + "§7/§e" + max + " §7(" + percentage + "%)");
        lore.add("");

        // Check Winter Cave access
        if (player.hasPermission("eventplugin.winter_cave")) {
            lore.add("§a✔ Winter Cave Access Granted!");
        } else {
            lore.add("§c✖ No Winter Cave Access");
        }

        lore.add("");
        lore.add("§e§lCLICK to select difficulty!");
        lore.add("§7Right-click to enter Winter Cave");
        lore.add("§7MMB -> possible rewards");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create item for regular event (like Monster Hunt).
     */
    private ItemStack createRegularEventItem(Player player, EventManager manager, String eventId) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l" + manager.getName());
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

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
        lore.add("§7MMB -> possible rewards");

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

        // Handle MIDDLE (MMB) click for rewards preview
        if (event.getClick() == ClickType.MIDDLE) {
            player.closeInventory();

            // Check if showcase rewards exist
            if (rewardPreviewDAO.hasShowcaseRewards(eventId)) {
                rewardPreviewDAO.openShowcasePreview(player, eventId);
            } else {
                player.sendMessage("§c§lNo rewards preview configured for this event yet!");
                player.sendMessage("§7An admin must use §e/seteventshowcase " + eventId + " §7to set it up.");
            }
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
        }
        // Special handling for New Moon
        else if (eventId.equalsIgnoreCase("new_moon")) {
            player.closeInventory();

            if (newMoonManager == null || newMoonQuestGUI == null || newMoonMapSelectionGUI == null) {
                player.sendMessage("§c§lNew Moon event is not properly initialized!");
                return;
            }

            if (event.isRightClick()) {
                // Right click = Open quest GUI
                // Grant temporary permission for quest GUI
                grantTemporaryPermission(player, "eventplugin.newmoon.quests");
                newMoonQuestGUI.open(player);
                // Schedule permission removal check after GUI closes
                schedulePermissionRemovalCheck(player);
            } else {
                // Left click = Open map selection (difficulty selection)
                // Grant temporary permission for map selection GUI
                grantTemporaryPermission(player, "eventplugin.newmoon.gui");
                newMoonMapSelectionGUI.open(player);
                // Schedule permission removal check after GUI closes
                schedulePermissionRemovalCheck(player);
            }
        }
        // Special handling for Winter Event
        else if (eventId.equalsIgnoreCase("winter_event")) {
            player.closeInventory();

            if (winterEventManager == null || winterDifficultyGUI == null || winterCaveGUI == null) {
                player.sendMessage("§c§lWinter Event is not properly initialized!");
                return;
            }

            if (event.isRightClick()) {
                // Right click = Open Winter Cave GUI
                if (player.hasPermission("eventplugin.winter_cave")) {
                    grantTemporaryPermission(player, "eventplugin.winter_cave");
                    winterCaveGUI.open(player);
                    schedulePermissionRemovalCheck(player);
                } else {
                    player.sendMessage("§c§l[Winter Event] §cYou don't have permission to enter Winter Cave!");
                }
            } else {
                // Left click = Open Difficulty Selection GUI
                grantTemporaryPermission(player, "eventplugin.winterevent.gui");
                winterDifficultyGUI.open(player);
                schedulePermissionRemovalCheck(player);
            }
        }
        else {
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
