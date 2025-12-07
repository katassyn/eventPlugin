package org.maks.eventPlugin.winterevent.wintercave.gui;

import org.bukkit.Bukkit;
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
import org.maks.eventPlugin.winterevent.wintercave.WinterCaveManager;

import java.util.*;

/**
 * Admin GUI for managing Winter Cave daily rewards (days 1-30).
 * Works like quest reward editor - place items, then click SAVE.
 */
public class WinterCaveRewardsGUI implements Listener {
    private final WinterCaveManager caveManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    public WinterCaveRewardsGUI(WinterCaveManager caveManager) {
        this.caveManager = caveManager;
    }

    public void open(Player player) {
        // Check permission
        if (!player.hasPermission("eventplugin.admin.winter_cave_rewards")) {
            player.sendMessage("§c§l[Winter Event] §cYou don't have permission to access this GUI!");
            return;
        }

        // Create inventory
        Inventory inv = Bukkit.createInventory(null, 54, "§8§lWinter Cave Rewards (Days 1-30)");

        // Load current rewards into slots 0-29 (days 1-30)
        for (int day = 1; day <= 30; day++) {
            ItemStack reward = caveManager.getRewardDAO().getDayReward(day);
            if (reward != null) {
                inv.setItem(day - 1, reward.clone());
            }
        }

        // SAVE button (slot 49)
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveBtn.getItemMeta();
        saveMeta.setDisplayName("§a§l✔ SAVE ALL REWARDS");
        List<String> saveLore = new ArrayList<>();
        saveLore.add("");
        saveLore.add("§7Click to save all items in slots 0-29");
        saveLore.add("§7as rewards for days 1-30");
        saveLore.add("");
        saveLore.add("§cWarning: §7This will overwrite all current rewards!");
        saveLore.add("");
        saveLore.add("§e§lCLICK TO SAVE");
        saveMeta.setLore(saveLore);
        saveBtn.setItemMeta(saveMeta);
        inv.setItem(49, saveBtn);

        // Info item (slot 50)
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lHow to Use");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§71. Place reward items in slots 0-29");
        infoLore.add("§7   (Slot 0 = Day 1, Slot 29 = Day 30)");
        infoLore.add("§72. Click the §a§lSAVE§7 button");
        infoLore.add("§73. Empty slots = no reward for that day");
        infoLore.add("");
        infoLore.add("§e§lTip: §7You can shift-click items from");
        infoLore.add("§7your inventory to quickly fill slots!");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(50, info);

        // CLEAR ALL button (slot 52)
        ItemStack clearBtn = new ItemStack(Material.RED_CONCRETE);
        ItemMeta clearMeta = clearBtn.getItemMeta();
        clearMeta.setDisplayName("§c§l✘ CLEAR ALL");
        List<String> clearLore = new ArrayList<>();
        clearLore.add("");
        clearLore.add("§7Removes all items from slots 0-29");
        clearLore.add("§7(Does NOT save to database)");
        clearLore.add("");
        clearLore.add("§e§lCLICK TO CLEAR");
        clearMeta.setLore(clearLore);
        clearBtn.setItemMeta(clearMeta);
        inv.setItem(52, clearBtn);

        // BACK button (slot 53)
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName("§c§l← CLOSE");
        List<String> backLore = new ArrayList<>();
        backLore.add("");
        backLore.add("§cUnsaved changes will be lost!");
        backMeta.setLore(backLore);
        backBtn.setItemMeta(backMeta);
        inv.setItem(53, backBtn);

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
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

        int slot = event.getRawSlot();

        // Allow placing items in slots 0-29 (reward slots)
        if (slot >= 0 && slot < 30) {
            // Allow normal inventory interaction
            return;
        }

        // Allow interaction with player's own inventory (bottom inventory)
        if (slot >= 54) {
            return;
        }

        // Cancel clicks on control buttons and outside slots
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // SAVE button (slot 49)
        if (slot == 49) {
            saveAllRewards(player, clickedInv);
            return;
        }

        // CLEAR ALL button (slot 52)
        if (slot == 52) {
            for (int i = 0; i < 30; i++) {
                clickedInv.setItem(i, null);
            }
            player.sendMessage("§f§l[Winter Event] §eCleared all reward slots (not saved yet)");
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            return;
        }

        // BACK button (slot 53)
        if (slot == 53) {
            player.closeInventory();
            return;
        }
    }

    /**
     * Save all items from slots 0-29 to database as rewards for days 1-30.
     */
    private void saveAllRewards(Player player, Inventory inv) {
        int savedCount = 0;

        // Save each slot (0-29) as day (1-30) reward
        for (int i = 0; i < 30; i++) {
            ItemStack item = inv.getItem(i);
            int day = i + 1;

            if (item != null && item.getType() != Material.AIR) {
                caveManager.getRewardDAO().setDayReward(day, item.clone());
                savedCount++;
            } else {
                // Empty slot = remove reward for that day
                caveManager.getRewardDAO().setDayReward(day, null);
            }
        }

        player.sendMessage("§f§l[Winter Event] §aSaved " + savedCount + " rewards for 30 days!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openGUIs.remove(playerId);
    }
}
