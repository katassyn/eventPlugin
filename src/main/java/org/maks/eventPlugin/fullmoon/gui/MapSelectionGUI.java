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
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.fullmoon.FullMoonManager;

import java.util.*;

/**
 * GUI for selecting Normal or Hard difficulty for Full Moon event.
 */
public class MapSelectionGUI implements Listener {

    private final JavaPlugin plugin;
    private final FullMoonManager fullMoonManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    private static final int NORMAL_SLOT = 11;
    private static final int HARD_SLOT = 15;

    public MapSelectionGUI(JavaPlugin plugin, FullMoonManager fullMoonManager) {
        this.plugin = plugin;
        this.fullMoonManager = fullMoonManager;
    }

    /**
     * Open the map selection GUI for a player.
     */
    public void open(Player player) {
        if (!fullMoonManager.isEventActive()) {
            player.sendMessage("§c§l[Full Moon] §cThe Full Moon event is not currently active!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§8§lFull Moon - Select Difficulty");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // Normal mode button
        boolean meetsNormal = fullMoonManager.meetsNormalRequirements(player);
        ItemStack normalButton = new ItemStack(meetsNormal ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta normalMeta = normalButton.getItemMeta();
        normalMeta.setDisplayName("§e§lNormal Difficulty");

        List<String> normalLore = new ArrayList<>();
        normalLore.add("");
        normalLore.add("§7Requirements:");
        normalLore.add("§7  • Level 50+");
        normalLore.add("");
        normalLore.add("§7Rewards:");
        normalLore.add("§7  • 1x progress per mob");
        normalLore.add("§7  • 1x drakens");
        normalLore.add("§7  • [ N ] prefix items");
        normalLore.add("");

        if (meetsNormal) {
            normalLore.add("§a§lCLICK TO ENTER");
        } else {
            normalLore.add("§c§lREQUIREMENTS NOT MET");
            normalLore.add("§cYou need level 50+");
        }

        normalMeta.setLore(normalLore);
        normalButton.setItemMeta(normalMeta);
        inv.setItem(NORMAL_SLOT, normalButton);

        // Hard mode button
        boolean meetsHard = fullMoonManager.meetsHardRequirements(player);
        ItemStack hardButton = new ItemStack(meetsHard ? Material.RED_WOOL : Material.GRAY_WOOL);
        ItemMeta hardMeta = hardButton.getItemMeta();
        hardMeta.setDisplayName("§c§lHard Difficulty");

        List<String> hardLore = new ArrayList<>();
        hardLore.add("");
        hardLore.add("§7Requirements:");
        hardLore.add("§7  • Level 75+");
        hardLore.add("§7  • 15 IPS (Ingredient Pouch)");
        hardLore.add("");
        hardLore.add("§7Rewards:");
        hardLore.add("§c  • 2x progress per mob");
        hardLore.add("§c  • 2x drakens");
        hardLore.add("§c  • [ H ] prefix items");
        hardLore.add("§c  • Mythic rarity items");
        hardLore.add("");

        if (meetsHard) {
            hardLore.add("§a§lCLICK TO ENTER");
        } else {
            hardLore.add("§c§lREQUIREMENTS NOT MET");
            if (player.getLevel() < 75) {
                hardLore.add("§cYou need level 75+");
            }
            // TODO: Add IPS check message when implemented
        }

        hardMeta.setLore(hardLore);
        hardButton.setItemMeta(hardMeta);
        inv.setItem(HARD_SLOT, hardButton);

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = openGUIs.get(player.getUniqueId());

        if (inv == null || !event.getInventory().equals(inv)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == NORMAL_SLOT) {
            if (fullMoonManager.meetsNormalRequirements(player)) {
                player.closeInventory();
                warpPlayer(player, "normal");
            } else {
                player.sendMessage("§c§l[Full Moon] §cYou don't meet the requirements for Normal mode!");
                player.sendMessage("§cRequired: Level 50+");
            }
        } else if (slot == HARD_SLOT) {
            if (fullMoonManager.meetsHardRequirements(player)) {
                player.closeInventory();
                warpPlayer(player, "hard");
            } else {
                player.sendMessage("§c§l[Full Moon] §cYou don't meet the requirements for Hard mode!");
                player.sendMessage("§cRequired: Level 75+ and 15 IPS");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Warp player to the appropriate map using Essentials.
     */
    private void warpPlayer(Player player, String difficulty) {
        String warpName = fullMoonManager.getConfig()
                .getSection("full_moon.warps")
                .getString(difficulty, "fullmoon_" + difficulty);

        // Set player difficulty before warping
        fullMoonManager.setPlayerDifficulty(player.getUniqueId(), difficulty);

        // Execute warp command as console (player may not have permission)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warp " + warpName + " " + player.getName());

        player.sendMessage("§a§l[Full Moon] §aWarping to " + (difficulty.equalsIgnoreCase("hard") ? "§cHard" : "§eNormal") + " §amode...");
        player.sendTitle(
                "§6§lFull Moon",
                (difficulty.equalsIgnoreCase("hard") ? "§c§lHARD MODE" : "§e§lNORMAL MODE"),
                10, 60, 20
        );
    }
}
