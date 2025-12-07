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
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.winterevent.WinterQuest;
import org.maks.eventPlugin.winterevent.WinterQuestManager;

import java.util.*;

/**
 * Two-stage admin GUI for editing Winter Event quest rewards.
 * Stage 1: Select a quest to edit
 * Stage 2: Place/remove reward items
 */
public class AdminWinterQuestRewardEditorGUI implements Listener {

    private final WinterQuestManager questManager;
    private final JavaPlugin plugin;

    private final Map<UUID, Inventory> openGUIs = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static class Session {
        int selectedQuestId = -1;
        boolean isEditing = false;
    }

    public AdminWinterQuestRewardEditorGUI(WinterQuestManager questManager, JavaPlugin plugin) {
        this.questManager = questManager;
        this.plugin = plugin;
    }

    /**
     * Open Stage 1: Quest selection.
     */
    public void open(Player player) {
        if (!player.hasPermission("eventplugin.admin.winter_quest_rewards")) {
            player.sendMessage("§c§lYou don't have permission to use this!");
            return;
        }

        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.isEditing = false;

        Inventory inv = Bukkit.createInventory(null, 54, "§6§lWinter Quests - Select Quest");

        // Fill background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        // Add all 14 quests
        List<WinterQuest> allQuests = questManager.getAllQuests();
        for (int i = 0; i < allQuests.size() && i < 28; i++) { // Max 28 slots (2 rows)
            WinterQuest quest = allQuests.get(i);
            int slot = i < 7 ? (10 + i) : (28 + (i - 7)); // Rows 2 & 4

            inv.setItem(slot, createQuestSelectItem(quest));
        }

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    /**
     * Open Stage 2: Reward editing.
     */
    private void openRewardEditor(Player player, int questId) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        session.selectedQuestId = questId;
        session.isEditing = true;

        WinterQuest quest = questManager.getQuest(questId);
        if (quest == null) {
            player.sendMessage("§c§lQuest not found!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§6§lEdit Quest " + questId + " Rewards");

        // Add current rewards (slots 0-25)
        List<ItemStack> currentRewards = questManager.getQuestRewards(questId);
        for (int i = 0; i < currentRewards.size() && i < 26; i++) {
            inv.setItem(i, currentRewards.get(i).clone());
        }

        // Save button (slot 26)
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveBtn.getItemMeta();
        saveMeta.setDisplayName("§a§l✔ SAVE REWARDS");
        List<String> saveLore = new ArrayList<>();
        saveLore.add("");
        saveLore.add("§7Click to save all items");
        saveLore.add("§7in slots 0-25 as rewards");
        saveLore.add("");
        saveLore.add("§e§lCLICK TO SAVE");
        saveMeta.setLore(saveLore);
        saveBtn.setItemMeta(saveMeta);
        inv.setItem(26, saveBtn);

        // Info item (slot 49)
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lHow to Use");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§71. Place reward items in slots 0-25");
        infoLore.add("§72. Click the §a§lSAVE§7 button");
        infoLore.add("§73. Close to return to quest selection");
        infoLore.add("");
        infoLore.add("§cWarning: §7Saving clears old rewards!");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // Back button (slot 53)
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName("§c§l← BACK");
        backBtn.setItemMeta(backMeta);
        inv.setItem(53, backBtn);

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    /**
     * Create quest selection item.
     */
    private ItemStack createQuestSelectItem(WinterQuest quest) {
        Material mat = quest.chainType().equals("bear") ? Material.HONEYCOMB : Material.COAL;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String chainColor = quest.chainType().equals("bear") ? "§6" : "§5";
        meta.setDisplayName(chainColor + "§lQuest " + quest.id());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + quest.description());
        lore.add("");

        int rewardCount = questManager.getQuestRewards(quest.id()).size();
        lore.add("§7Current rewards: §e" + rewardCount + " items");
        lore.add("");
        lore.add("§e§lCLICK TO EDIT REWARDS");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory inv = openGUIs.get(playerId);

        if (inv == null || !event.getInventory().equals(inv)) {
            return;
        }

        Session session = sessions.get(playerId);
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        // Stage 1: Quest selection
        if (!session.isEditing) {
            event.setCancelled(true);

            int slot = event.getRawSlot();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                return;
            }

            // Find which quest was clicked
            List<WinterQuest> allQuests = questManager.getAllQuests();
            for (int i = 0; i < allQuests.size(); i++) {
                int questSlot = i < 7 ? (10 + i) : (28 + (i - 7));
                if (slot == questSlot) {
                    WinterQuest quest = allQuests.get(i);
                    openRewardEditor(player, quest.id());
                    return;
                }
            }
        }
        // Stage 2: Reward editing
        else {
            int slot = event.getRawSlot();

            // Save button
            if (slot == 26) {
                event.setCancelled(true);
                saveRewardsFromInventory(player, session, inv);
                return;
            }

            // Back button
            if (slot == 53) {
                event.setCancelled(true);
                open(player);
                return;
            }

            // Info button
            if (slot == 49) {
                event.setCancelled(true);
                return;
            }

            // Allow editing slots 0-25
            if (slot < 0 || slot > 25) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Save rewards from inventory slots 0-25.
     */
    private void saveRewardsFromInventory(Player player, Session session, Inventory inv) {
        if (session.selectedQuestId == -1) return;

        // Clear old rewards
        questManager.clearQuestRewards(session.selectedQuestId);

        // Add new rewards from slots 0-25
        int savedCount = 0;
        for (int i = 0; i < 26; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                questManager.addQuestReward(session.selectedQuestId, item.clone());
                savedCount++;
            }
        }

        player.sendMessage("§a§l[Winter Event] §aSaved " + savedCount + " rewards for Quest " + session.selectedQuestId + "!");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        // Refresh GUI
        Bukkit.getScheduler().runTaskLater(plugin, () -> openRewardEditor(player, session.selectedQuestId), 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openGUIs.remove(playerId);
        // Don't remove session - keep it for when they reopen
    }

    /**
     * Cleanup sessions (call this when plugin disables).
     */
    public void cleanup() {
        sessions.clear();
        openGUIs.clear();
    }
}
