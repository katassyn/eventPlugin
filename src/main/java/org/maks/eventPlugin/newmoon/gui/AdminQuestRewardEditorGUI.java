package org.maks.eventPlugin.newmoon.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.newmoon.NewMoonQuest;
import org.maks.eventPlugin.newmoon.NewMoonQuestManager;

import java.util.*;

/**
 * Admin GUI for managing New Moon quest rewards.
 * Supports all 10 quests (5 White Chain + 5 Black Chain).
 */
public class AdminQuestRewardEditorGUI implements Listener {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final JavaPlugin plugin;
    private final NewMoonQuestManager questManager;

    // Custom InventoryHolder for Quest Selection GUI
    public class QuestSelectionHolder implements InventoryHolder {
        private final Session session;

        public QuestSelectionHolder(Session session) {
            this.session = session;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public Inventory getInventory() {
            return session.inventory;
        }
    }

    // Custom InventoryHolder for Reward Items GUI
    public class RewardItemsHolder implements InventoryHolder {
        private final Session session;

        public RewardItemsHolder(Session session) {
            this.session = session;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public Inventory getInventory() {
            return session.inventory;
        }
    }

    private static class Session {
        enum Stage { QUEST_SELECTION, ADD_ITEMS }
        Stage stage = Stage.QUEST_SELECTION;
        Inventory inventory;
        List<ItemStack> rewards = new ArrayList<>();
        Integer selectedQuestId;
    }

    public AdminQuestRewardEditorGUI(JavaPlugin plugin, NewMoonQuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    /**
     * Open the quest selection GUI.
     * New Moon has 10 quests total.
     */
    public void open(Player player) {
        Session session = new Session();
        session.stage = Session.Stage.QUEST_SELECTION;

        QuestSelectionHolder holder = new QuestSelectionHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 36, "§6§lSelect New Moon Quest");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, bg);
        }

        // Get all quests (should be 10 total)
        List<NewMoonQuest> quests = questManager.getAllQuests();

        // Display quests in two rows:
        // Row 1 (White Chain): Quests 1-5 in slots 10-14
        // Row 2 (Black Chain): Quests 6-10 in slots 19-23
        int[] whiteSlots = {10, 11, 12, 13, 14};
        int[] blackSlots = {19, 20, 21, 22, 23};

        for (NewMoonQuest quest : quests) {
            int questId = quest.id();
            int slot;

            // Determine slot based on quest chain
            if (quest.chainType().equals("white")) {
                int index = quest.orderIndex();
                if (index >= 0 && index < whiteSlots.length) {
                    slot = whiteSlots[index];
                } else {
                    continue;
                }
            } else if (quest.chainType().equals("black")) {
                int index = quest.orderIndex();
                if (index >= 0 && index < blackSlots.length) {
                    slot = blackSlots[index];
                } else {
                    continue;
                }
            } else {
                continue;
            }

            // Create quest item
            ItemStack questItem = new ItemStack(
                quest.chainType().equals("white") ? Material.WHITE_WOOL : Material.BLACK_WOOL
            );
            ItemMeta meta = questItem.getItemMeta();

            String chainColor = quest.chainType().equals("white") ? "§f" : "§8";
            meta.setDisplayName(chainColor + "§lQuest " + questId);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7" + quest.description());
            lore.add("");
            lore.add("§7Chain: " + chainColor + (quest.chainType().equals("white") ? "White Lord" : "Black Lord"));
            lore.add("");

            // Show existing rewards count
            List<ItemStack> existingRewards = questManager.getQuestRewards(questId);
            lore.add("§7Current rewards: §e" + existingRewards.size());
            lore.add("");
            lore.add("§e§lCLICK to edit rewards!");

            meta.setLore(lore);
            questItem.setItemMeta(meta);
            inv.setItem(slot, questItem);
        }

        session.inventory = inv;
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    /**
     * Open the reward items stage for a selected quest.
     */
    private void openRewardItemsStage(Player player, Session session) {
        session.stage = Session.Stage.ADD_ITEMS;

        RewardItemsHolder holder = new RewardItemsHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 27, "§6Quest " + session.selectedQuestId + " Rewards");

        // Load existing rewards from database
        List<ItemStack> existingRewards = questManager.getQuestRewards(session.selectedQuestId);
        session.rewards.clear();
        for (int i = 0; i < existingRewards.size() && i < 26; i++) {
            ItemStack reward = existingRewards.get(i).clone();
            session.rewards.add(reward);
            inv.setItem(i, reward);
        }

        if (!existingRewards.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "Loaded " + existingRewards.size() + " existing rewards.");
        }

        // Add save button
        ItemStack save = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = save.getItemMeta();
        meta.setDisplayName("§a§lSave Rewards");
        meta.setLore(List.of("", "§7Click to save all rewards", "§7to the database"));
        save.setItemMeta(meta);
        inv.setItem(26, save);

        session.inventory = inv;
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        InventoryHolder holder = event.getInventory().getHolder();

        // Check if this is one of our custom inventories
        if (!(holder instanceof QuestSelectionHolder) && !(holder instanceof RewardItemsHolder)) {
            return;
        }

        int slot = event.getRawSlot();

        // QUEST SELECTION STAGE
        if (session.stage == Session.Stage.QUEST_SELECTION) {
            event.setCancelled(true);

            // Check if clicked on a quest icon
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                return;
            }

            // Extract quest ID from item name
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String displayName = clicked.getItemMeta().getDisplayName();
                // Extract ID from "§f§lQuest X" or "§8§lQuest X"
                String[] parts = displayName.split(" ");
                if (parts.length >= 2) {
                    try {
                        int questId = Integer.parseInt(ChatColor.stripColor(parts[1]));
                        session.selectedQuestId = questId;
                        player.sendMessage(ChatColor.GREEN + "Selected Quest " + questId);
                        openRewardItemsStage(player, session);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        // ADD ITEMS STAGE
        else if (session.stage == Session.Stage.ADD_ITEMS) {
            if (slot == 26) {
                // Save button clicked
                event.setCancelled(true);
                saveRewardsFromInventory(player, session);
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "§a§lQuest rewards saved!");
                return;
            }

            // Top inventory (reward slots 0-25) - allow normal item placement
            if (slot >= 0 && slot < 26) {
                // Don't cancel - allow normal inventory interaction
                // Items can be placed/removed freely
            }
        }
    }

    /**
     * Save rewards to database by reading directly from inventory.
     */
    private void saveRewardsFromInventory(Player player, Session session) {
        if (session.selectedQuestId == null) {
            player.sendMessage(ChatColor.RED + "No quest selected!");
            return;
        }

        // Clear old rewards first
        questManager.clearQuestRewards(session.selectedQuestId);

        // Read all items from slots 0-25 in the inventory
        Inventory inv = session.inventory;
        int savedCount = 0;

        for (int i = 0; i < 26; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                questManager.addQuestReward(session.selectedQuestId, item.clone());
                savedCount++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Saved " + savedCount + " rewards for Quest " + session.selectedQuestId);
        plugin.getLogger().info("[New Moon AdminQuestRewardGUI] Saved " + savedCount + " rewards for quest " + session.selectedQuestId);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof QuestSelectionHolder || holder instanceof RewardItemsHolder) {
            // Don't remove session immediately - wait a bit in case they're switching stages
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Session s = sessions.get(player.getUniqueId());
                if (s != null && s.inventory == event.getInventory()) {
                    sessions.remove(player.getUniqueId());
                }
            }, 1L);
        }
    }
}
