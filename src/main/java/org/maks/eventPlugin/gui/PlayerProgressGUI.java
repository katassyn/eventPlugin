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
import org.maks.eventPlugin.eventsystem.BuffManager;
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
            1, 3, 4, 5, 7,
            // row 1 (right → left)
            16, 14, 12, 10,
            // row 2 (left → right)
            19, 21, 23, 25,
            // row 3 (right → left)
            34, 32, 30, 28,
            // row 4 (left → right)
            37, 38, 39, 41, 42, 43
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
    private final BuffManager buffManager;

    public PlayerProgressGUI(BuffManager buffManager) {
        this.buffManager = buffManager;
    }

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
        List<String> lore = new ArrayList<>(Arrays.asList(eventManager.getDescription().split("\\n")));
        lore.add("Ends in: " + TimeUtil.formatDuration(eventManager.getTimeRemaining()));
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(53, info);

        boolean attrie = buffManager.hasBuff(player);
        ItemStack attrieItem = new ItemStack(Material.PAPER);
        ItemMeta am = attrieItem.getItemMeta();
        am.setDisplayName("§dAttrie bonus: " + (attrie ? "ON" : "OFF"));
        List<String> aLore = new ArrayList<>();
        if (attrie) {
            aLore.add("Remaining: " + TimeUtil.formatDuration(buffManager.getRemaining(player)));
        } else {
            aLore.add("Not active");
        }
        am.setLore(aLore);
        attrieItem.setItemMeta(am);
        inv.setItem(45, attrieItem);

        Session session = new Session();
        session.inv = inv;
        session.manager = eventManager;


        Set<Integer> usedReward = new HashSet<>();
        for (var reward : eventManager.getRewards()) {
            int pathIndex = (int) Math.ceil(reward.requiredProgress() / perSlot) - 1;
            if (pathIndex < 0) pathIndex = 0;
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
            boolean claimed = eventManager.hasClaimed(player, reward.requiredProgress());
            String status;
            if (claimed) {
                status = "§aClaimed";
            } else if (unlocked) {
                status = "§aClick to claim!";
            } else {
                status = "§cNot yet unlocked";
            }
            rm.setLore(Arrays.asList(
                    "Required: §6" + reward.requiredProgress() + "§7 points",
                    status
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
            int progress = session.manager.getProgress(player);
            if (session.manager.hasClaimed(player, req)) {
                player.sendMessage("§cAlready claimed");
                return;
            }
            if (progress < req) {
                player.sendMessage("§cNot yet unlocked");
                return;
            }
            if (session.manager.claimReward(player, req)) {
                session.manager.getRewards().stream()
                        .filter(r -> r.requiredProgress() == req)
                        .findFirst()
                        .ifPresent(r -> player.getInventory().addItem(r.item().clone()));

                // Update item lore to show claimed
                ItemStack item = event.getInventory().getItem(event.getRawSlot());
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setLore(Arrays.asList(
                                "Required: §6" + req + "§7 points",
                                "§aClaimed"
                        ));
                        item.setItemMeta(meta);
                        event.getInventory().setItem(event.getRawSlot(), item);
                    }
                }
                player.sendMessage("§aReward claimed!");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
