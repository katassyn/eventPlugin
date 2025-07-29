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
    private static final List<Integer> PATH_SLOTS = List.of(
            1, 3, 4, 5, 7,
            10, 12, 14, 16,
            19, 21, 23, 25,
            28, 30, 32, 34,
            37, 38, 39, 41, 42, 43
    );
        for (int i = 0; i < 54; i++) {
            if (!PATH_SLOTS.contains(i)) REWARD_SLOTS.add(i);
        int progress = eventManager.getProgress(player);
        int max = eventManager.getMaxProgress();
                eventManager.getName() + " - " + progress + "/" + max);

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
