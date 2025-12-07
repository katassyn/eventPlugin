package org.maks.eventPlugin.winterevent.summit.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.winterevent.WinterEventManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for selecting Winter Summit difficulty level.
 * 27-slot inventory with Infernal, Hell, and Blood options.
 */
public class DifficultySelectionGUI implements Listener {
    private final WinterEventManager winterEventManager;
    private final ConfigManager config;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    public DifficultySelectionGUI(WinterEventManager winterEventManager, ConfigManager config) {
        this.winterEventManager = winterEventManager;
        this.config = config;
    }

    public void open(Player player) {
        if (!winterEventManager.isEventActive()) {
            player.sendMessage("§c§l[Winter Event] §cEvent is not active!");
            return;
        }

        // Create inventory
        Inventory inv = Bukkit.createInventory(null, 27, "§8§lWinter Summit - Select Difficulty");

        // Fill with black stained glass pane
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Infernal (slot 10)
        inv.setItem(10, createDifficultyItem(player, "infernal", Material.IRON_SWORD));

        // Hell (slot 13)
        inv.setItem(13, createDifficultyItem(player, "hell", Material.DIAMOND_SWORD));

        // Blood (slot 16)
        inv.setItem(16, createDifficultyItem(player, "blood", Material.NETHERITE_SWORD));

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private ItemStack createDifficultyItem(Player player, String difficulty, Material material) {
        String configPath = "winter_event.summit.requirements." + difficulty;
        int requiredLevel = config.getSection(configPath).getInt("level");
        int requiredIPS = config.getSection(configPath).getInt("ips");

        boolean meetsLevel = player.getLevel() >= requiredLevel;
        boolean meetsIPS = requiredIPS == 0 || (PouchHelper.isAvailable() && PouchHelper.hasEnough(player, "ips", requiredIPS));
        boolean meetsRequirements = meetsLevel && meetsIPS;

        ItemStack item = new ItemStack(meetsRequirements ? material : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        String displayName = difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1);
        meta.setDisplayName((meetsRequirements ? "§a§l" : "§c§l") + displayName + " Mode");

        meta.setLore(Arrays.asList(
                "§7Required Level: " + (meetsLevel ? "§a" : "§c") + requiredLevel,
                "§7Required IPS: " + (meetsIPS ? "§a" : "§c") + requiredIPS,
                "",
                meetsRequirements ? "§eClick to warp to Winter Summit" : "§cYou don't meet the requirements!"
        ));

        // Hide vanilla attributes (attack damage, attack speed, etc.)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInv = openGUIs.get(player.getUniqueId());

        if (clickedInv == null || !event.getInventory().equals(clickedInv)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BARRIER) {
            return;
        }

        // Determine difficulty
        String difficulty = null;
        if (event.getSlot() == 10) {
            difficulty = "infernal";
        } else if (event.getSlot() == 13) {
            difficulty = "hell";
        } else if (event.getSlot() == 16) {
            difficulty = "blood";
        }

        if (difficulty == null) {
            return;
        }

        // Check requirements
        String configPath = "winter_event.summit.requirements." + difficulty;
        int requiredLevel = config.getSection(configPath).getInt("level");
        int requiredIPS = config.getSection(configPath).getInt("ips");

        if (player.getLevel() < requiredLevel) {
            player.sendMessage("§c§l[Winter Event] §cYou need level " + requiredLevel + "!");
            return;
        }

        if (requiredIPS > 0 && PouchHelper.isAvailable()) {
            if (!PouchHelper.hasEnough(player, "ips", requiredIPS)) {
                player.sendMessage("§c§l[Winter Event] §cYou need " + requiredIPS + " IPS!");
                return;
            }
            // Consume IPS
            if (!PouchHelper.consumeItem(player, "ips", requiredIPS)) {
                player.sendMessage("§c§l[Winter Event] §cFailed to consume IPS!");
                return;
            }
        }

        // Warp player
        String warp = config.getSection(configPath).getString("warp");
        player.closeInventory();
        openGUIs.remove(player.getUniqueId());

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warp " + warp + " " + player.getName());
        player.sendMessage("§f§l[Winter Event] §aTeleporting to Winter Summit (" + difficulty + " mode)...");
    }
}
