package org.maks.eventPlugin.winterevent.gui;

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
import org.maks.eventPlugin.winterevent.WinterQuest;
import org.maks.eventPlugin.winterevent.WinterQuestManager;

import java.util.*;

/**
 * Dual-column quest GUI for Winter Event.
 * Bear Chain (left) vs Krampus Chain (right).
 */
public class WinterQuestGUI implements Listener {

    private final WinterQuestManager questManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    // Dual column slots
    private static final int[] BEAR_SLOTS = {10, 19, 28, 37, 46, 11, 20};     // 7 quests, left column
    private static final int[] KRAMPUS_SLOTS = {16, 25, 34, 43, 52, 15, 24}; // 7 quests, right column

    public WinterQuestGUI(WinterQuestManager questManager) {
        this.questManager = questManager;
    }

    /**
     * Open the quest GUI for a player.
     */
    public void open(Player player) {
        UUID playerId = player.getUniqueId();

        Inventory inv = Bukkit.createInventory(null, 54, "§b§lWinter Event - Quests");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        // Add chain headers
        inv.setItem(2, createChainHeader("bear"));
        inv.setItem(6, createChainHeader("krampus"));

        // Add Bear Chain quests (1-7)
        List<WinterQuest> bearQuests = questManager.getQuestsForChain("bear");
        for (int i = 0; i < bearQuests.size() && i < BEAR_SLOTS.length; i++) {
            WinterQuest quest = bearQuests.get(i);
            int slot = BEAR_SLOTS[i];

            boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
            boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
            boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
            boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());
            int progress = questManager.getQuestProgress(playerId, quest.id());

            ItemStack questItem = createQuestItem(quest, isUnlocked, isAccepted, isCompleted, isClaimed, progress);
            inv.setItem(slot, questItem);
        }

        // Add Krampus Chain quests (8-14)
        List<WinterQuest> krampusQuests = questManager.getQuestsForChain("krampus");
        for (int i = 0; i < krampusQuests.size() && i < KRAMPUS_SLOTS.length; i++) {
            WinterQuest quest = krampusQuests.get(i);
            int slot = KRAMPUS_SLOTS[i];

            boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
            boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
            boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
            boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());
            int progress = questManager.getQuestProgress(playerId, quest.id());

