package org.maks.eventPlugin.fullmoon.gui;

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
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.Quest;
import org.maks.eventPlugin.fullmoon.QuestManager;

import java.util.*;

/**
 * GUI for viewing Full Moon quest progress.
 */
public class QuestGUI implements Listener {

    private final FullMoonManager fullMoonManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    // Quest slots in the GUI
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15};

    public QuestGUI(FullMoonManager fullMoonManager) {
        this.fullMoonManager = fullMoonManager;
    }

    /**
     * Open the quest GUI for a player.
     */
    public void open(Player player) {
        if (!fullMoonManager.isEventActive()) {
            player.sendMessage("§c§l[Full Moon] §cThe Full Moon event is not currently active!");
            return;
        }

        QuestManager questManager = fullMoonManager.getQuestManager();
        UUID playerId = player.getUniqueId();

        Inventory inv = Bukkit.createInventory(null, 54, "§8§lFull Moon - Quests");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        // Add all quests
        List<Quest> quests = questManager.getAllQuests();
        for (int i = 0; i < quests.size() && i < QUEST_SLOTS.length; i++) {
            Quest quest = quests.get(i);
            int slot = QUEST_SLOTS[i];

            boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
            boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
            boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
            boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());
            int progress = questManager.getQuestProgress(playerId, quest.id());

            ItemStack questItem = createQuestItem(quest, isUnlocked, isAccepted, isCompleted, isClaimed, progress);
            inv.setItem(slot, questItem);
        }

        // Add info item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lFull Moon Quests");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7Complete quests in order to");
        infoLore.add("§7unlock the next quest!");
        infoLore.add("");
        infoLore.add("§7Complete Quest 4 to unlock");
        infoLore.add("§7access to the §cBlood Moon Arena§7!");
        infoLore.add("");

        // Count completed quests
        int completed = 0;
        for (Quest q : quests) {
            if (questManager.isQuestCompleted(playerId, q.id())) {
                completed++;
            }
        }
        infoLore.add("§eProgress: §6" + completed + "§7/§6" + quests.size() + " §7quests");

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // Add map 2 unlock status
        boolean map2Unlocked = questManager.hasUnlockedMap2(playerId);
        ItemStack map2Item = new ItemStack(map2Unlocked ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta map2Meta = map2Item.getItemMeta();
        map2Meta.setDisplayName(map2Unlocked ? "§a§lBlood Moon Arena - UNLOCKED" : "§c§lBlood Moon Arena - LOCKED");
        List<String> map2Lore = new ArrayList<>();
        map2Lore.add("");
        if (map2Unlocked) {
            map2Lore.add("§aYou have unlocked access to the");
            map2Lore.add("§aBlood Moon Arena!");
            map2Lore.add("");
            map2Lore.add("§7Defeat §cAmarok §7to receive an");
            map2Lore.add("§7option to enter the arena.");
        } else {
            map2Lore.add("§7Complete §eQuest 4 §7to unlock");
            map2Lore.add("§7access to this challenging arena!");
        }
        map2Lore.add("");
        map2Meta.setLore(map2Lore);
        map2Item.setItemMeta(map2Meta);
        inv.setItem(53, map2Item);

        openGUIs.put(playerId, inv);
        player.openInventory(inv);
    }

    /**
     * Create an ItemStack representing a quest.
     */
    private ItemStack createQuestItem(Quest quest, boolean isUnlocked, boolean isAccepted, boolean isCompleted, boolean isClaimed, int progress) {
        Material material;
        String status;
        String colorCode;
        String actionPrompt = "";

        // Determine state and appearance
        if (isClaimed) {
            // Quest completed and rewards claimed
            material = Material.EMERALD_BLOCK;
            status = "§a§lCLAIMED";
            colorCode = "§a";
        } else if (isCompleted) {
            // Quest completed but rewards not claimed
            material = Material.GOLD_BLOCK;
            status = "§6§lCOMPLETE - CLAIM REWARD";
            colorCode = "§6";
            actionPrompt = "§e§lCLICK TO CLAIM REWARDS!";
        } else if (isAccepted) {
            // Quest accepted and in progress
            material = Material.YELLOW_CONCRETE;
            status = "§e§lIN PROGRESS";
            colorCode = "§e";
        } else if (isUnlocked) {
            // Quest unlocked but not accepted
            material = Material.LIME_CONCRETE;
            status = "§a§lREADY TO ACCEPT";
            colorCode = "§a";
            actionPrompt = "§e§lCLICK TO ACCEPT QUEST!";
        } else {
            // Quest locked
            material = Material.RED_CONCRETE;
            status = "§c§lLOCKED";
            colorCode = "§7";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(colorCode + "§lQuest " + quest.id());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + quest.description());
        lore.add("");


        // Show progress for accepted quests
        if (isAccepted && !isCompleted) {
            int percentage = (int) ((progress / (double) quest.requiredKills()) * 100);
            lore.add("§7Progress: §e" + progress + "§7/§e" + quest.requiredKills() + " §7(" + percentage + "%)");
            lore.add(createProgressBar(progress, quest.requiredKills()));
            lore.add("");
        }

        // Show state-specific messages
        if (isClaimed) {
            lore.add("§a✔ Rewards Claimed!");
            lore.add("");
        } else if (isCompleted) {
            lore.add("§a✔ Quest Completed!");
            lore.add("");
        } else if (!isUnlocked) {
            lore.add("§7Complete and claim the previous");
            lore.add("§7quest to unlock this one!");
            lore.add("");
        } else if (!isAccepted) {
            lore.add("§7Accept this quest to start");
            lore.add("§7tracking your progress!");
            lore.add("");
        }

        lore.add(status);

        // Action prompt
        if (!actionPrompt.isEmpty()) {
            lore.add("");
            lore.add(actionPrompt);
        }

        // Special notes
        if (quest.id() == 4) {
            lore.add("");
            lore.add("§7§oCompleting this quest unlocks");
            lore.add("§7§othe §c§oBlood Moon Arena§7§o!");
        } else if (quest.id() == 6) {
            lore.add("");
            lore.add("§c§oThis quest requires Hard mode!");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a visual progress bar.
     */
    private String createProgressBar(int current, int max) {
        int bars = 20;
        int filled = (int) ((current / (double) max) * bars);

        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                bar.append("§a§l|");
            } else {
                bar.append("§7§l|");
            }
        }
        bar.append("§7]");

        return bar.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory inv = openGUIs.get(playerId);

        if (inv == null || !event.getInventory().equals(inv)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        QuestManager questManager = fullMoonManager.getQuestManager();

        // Check if clicked slot is a quest slot
        int questIndex = -1;
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (QUEST_SLOTS[i] == slot) {
                questIndex = i;
                break;
            }
        }

        if (questIndex == -1) return; // Not a quest slot

        List<Quest> quests = questManager.getAllQuests();
        if (questIndex >= quests.size()) return;

        Quest quest = quests.get(questIndex);
        boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
        boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
        boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
        boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());

        // Handle different click actions based on quest state
        if (isClaimed) {
            // Already claimed - do nothing
            player.sendMessage("§a§l[Full Moon] §aYou have already claimed this reward!");
            return;
        }

        if (isCompleted && !isClaimed) {
            // Quest completed - claim reward
            if (questManager.claimReward(playerId, quest.id())) {
                // Give rewards to player
                for (ItemStack reward : quest.rewards()) {
                    player.getInventory().addItem(reward.clone());
                }

                player.sendMessage("§a§l[Full Moon] §aQuest " + quest.id() + " rewards claimed!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // Refresh GUI
                player.closeInventory();
                open(player);
            } else {
                player.sendMessage("§c§l[Full Moon] §cFailed to claim reward!");
            }
            return;
        }

        if (isUnlocked && !isAccepted && !isCompleted) {
            // Quest unlocked - accept it
            if (questManager.acceptQuest(playerId, quest.id())) {
                player.sendMessage("§a§l[Full Moon] §aQuest " + quest.id() + " accepted!");
                player.sendMessage("§7" + quest.description());
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

                // Refresh GUI
                player.closeInventory();
                open(player);
            } else {
                player.sendMessage("§c§l[Full Moon] §cFailed to accept quest!");
            }
            return;
        }

        // Quest is locked or in progress - just show message
        if (!isUnlocked) {
            player.sendMessage("§c§l[Full Moon] §cComplete the previous quest first!");
        } else if (isAccepted && !isCompleted) {
            int progress = questManager.getQuestProgress(playerId, quest.id());
            player.sendMessage("§e§l[Full Moon] §eProgress: " + progress + "/" + quest.requiredKills());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }
}
