package org.maks.eventPlugin.newmoon.gui;

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
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.NewMoonQuest;
import org.maks.eventPlugin.newmoon.NewMoonQuestManager;

import java.util.*;

/**
 * GUI for viewing New Moon quest progress.
 * Displays two independent quest chains side-by-side:
 * - Left: White Lord Chain (quests 1-5)
 * - Right: Black Lord Chain (quests 6-10)
 */
public class NewMoonQuestGUI implements Listener {

    private final NewMoonManager newMoonManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    // Quest slots for White Chain (left side)
    private static final int[] WHITE_CHAIN_SLOTS = {10, 19, 28, 37, 46};

    // Quest slots for Black Chain (right side)
    private static final int[] BLACK_CHAIN_SLOTS = {16, 25, 34, 43, 52};

    public NewMoonQuestGUI(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    /**
     * Open the quest GUI for a player.
     */
    public void open(Player player) {
        if (!newMoonManager.isEventActive()) {
            player.sendMessage("§c§l[New Moon] §cThe New Moon event is not currently active!");
            return;
        }

        NewMoonQuestManager questManager = newMoonManager.getQuestManager();
        UUID playerId = player.getUniqueId();

        Inventory inv = Bukkit.createInventory(null, 54, "§8§lNew Moon - Quest Chains");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        // White chain divider
        ItemStack whiteDivider = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta whiteMeta = whiteDivider.getItemMeta();
        whiteMeta.setDisplayName("§f§lWhite Lord Chain");
        List<String> whiteLore = new ArrayList<>();
        whiteLore.add("");
        whiteLore.add("§7Complete quests to unlock");
        whiteLore.add("§7the portal to the §f§lWhite Realm§7!");
        whiteMeta.setLore(whiteLore);
        whiteDivider.setItemMeta(whiteMeta);
        inv.setItem(1, whiteDivider);

        // Black chain divider
        ItemStack blackDivider = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta blackMeta = blackDivider.getItemMeta();
        blackMeta.setDisplayName("§5§lBlack Lord Chain");
        List<String> blackLore = new ArrayList<>();
        blackLore.add("");
        blackLore.add("§7Complete quests to unlock");
        blackLore.add("§7the portal to the §5§lBlack Realm§7!");
        blackMeta.setLore(blackLore);
        blackDivider.setItemMeta(blackMeta);
        inv.setItem(7, blackDivider);

        // Add White Chain quests (1-5)
        List<NewMoonQuest> whiteQuests = questManager.getQuestsForChain("white");
        for (int i = 0; i < whiteQuests.size() && i < WHITE_CHAIN_SLOTS.length; i++) {
            NewMoonQuest quest = whiteQuests.get(i);
            int slot = WHITE_CHAIN_SLOTS[i];

            boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
            boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
            boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
            boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());
            int progress = questManager.getQuestProgress(playerId, quest.id());

            ItemStack questItem = createQuestItem(quest, isUnlocked, isAccepted, isCompleted, isClaimed, progress);
            inv.setItem(slot, questItem);
        }

        // Add Black Chain quests (6-10)
        List<NewMoonQuest> blackQuests = questManager.getQuestsForChain("black");
        for (int i = 0; i < blackQuests.size() && i < BLACK_CHAIN_SLOTS.length; i++) {
            NewMoonQuest quest = blackQuests.get(i);
            int slot = BLACK_CHAIN_SLOTS[i];

            boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
            boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
            boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
            boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());
            int progress = questManager.getQuestProgress(playerId, quest.id());

            ItemStack questItem = createQuestItem(quest, isUnlocked, isAccepted, isCompleted, isClaimed, progress);
            inv.setItem(slot, questItem);
        }

