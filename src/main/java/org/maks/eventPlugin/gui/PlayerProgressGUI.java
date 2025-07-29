package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.eventsystem.EventManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgressGUI implements Listener {
    private final EventManager eventManager;
    private final Map<UUID, Inventory> open = new HashMap<>();

    public PlayerProgressGUI(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void open(Player player) {
        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, "Event Progress");

        int progress = eventManager.getProgress(player);
        int max = eventManager.getMaxProgress();
        int filledSlots = (int) ((double) progress / max * (size - 9));

        ItemStack filled = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = filled.getItemMeta();
        meta.setDisplayName("§eProgress " + progress + " / " + max);
        filled.setItemMeta(meta);

        ItemStack empty = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);

        for (int i = 0; i < size - 9; i++) {
            inv.setItem(i, i < filledSlots ? filled : empty);
        }

        int index = size - 9;
        for (var reward : eventManager.getRewards()) {
            ItemStack rewardItem = reward.item().clone();
            ItemMeta m = rewardItem.getItemMeta();
            m.setDisplayName("§6Reward at " + reward.requiredProgress());
            m.setLore(java.util.List.of("Requires: " + reward.requiredProgress()));
            rewardItem.setItemMeta(m);
            inv.setItem(index++, rewardItem);
            if (index >= size) break;
        }

        open.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = open.get(player.getUniqueId());
        if (inv == null || !event.getInventory().equals(inv)) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        for (var reward : eventManager.getRewards()) {
            if (item.isSimilar(reward.item())) {
                if (eventManager.claimReward(player, reward.requiredProgress())) {
                    player.getInventory().addItem(reward.item().clone());
                    player.sendMessage("§aReward claimed!");
                } else {
                    player.sendMessage("§cYou cannot claim this reward yet.");
                }
                break;
            }
        }
    }
}
