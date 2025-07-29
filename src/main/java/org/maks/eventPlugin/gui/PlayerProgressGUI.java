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
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.util.TimeUtil;

import java.util.*;

public class PlayerProgressGUI implements Listener {
    /**
     * Ordered slots representing the progress path. The order follows the
     * visual snake-like track defined in the specification so that progress
     * fills along the path correctly.
     */
    private static final List<Integer> PATH_SLOTS = List.of(
            // row 0 (left → right)
            1, 3, 4, 5, 6,
            // row 1 (right → left)
            15, 14, 12, 10,
            // row 2 (left → right)
            19, 21, 23, 24, 26,
            // row 3 (right → left)
            34, 32, 31, 29,
            // row 4 (left → right)
            38, 40, 42, 44,
            // row 5 (right → left)
            51, 48
    );
    private static final List<Integer> REWARD_SLOTS = new ArrayList<>();
    private static final Map<Integer, List<Integer>> PATH_TO_REWARD = new HashMap<>();

    static {
        for (int i = 0; i < 54; i++) {
            if (!PATH_SLOTS.contains(i)) REWARD_SLOTS.add(i);
        }
        REWARD_SLOTS.remove(Integer.valueOf(53));
        for (int i = 0; i < PATH_SLOTS.size(); i++) {
            int slot = PATH_SLOTS.get(i);
            int row = slot / 9;
            int col = slot % 9;
            for (int rSlot : REWARD_SLOTS) {
                int rr = rSlot / 9;
                int rc = rSlot % 9;
                if (Math.abs(row - rr) + Math.abs(col - rc) == 1) {
                    PATH_TO_REWARD.computeIfAbsent(i, k -> new ArrayList<>()).add(rSlot);
                }
            }
        }
    }

    private static class Session {
        Inventory inv;
        EventManager manager;
        Map<Integer, Integer> rewardSlots = new HashMap<>();
    }

    private static String shortNumber(int n) {
        return n >= 1000 ? (n / 1000) + "k" : String.valueOf(n);
    }

    private final Map<UUID, Session> open = new HashMap<>();

    public void open(Player player, EventManager eventManager) {
        int progress = eventManager.getProgress(player);
        int max = eventManager.getMaxProgress();
        Inventory inv = Bukkit.createInventory(null, 54,
                eventManager.getName() + " - " +
                        shortNumber(progress) + "/" + shortNumber(max) + " - " +
                        TimeUtil.formatDuration(eventManager.getTimeRemaining()));

        double perSlot = (double) max / PATH_SLOTS.size();
        int filled = (int) Math.floor(progress / perSlot);

        ItemStack filledItem = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack emptyItem = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta mFilled = filledItem.getItemMeta();
        ItemMeta mEmpty = emptyItem.getItemMeta();
        String numbers = "§e" + progress + "§7 / §e" + max;
        mFilled.setDisplayName("§eProgress: " + numbers);
        mEmpty.setDisplayName("§fProgress: " + numbers);
        filledItem.setItemMeta(mFilled);
        emptyItem.setItemMeta(mEmpty);

        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        for (int i = 0; i < PATH_SLOTS.size(); i++) {
            int slot = PATH_SLOTS.get(i);
            inv.setItem(slot, i < filled ? filledItem : emptyItem);
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b" + eventManager.getName());
        infoMeta.setLore(Arrays.asList(
                eventManager.getDescription(),
                "Ends in: " + TimeUtil.formatDuration(eventManager.getTimeRemaining())
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(53, info);

        Session session = new Session();
        session.inv = inv;
        session.manager = eventManager;

        Set<Integer> usedReward = new HashSet<>();
        for (var reward : eventManager.getRewards()) {
            int pathIndex = (int) Math.floor(reward.requiredProgress() / perSlot);
            if (pathIndex >= PATH_SLOTS.size()) pathIndex = PATH_SLOTS.size() - 1;
            List<Integer> candidates = PATH_TO_REWARD.get(pathIndex);
            int slot = -1;
            if (candidates != null) {
                for (int c : candidates) if (usedReward.add(c)) { slot = c; break; }
            }
            if (slot == -1) {
                for (int c : REWARD_SLOTS) if (usedReward.add(c)) { slot = c; break; }
            }
            if (slot == -1) continue;

            ItemStack rewardItem = reward.item().clone();
            ItemMeta rm = rewardItem.getItemMeta();
            boolean unlocked = progress >= reward.requiredProgress();
            rm.setLore(Arrays.asList(
                    "Required: §6" + reward.requiredProgress() + "§7 points",
                    unlocked ? "§aClick to claim!" : "§cNot yet unlocked"
            ));
            rewardItem.setItemMeta(rm);
            inv.setItem(slot, rewardItem);
            session.rewardSlots.put(slot, reward.requiredProgress());
        }

        open.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Session session = open.get(player.getUniqueId());
        if (session == null || !event.getInventory().equals(session.inv)) return;
        event.setCancelled(true);
        Integer req = session.rewardSlots.get(event.getRawSlot());
        if (req != null) {
            if (session.manager.claimReward(player, req)) {
                session.manager.getRewards().stream()
                        .filter(r -> r.requiredProgress() == req)
                        .findFirst()
                        .ifPresent(r -> player.getInventory().addItem(r.item().clone()));
                player.sendMessage("§aReward claimed!");
            } else {
                player.sendMessage("§cNot yet unlocked");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