        // Add info item (center bottom)
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lNew Moon Quest Chains");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7All 10 quests are available!");
        infoLore.add("§7Complete them in ANY order.");
        infoLore.add("");
        infoLore.add("§f§lWhite Chain §7→ Unlock §fWhite Realm");
        infoLore.add("§5§lBlack Chain §7→ Unlock §5Black Realm");
        infoLore.add("");
        infoLore.add("§eQuest 3 §7unlocks §fWhite Portal");
        infoLore.add("§eQuest 8 §7unlocks §5Black Portal");
        infoLore.add("");

        // Count completed quests per chain
        int whiteCompleted = (int) whiteQuests.stream()
                .filter(q -> questManager.isQuestCompleted(playerId, q.id()))
                .count();
        int blackCompleted = (int) blackQuests.stream()
                .filter(q -> questManager.isQuestCompleted(playerId, q.id()))
                .count();

        infoLore.add("§fWhite: §e" + whiteCompleted + "§7/§e5 §7complete");
        infoLore.add("§5Black: §e" + blackCompleted + "§7/§e5 §7complete");

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // Portal unlock status (bottom corners)
        boolean whitePortalUnlocked = questManager.hasUnlockedWhitePortal(playerId);
        ItemStack whitePortalItem = new ItemStack(whitePortalUnlocked ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta whitePortalMeta = whitePortalItem.getItemMeta();
        whitePortalMeta.setDisplayName(whitePortalUnlocked ? "§f§lWhite Realm - UNLOCKED" : "§7§lWhite Realm - LOCKED");
        List<String> whitePortalLore = new ArrayList<>();
        whitePortalLore.add("");
        if (whitePortalUnlocked) {
            whitePortalLore.add("§aYou have unlocked access to the");
            whitePortalLore.add("§f§lWhite Realm§a!");
        } else {
            whitePortalLore.add("§7Complete §eQuest 3 §7(White Chain)");
            whitePortalLore.add("§7to unlock this realm!");
        }
        whitePortalLore.add("");
        whitePortalMeta.setLore(whitePortalLore);
        whitePortalItem.setItemMeta(whitePortalMeta);
        inv.setItem(45, whitePortalItem);

        boolean blackPortalUnlocked = questManager.hasUnlockedBlackPortal(playerId);
        ItemStack blackPortalItem = new ItemStack(blackPortalUnlocked ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta blackPortalMeta = blackPortalItem.getItemMeta();
        blackPortalMeta.setDisplayName(blackPortalUnlocked ? "§5§lBlack Realm - UNLOCKED" : "§5§lBlack Realm - LOCKED");
        List<String> blackPortalLore = new ArrayList<>();
        blackPortalLore.add("");
        if (blackPortalUnlocked) {
            blackPortalLore.add("§aYou have unlocked access to the");
            blackPortalLore.add("§5§lBlack Realm§a!");
        } else {
            blackPortalLore.add("§7Complete §eQuest 8 §7(Black Chain)");
            blackPortalLore.add("§7to unlock this realm!");
        }
        blackPortalLore.add("");
        blackPortalMeta.setLore(blackPortalLore);
        blackPortalItem.setItemMeta(blackPortalMeta);
        inv.setItem(53, blackPortalItem);

        openGUIs.put(playerId, inv);
        player.openInventory(inv);
    }

    /**
     * Create an ItemStack representing a quest.
     */
    private ItemStack createQuestItem(NewMoonQuest quest, boolean isUnlocked, boolean isAccepted,
                                     boolean isCompleted, boolean isClaimed, int progress) {
        Material material;
        String status;
        String colorCode;
        String actionPrompt = "";

        // Determine state and appearance
        if (isClaimed) {
            material = Material.EMERALD_BLOCK;
            status = "§a§lCLAIMED";
            colorCode = "§a";
        } else if (isCompleted) {
            material = Material.GOLD_BLOCK;
            status = "§6§lCOMPLETE - CLAIM REWARD";
            colorCode = "§6";
            actionPrompt = "§e§lCLICK TO CLAIM REWARDS!";
        } else if (isAccepted) {
            material = Material.YELLOW_CONCRETE;
            status = "§e§lIN PROGRESS";
            colorCode = "§e";
        } else if (isUnlocked) {
            // Quest unlocked but not accepted yet - ready to accept
            material = Material.LIME_CONCRETE;
            status = "§a§lREADY TO ACCEPT";
            colorCode = "§a";
            actionPrompt = "§e§lCLICK TO ACCEPT QUEST!";
        } else {
            // Quest locked - previous quest must be completed and claimed
            material = Material.RED_CONCRETE;
            status = "§c§lLOCKED";
            colorCode = "§7";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Chain-specific coloring
        String chainColor = quest.chainType().equals("white") ? "§f" : "§5";
        meta.setDisplayName(chainColor + "§lQuest " + quest.id() + " §7(" + quest.getChainDisplayName() + "§7)");

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
            lore.add("§7quest in this chain to unlock!");
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
        if (quest.isPortalUnlockQuest()) {
            lore.add("");
            String realmName = quest.chainType().equals("white") ? "§f§lWhite Realm" : "§5§lBlack Realm";
            lore.add("§7§oCompleting this quest unlocks");
            lore.add("§7§othe " + realmName + "§7§o portal!");
        }
        if (quest.isHardMode()) {
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
        NewMoonQuestManager questManager = newMoonManager.getQuestManager();

        // Find which quest was clicked
        NewMoonQuest quest = null;

        // Check white chain slots
        for (int i = 0; i < WHITE_CHAIN_SLOTS.length; i++) {
            if (WHITE_CHAIN_SLOTS[i] == slot) {
                List<NewMoonQuest> whiteQuests = questManager.getQuestsForChain("white");
                if (i < whiteQuests.size()) {
                    quest = whiteQuests.get(i);
                    break;
                }
            }
        }

        // Check black chain slots if not found
        if (quest == null) {
            for (int i = 0; i < BLACK_CHAIN_SLOTS.length; i++) {
                if (BLACK_CHAIN_SLOTS[i] == slot) {
                    List<NewMoonQuest> blackQuests = questManager.getQuestsForChain("black");
                    if (i < blackQuests.size()) {
                        quest = blackQuests.get(i);
                        break;
                    }
                }
            }
        }

        if (quest == null) return; // Not a quest slot

        boolean isUnlocked = questManager.isQuestUnlocked(playerId, quest.id());
        boolean isAccepted = questManager.isQuestAccepted(playerId, quest.id());
        boolean isCompleted = questManager.isQuestCompleted(playerId, quest.id());
        boolean isClaimed = questManager.hasClaimedReward(playerId, quest.id());

        // Handle different click actions based on quest state
        if (isClaimed) {
            player.sendMessage("§a§l[New Moon] §aYou have already claimed this reward!");
            return;
        }

        if (isCompleted && !isClaimed) {
            // Quest completed - claim reward
            // First check if player has enough inventory space
            int requiredSlots = calculateRequiredSlots(player, quest.rewards());
            int emptySlots = countEmptySlots(player);

            if (emptySlots < requiredSlots) {
                player.sendMessage("§c§l[New Moon] §cYou need at least §e" + requiredSlots + " §cempty slots in your inventory!");
                player.sendMessage("§c§lFree up space and try again.");
                return;
            }

            if (questManager.claimReward(playerId, quest.id())) {
                // Give rewards to player
                for (ItemStack reward : quest.rewards()) {
                    player.getInventory().addItem(reward.clone());
                }

                player.sendMessage("§a§l[New Moon] §aQuest " + quest.id() + " rewards claimed!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // Refresh GUI
                player.closeInventory();
                open(player);
            } else {
                player.sendMessage("§c§l[New Moon] §cFailed to claim reward!");
            }
            return;
        }

        if (isUnlocked && !isAccepted && !isCompleted) {
            // Quest unlocked - accept it
            if (questManager.acceptQuest(playerId, quest.id())) {
                player.sendMessage("§a§l[New Moon] §aQuest " + quest.id() + " accepted!");
                player.sendMessage("§7" + quest.description());
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

                // Refresh GUI
                player.closeInventory();
                open(player);
            } else {
                player.sendMessage("§c§l[New Moon] §cFailed to accept quest!");
            }
            return;
        }

        // Quest in progress - show message
        if (isAccepted && !isCompleted) {
            int progress = questManager.getQuestProgress(playerId, quest.id());
            player.sendMessage("§e§l[New Moon] §eProgress: " + progress + "/" + quest.requiredKills());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Calculate how many inventory slots are required for a list of items.
     * Takes into account existing stacks of the same item.
     */
    private int calculateRequiredSlots(Player player, java.util.List<ItemStack> items) {
        int totalRequired = 0;
        for (ItemStack item : items) {
            totalRequired += getRequiredSlotsForItem(player, item);
        }
        return totalRequired;
    }

    /**
     * Calculate how many inventory slots are required for a single item.
     */
    private int getRequiredSlotsForItem(Player player, ItemStack item) {
        int amountToAdd = item.getAmount();
        int maxStackSize = item.getMaxStackSize();

        // Check existing stacks in inventory
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem != null && invItem.isSimilar(item)) {
                int spaceInStack = maxStackSize - invItem.getAmount();
                if (spaceInStack > 0) {
                    amountToAdd -= spaceInStack;
                    if (amountToAdd <= 0) {
                        return 0; // Can fit in existing stacks
                    }
                }
            }
        }

        // Calculate how many new slots are needed
        return (int) Math.ceil((double) amountToAdd / maxStackSize);
    }

    /**
     * Count empty slots in player's inventory.
     */
    private int countEmptySlots(Player player) {
        int emptySlots = 0;
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null || invItem.getType() == org.bukkit.Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots;
    }
}
