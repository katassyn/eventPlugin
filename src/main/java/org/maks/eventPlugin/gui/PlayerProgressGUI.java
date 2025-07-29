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
import org.maks.eventPlugin.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgressGUI implements Listener {
    private static class Session {
        Inventory inv;
        EventManager manager;
    }

    private final Map<UUID, Session> open = new HashMap<>();

    public PlayerProgressGUI() {
    }

    public void open(Player player, EventManager eventManager) {
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

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b" + eventManager.getName());
        infoMeta.setLore(java.util.List.of(
                eventManager.getDescription(),
                "Ends in: " + TimeUtil.formatDuration(eventManager.getTimeRemaining())
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(size - 1, info);

        int index = size - 9;
        for (var reward : eventManager.getRewards()) {
            ItemStack rewardItem = reward.item().clone();
            ItemMeta m = rewardItem.getItemMeta();
            m.setDisplayName("§6Reward at " + reward.requiredProgress());
            m.setLore(java.util.List.of("Requires: " + reward.requiredProgress()));
            rewardItem.setItemMeta(m);
            inv.setItem(index++, rewardItem);
            if (index >= size - 1) break;
        }

        Session session = new Session();
        session.inv = inv;
        session.manager = eventManager;
        open.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Session session = open.get(player.getUniqueId());
        if (session == null || !event.getInventory().equals(session.inv)) return;
        EventManager eventManager = session.manager;
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

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