            ItemStack questItem = createQuestItem(quest, isUnlocked, isAccepted, isCompleted, isClaimed, progress);
            inv.setItem(slot, questItem);
        }

        // Add chain completion status
        inv.setItem(48, createChainStatus(playerId, "bear"));
        inv.setItem(50, createChainStatus(playerId, "krampus"));

        openGUIs.put(playerId, inv);
        player.openInventory(inv);
    }

    /**
     * Create a chain header item.
     */
    private ItemStack createChainHeader(String chainType) {
        Material mat = chainType.equals("bear") ? Material.HONEYCOMB : Material.COAL;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (chainType.equals("bear")) {
            meta.setDisplayName("§6§l🐻 BEAR CHAIN");
        } else {
            meta.setDisplayName("§5§l👹 KRAMPUS CHAIN");
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§77 sequential quests");
        lore.add("§7Complete in order to unlock the next");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a chain completion status item.
     */
    private ItemStack createChainStatus(UUID playerId, String chainType) {
        boolean completed = chainType.equals("bear") ?
            questManager.hasBearChainCompleted(playerId) :
            questManager.hasKrampusChainCompleted(playerId);

        Material mat = completed ? Material.EMERALD_BLOCK : Material.YELLOW_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String name = chainType.equals("bear") ? "§6§lBear Chain" : "§5§lKrampus Chain";
        meta.setDisplayName(name + (completed ? " §a§lCOMPLETE!" : " §e§lIN PROGRESS"));

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (completed) {
            lore.add("§a✔ All quests completed!");
            lore.add("§a✔ All rewards claimed!");
        } else {
            lore.add("§7Complete all 7 quests");
            lore.add("§7to finish this chain!");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a quest item with appropriate state.
     */
    private ItemStack createQuestItem(WinterQuest quest, boolean isUnlocked, boolean isAccepted,
                                     boolean isCompleted, boolean isClaimed, int progress) {
        Material mat;
        String status;
        String action = "";

        if (isClaimed) {
            mat = Material.EMERALD_BLOCK;
            status = "§a§lCLAIMED";
        } else if (isCompleted) {
            mat = Material.GOLD_BLOCK;
            status = "§6§lCOMPLETE - CLAIM REWARD";
            action = "§e§lCLICK TO CLAIM REWARDS!";
        } else if (isAccepted) {
            mat = Material.YELLOW_CONCRETE;
            status = "§e§lIN PROGRESS";
        } else if (isUnlocked) {
            mat = Material.LIME_CONCRETE;
            status = "§a§lREADY TO ACCEPT";
            action = "§e§lCLICK TO ACCEPT QUEST!";
        } else {
            mat = Material.RED_CONCRETE;
            status = "§c§lLOCKED";
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§lQuest " + quest.id() + ": §r" + status);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + quest.description());
        lore.add("");

        if (isAccepted && !isCompleted) {
            // Show progress bar
            String progressBar = createProgressBar(progress, quest.requiredKills());
            lore.add("§7Progress: §e" + progress + "§7/§e" + quest.requiredKills());
            lore.add(progressBar);
        }

        if (!isUnlocked) {
            lore.add("§cComplete and claim the previous quest to unlock");
        }

        if (quest.isBloodOnly()) {
            lore.add("");
            lore.add("§c§l⚠ Blood Difficulty Only!");
        }

        if (quest.isCollectionQuest()) {
            lore.add("");
            lore.add("§e§lCollection Quest");
            lore.add("§7Drops randomly from kills");
        }

        if (isCompleted && !isClaimed && !quest.rewards().isEmpty()) {
            lore.add("");
            lore.add("§6§lRewards:");
            for (ItemStack reward : quest.rewards()) {
                lore.add("§7  - §f" + reward.getAmount() + "x " + reward.getType().name());
            }
        }

        if (!action.isEmpty()) {
            lore.add("");
            lore.add(action);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a progress bar.
     */
    private String createProgressBar(int current, int max) {
        int bars = 20;
        int filled = (int) ((current / (double) max) * bars);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? "§a§l|" : "§7§l|");
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
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        // Find which quest was clicked
        WinterQuest clickedQuest = null;
        for (int i = 0; i < BEAR_SLOTS.length; i++) {
            if (BEAR_SLOTS[i] == slot) {
                List<WinterQuest> bearQuests = questManager.getQuestsForChain("bear");
                if (i < bearQuests.size()) {
                    clickedQuest = bearQuests.get(i);
                }
                break;
            }
        }
        for (int i = 0; i < KRAMPUS_SLOTS.length; i++) {
            if (KRAMPUS_SLOTS[i] == slot) {
                List<WinterQuest> krampusQuests = questManager.getQuestsForChain("krampus");
                if (i < krampusQuests.size()) {
                    clickedQuest = krampusQuests.get(i);
                }
                break;
            }
        }

        if (clickedQuest == null) {
            return; // Not a quest slot
        }

        boolean isUnlocked = questManager.isQuestUnlocked(playerId, clickedQuest.id());
        boolean isAccepted = questManager.isQuestAccepted(playerId, clickedQuest.id());
        boolean isCompleted = questManager.isQuestCompleted(playerId, clickedQuest.id());
        boolean isClaimed = questManager.hasClaimedReward(playerId, clickedQuest.id());

        // Handle claim rewards
        if (isCompleted && !isClaimed) {
            // Check inventory space
            int requiredSlots = calculateRequiredSlots(player, clickedQuest.rewards());
            int emptySlots = countEmptySlots(player);

            if (emptySlots < requiredSlots) {
                player.sendMessage("§c§l[Winter Event] §cYou need at least " + requiredSlots + " empty slots!");
                player.sendMessage("§c§lFree up space and try again.");
                return;
            }

            if (questManager.claimReward(playerId, clickedQuest.id())) {
                for (ItemStack reward : clickedQuest.rewards()) {
                    player.getInventory().addItem(reward.clone());
                }
                player.sendMessage("§a§l[Winter Event] §aQuest " + clickedQuest.id() + " rewards claimed!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                open(player); // Refresh GUI
            }
        }

        // Handle accept quest
        if (isUnlocked && !isAccepted && !isCompleted) {
            if (questManager.acceptQuest(playerId, clickedQuest.id())) {
                player.sendMessage("§a§l[Winter Event] §aQuest " + clickedQuest.id() + " accepted!");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                open(player); // Refresh GUI
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openGUIs.remove(playerId);
    }

    /**
     * Calculate required inventory slots for items.
     */
    private int calculateRequiredSlots(Player player, List<ItemStack> items) {
        Map<Material, Integer> stackableAmounts = new HashMap<>();
        int nonStackableCount = 0;

        for (ItemStack item : items) {
            if (item.getMaxStackSize() == 1) {
                nonStackableCount += item.getAmount();
            } else {
                stackableAmounts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        int stackableSlots = 0;
        for (Map.Entry<Material, Integer> entry : stackableAmounts.entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();
            int maxStack = new ItemStack(mat).getMaxStackSize();

            // Check existing stacks in player inventory
            int existingAmount = 0;
            int partialStacks = 0;
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && invItem.getType() == mat) {
                    existingAmount += invItem.getAmount();
                    if (invItem.getAmount() < maxStack) {
                        partialStacks++;
                    }
                }
            }

            int totalAmount = existingAmount + amount;
            int totalStacks = (int) Math.ceil((double) totalAmount / maxStack);
            int currentStacks = (int) Math.ceil((double) existingAmount / maxStack);
            stackableSlots += Math.max(0, totalStacks - currentStacks);
        }

        return stackableSlots + nonStackableCount;
    }

    /**
     * Count empty slots in player inventory.
     */
    private int countEmptySlots(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                count++;
            }
        }
        return count;
    }
}
