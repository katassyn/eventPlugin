package org.maks.eventPlugin.winterevent.summit.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.winterevent.WinterEventManager;
import org.maks.eventPlugin.winterevent.summit.WinterSummitManager;
import org.maks.eventPlugin.winterevent.summit.listener.WinterPortalListener;

import java.util.*;

/**
 * Portal confirmation GUI for Winter Summit (Bear/Krampus) across Infernal/Hell/Blood.
 * Shows entry cost and requirements, asks player to confirm.
 */
public class WinterPortalConfirmationGUI implements Listener {

    private final WinterEventManager winterEventManager;
    private final WinterSummitManager summitManager;
    private final ConfigManager config;
    private WinterPortalListener portalListener;

    private final Map<UUID, PortalEntry> pendingEntries = new HashMap<>();
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();
    private final Set<UUID> buttonClicked = new HashSet<>();

    public WinterPortalConfirmationGUI(WinterEventManager winterEventManager,
                                       WinterSummitManager summitManager,
                                       WinterPortalListener portalListener) {
        this.winterEventManager = winterEventManager;
        this.summitManager = summitManager;
        this.config = winterEventManager.getConfig();
        this.portalListener = portalListener;
    }

    public void setPortalListener(WinterPortalListener portalListener) {
        this.portalListener = portalListener;
    }

    public boolean hasOpenGUI(UUID playerId) {
        return openGUIs.containsKey(playerId);
    }

    public void open(Player player, String bossType, String difficulty, Location portalLocation) {
        UUID pid = player.getUniqueId();
        pendingEntries.put(pid, new PortalEntry(bossType, difficulty, portalLocation));

        String title = "§8§lEnter §b§lWinter Summit§8§l?";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Background filler
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Info item in center
        ItemStack info = new ItemStack(Material.ENDER_EYE);
        ItemMeta im = info.getItemMeta();
        String diffColor = switch (difficulty) {
            case "blood" -> "§4";
            case "hell" -> "§6";
            default -> "§a"; // infernal
        };
        String bossColor = bossType.equals("krampus") ? "§c" : "§6";
        String niceDiff = capitalize(difficulty);
        String niceBoss = bossType.equals("krampus") ? "Krampus" : "Greedy Bear";
        im.setDisplayName("§b§lWinter Summit: " + bossColor + "§l" + niceBoss + " " + diffColor + "§l[" + niceDiff + "]");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7You are about to challenge:");
        lore.add(bossColor + "§l" + niceBoss);
        lore.add("§7Difficulty: " + diffColor + niceDiff);
        lore.add("");
        lore.add("§7Requirements:");
        int levelReq = getLevelRequirement(difficulty);
        int ipsReq = getIPSRequirement(difficulty);
        lore.add("  §e• Level: §f" + levelReq);
        lore.add("  §e• IPS: §f" + ipsReq);
        lore.add("");
        lore.add("§7Entry Cost:");
        int itemCost = getItemCost(bossType, difficulty);
        String itemId = getItemId(bossType);
        lore.add("  §e• " + itemCost + "x " + prettyItem(itemId));
        lore.add("");
        boolean meets = meetsRequirements(player, difficulty, bossType);
        if (meets) lore.add("§a✔ You meet all requirements");
        else lore.add("§c✘ You don't meet the requirements");
        lore.add("");
        lore.add("§7Click §a§lCONFIRM §7to enter or §c§lCANCEL §7to go back");
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(13, info);

        // Confirm button (11)
        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cm = confirm.getItemMeta();
        cm.setDisplayName("§a§l✔ CONFIRM ENTRY");
        confirm.setItemMeta(cm);
        inv.setItem(11, confirm);

        // Cancel button (15)
        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta ccm = cancel.getItemMeta();
        ccm.setDisplayName("§c§l✘ CANCEL");
        cancel.setItemMeta(ccm);
        inv.setItem(15, cancel);

        openGUIs.put(pid, inv);

        var result = player.openInventory(inv);
        if (result == null) {
            Bukkit.getLogger().severe("[Winter Portal GUI] Failed to open inventory for " + player.getName());
            openGUIs.remove(pid);
            pendingEntries.remove(pid);
            return;
        }
    }

