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
     * Ordered slots representing the progress path. The numbers follow the
     * original vertical snake that starts in the top-left corner, winds down to
     * the bottom, then back up through subsequent columns.
     */
    private static final List<Integer> PATH_SLOTS = List.of(
            // column 1 downward
            1, 10, 19, 28, 37,
            // bottom row sweep to the right
            38, 39,
            // column 3 upward
            30, 21, 12, 3,
            // top row across
            4, 5,
            // column 5 downward
            14, 23, 32, 41,
            // bottom row sweep
            42, 43,
            // column 7 upward to finish
            34, 25, 16, 7

    );

    /**
     * Slots used to display rewards. Each entry corresponds to the adjacent
     * progress slot at the same index in {@link #PATH_SLOTS}. The numbers were
     * chosen so that rewards visually follow the vertical snake path rather
     * than stacking in simple rows.
     */
    private static final List<Integer> REWARD_SLOTS = List.of(
            0, 9, 18, 27, 36,
            47, 48,
            29, 20, 11, 2,
            13, 6,
            15, 22, 31, 50,
            51, 52,
            33, 24, 17, 8

    );


    private static class Session {
        Inventory inv;
        EventManager manager;
        Map<Integer, Integer> rewardSlots = new HashMap<>();
        List<String> eventIds = new ArrayList<>();
        int currentIndex;
        Map<String, EventManager> allEvents;
    }

    private static String shortNumber(int n) {
        return n >= 1000 ? (n / 1000) + "k" : String.valueOf(n);
    }

    private final Map<UUID, Session> open = new HashMap<>();
    private final BuffManager buffManager;
    private Map<String, EventManager> allEvents;

    public PlayerProgressGUI(BuffManager buffManager) {
        this.buffManager = buffManager;
    }

    public void setAllEvents(Map<String, EventManager> allEvents) {
        this.allEvents = allEvents;
    }

    public void open(Player player, EventManager eventManager) {
        int progress = eventManager.getProgress(player);
        int max = eventManager.getMaxProgress();
        Inventory inv = Bukkit.createInventory(null, 54,
                eventManager.getName() + " - " +
                        shortNumber(progress) + "/" + shortNumber(max) + " - " +
                        TimeUtil.formatDuration(eventManager.getTimeRemaining()));

        // Using purely integer math avoids floating point rounding issues when
        // calculating which progress slots should be filled. This ensures that
        // both the progress glass panes and reward markers line up perfectly
        // with the configured maximum progress value.
        int filled = (int) ((long) progress * PATH_SLOTS.size() / Math.max(1, max));

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
        session.allEvents = allEvents;

        // Find all active events and current index
        if (allEvents != null) {
            for (Map.Entry<String, EventManager> entry : allEvents.entrySet()) {
                if (entry.getValue().isActive()) {
                    session.eventIds.add(entry.getKey());
                }
            }
            // Find current event ID
            String currentEventId = null;
            for (Map.Entry<String, EventManager> entry : allEvents.entrySet()) {
                if (entry.getValue() == eventManager) {
                    currentEventId = entry.getKey();
                    break;
                }
            }
            if (currentEventId != null) {
                session.currentIndex = session.eventIds.indexOf(currentEventId);
            }
        }

        Set<Integer> usedReward = new HashSet<>();
        for (var reward : eventManager.getRewards()) {
            // Map required progress to the index of the progress path using
            // ceiling arithmetic so that a reward for progress slightly above
            // a threshold still appears next to the correct glass pane. This
            // mirrors the player's view where each pane represents an equal
            // slice of the maximum progress.
            long numerator = (long) reward.requiredProgress() * PATH_SLOTS.size() + max - 1;
            int pathIndex = (int) (numerator / Math.max(1, max)) - 1;
            if (pathIndex < 0) pathIndex = 0;
            if (pathIndex >= PATH_SLOTS.size()) pathIndex = PATH_SLOTS.size() - 1;

            int slot = -1;
            for (int i = pathIndex; i < REWARD_SLOTS.size(); i++) {
                int candidate = REWARD_SLOTS.get(i);
                if (usedReward.add(candidate)) { slot = candidate; break; }
            }
            if (slot == -1) {
                for (int i = pathIndex - 1; i >= 0; i--) {
                    int candidate = REWARD_SLOTS.get(i);
                    if (usedReward.add(candidate)) { slot = candidate; break; }
                }

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

        // Add navigation arrows if multiple events exist
        if (session.eventIds.size() > 1) {
            // Previous event arrow (slot 46)
            if (session.currentIndex > 0) {
                ItemStack prevArrow = new ItemStack(Material.ARROW);
                ItemMeta prevMeta = prevArrow.getItemMeta();
                prevMeta.setDisplayName("§e← Previous Event");
                prevArrow.setItemMeta(prevMeta);
                inv.setItem(46, prevArrow);
            }

            // Next event arrow (slot 49)
            if (session.currentIndex < session.eventIds.size() - 1) {
                ItemStack nextArrow = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = nextArrow.getItemMeta();
                nextMeta.setDisplayName("§eNext Event →");
                nextArrow.setItemMeta(nextMeta);
                inv.setItem(49, nextArrow);
            }
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

        int slot = event.getRawSlot();

        // Handle navigation arrows
        if (slot == 46 && session.currentIndex > 0) {
            // Previous event
            String prevEventId = session.eventIds.get(session.currentIndex - 1);
            EventManager prevManager = session.allEvents.get(prevEventId);
            if (prevManager != null) {
                // Don't remove session here - let the new session overwrite it
                open(player, prevManager);
            }
            return;
        } else if (slot == 49 && session.currentIndex < session.eventIds.size() - 1) {
            // Next event
            String nextEventId = session.eventIds.get(session.currentIndex + 1);
            EventManager nextManager = session.allEvents.get(nextEventId);
            if (nextManager != null) {
                // Don't remove session here - let the new session overwrite it
                open(player, nextManager);
            }
            return;
        }

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
                // EventManager.claimReward() already gives all rewards to the player

                // Update all items with this required progress to show claimed
                for (Map.Entry<Integer, Integer> entry : session.rewardSlots.entrySet()) {
                    if (entry.getValue().equals(req)) {
                        ItemStack item = event.getInventory().getItem(entry.getKey());
                        if (item != null) {
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                meta.setLore(Arrays.asList(
                                        "Required: §6" + req + "§7 points",
                                        "§aClaimed"
                                ));
                                item.setItemMeta(meta);
                                event.getInventory().setItem(entry.getKey(), item);
                            }
                        }
                    }
                }
                player.sendMessage("§aReward claimed!");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Only remove session if the closed inventory matches the stored session
        // This prevents removing a new session when switching between events
        Session session = open.get(event.getPlayer().getUniqueId());
        if (session != null && event.getInventory().equals(session.inv)) {
            open.remove(event.getPlayer().getUniqueId());
        }
    }
}
