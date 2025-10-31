package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.eventsystem.Reward;

import java.util.*;

/**
 * GUI displaying all possible drops/rewards from an event.
 * Shows rewards sorted by required progress.
 */
public class EventDropPreviewGUI implements InventoryHolder {
    private final EventManager eventManager;
    private final Player player;
    private final Inventory inventory;

    public EventDropPreviewGUI(EventManager eventManager, Player player) {
        this.eventManager = eventManager;
        this.player = player;

        String title = "§8§lRewards - §6" + eventManager.getName();
        this.inventory = Bukkit.createInventory(this, 54, title);
        setupInventory();
    }

    private void setupInventory() {
        // Fill with background
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, bg);
        }

        // Event info at top
        ItemStack info = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§l" + eventManager.getName());

        List<String> lore = new ArrayList<>();
        lore.add("");

        String[] descLines = eventManager.getDescription().split("\\n");
        for (String line : descLines) {
            lore.add("§7" + line);
        }

        lore.add("");
        lore.add("§7Maximum Progress: §e" + eventManager.getMaxProgress());
        lore.add("§7Total Rewards: §e" + eventManager.getRewards().size());
        lore.add("");
        lore.add("§fThese are the possible rewards");
        lore.add("§ffrom this event");

        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        // Display rewards sorted by required progress
        List<Reward> rewards = new ArrayList<>(eventManager.getRewards());
        rewards.sort(Comparator.comparingInt(Reward::requiredProgress));

        // Available slots for rewards (avoid top row and edges)
        int[] rewardSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int slotIndex = 0;
        for (Reward reward : rewards) {
            if (slotIndex >= rewardSlots.length) break;

            int slot = rewardSlots[slotIndex++];
            ItemStack displayItem = createRewardDisplayItem(reward);
            inventory.setItem(slot, displayItem);
        }

        // Back/Close button
        ItemStack close = createItem(Material.BARRIER, "§cClose");
        inventory.setItem(49, close);
    }

    /**
     * Creates a display version of a reward with additional information.
     */
    private ItemStack createRewardDisplayItem(Reward reward) {
        ItemStack item = reward.item().clone();
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add separator if there's existing lore
        if (!lore.isEmpty()) {
            lore.add("");
        }

        // Add reward information
        lore.add("§8§m━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7Reward Information:");
        lore.add("§8▪ §7Required Progress: §e" + reward.requiredProgress());

        // Add player's current progress
        int playerProgress = eventManager.getProgress(player);
        if (playerProgress >= reward.requiredProgress()) {
            lore.add("§8▪ §aYou've unlocked this reward!");
        } else {
            int needed = reward.requiredProgress() - playerProgress;
            lore.add("§8▪ §cYou need §e" + needed + " §cmore progress");
        }

        lore.add("§8§m━━━━━━━━━━━━━━━━━━━━━");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Handles click events in this GUI.
     * Returns true if the click was handled (for use in listener).
     */
    public boolean handleClick(int slot) {
        // Close button
        if (slot == 49) {
            player.closeInventory();
            return true;
        }

        // Block all other interactions
        return false;
    }
}