    private boolean meetsRequirements(Player player, String difficulty, String bossType) {
        int levelReq = getLevelRequirement(difficulty);
        if (player.getLevel() < levelReq) return false;
        int ipsReq = getIPSRequirement(difficulty);
        if (ipsReq > 0 && PouchHelper.isAvailable() && !PouchHelper.hasEnough(player, "ips", ipsReq)) return false;
        int itemCost = getItemCost(bossType, difficulty);
        String itemId = getItemId(bossType);
        if (PouchHelper.isAvailable() && !PouchHelper.hasEnough(player, itemId, itemCost)) return false;
        return true;
    }

    private int getLevelRequirement(String difficulty) {
        String path = "winter_event.summit.requirements." + difficulty + ".level";
        return config.getConfig().getInt(path, 0);
    }

    private int getIPSRequirement(String difficulty) {
        String path = "winter_event.summit.requirements." + difficulty + ".ips";
        return config.getConfig().getInt(path, 0);
    }

    private int getItemCost(String bossType, String difficulty) {
        String path = "winter_event.summit.boss_costs." + bossType + "." + difficulty;
        return config.getConfig().getInt(path, 1);
    }

    private String getItemId(String bossType) {
        return bossType.equals("krampus") ? "winter_solstice_log" : "honey_bait";
    }

    private String prettyItem(String id) {
        return switch (id) {
            case "winter_solstice_log" -> "Winter Solstice Log";
            case "honey_bait" -> "Honey Bait";
            default -> id;
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID pid = player.getUniqueId();
        Inventory inv = openGUIs.get(pid);
        if (inv == null || !event.getInventory().equals(inv)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PortalEntry entry = pendingEntries.get(pid);
        if (entry == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 11) { // Confirm
            buttonClicked.add(pid);

            if (!meetsRequirements(player, entry.difficulty, entry.bossType)) {
                player.sendMessage("§c§l[Winter Event] §cYou don't meet the requirements!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                // Treat as ESC to set cooldown
                buttonClicked.remove(pid);
                player.closeInventory();
                return;
            }

            int ipsReq = getIPSRequirement(entry.difficulty);
            String itemId = getItemId(entry.bossType);
            int itemCost = getItemCost(entry.bossType, entry.difficulty);

            // Consume costs safely
            boolean consumedIPS = false;
            if (ipsReq > 0 && PouchHelper.isAvailable()) {
                if (!PouchHelper.consumeItem(player, "ips", ipsReq)) {
                    player.sendMessage("§c§l[Winter Event] §cFailed to consume IPS!");
                    player.closeInventory();
                    return;
                }
                consumedIPS = true;
            }

            if (PouchHelper.isAvailable() && !PouchHelper.consumeItem(player, itemId, itemCost)) {
                player.sendMessage("§c§l[Winter Event] §cFailed to consume entry items!");
                if (consumedIPS) {
                    PouchHelper.addItem(player, "ips", ipsReq);
                }
                player.closeInventory();
                return;
            }

            // Create boss instance
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            player.sendMessage("§f§l[Winter Event] §aCreating boss arena...");

            var instance = summitManager.createBossInstance(player, entry.bossType, entry.difficulty);
            if (instance == null) {
                // Refund costs on failure
                if (PouchHelper.isAvailable()) {
                    PouchHelper.addItem(player, itemId, itemCost);
                    if (ipsReq > 0) PouchHelper.addItem(player, "ips", ipsReq);
                }
                player.sendMessage("§c§l[Winter Event] §cFailed to create boss arena!");
                return;
            }

            if (portalListener != null) {
                portalListener.clearGUICooldown(pid);
            }

        } else if (slot == 15) { // Cancel
            buttonClicked.add(pid);
            if (portalListener != null) portalListener.setDeclineCooldown(pid);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID pid = player.getUniqueId();
        Inventory tracked = openGUIs.get(pid);

        // Only process if the closing inventory is the one we're tracking
        if (tracked == null || !event.getInventory().equals(tracked)) {
            return;
        }

        boolean clicked = buttonClicked.remove(pid);
        openGUIs.remove(pid);
        pendingEntries.remove(pid);

        if (!clicked && portalListener != null) {
            portalListener.setDeclineCooldown(pid);
        }
    }

    private static class PortalEntry {
        final String bossType;
        final String difficulty;
        final Location portalLocation;
        PortalEntry(String bossType, String difficulty, Location portalLocation) {
            this.bossType = bossType;
            this.difficulty = difficulty;
            this.portalLocation = portalLocation;
        }
    }
}
