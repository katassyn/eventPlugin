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
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.newmoon.NewMoonManager;

import java.util.*;

/**
 * GUI for selecting Map 1 (Black Bog) difficulty.
 * Players can choose between Normal and Hard mode.
 *
 * Requirements:
 * - Normal: Level 50
 * - Hard: Level 75 + 15 IPS (consumed on entry)
 */
public class Map1SelectionGUI implements Listener {

    private final NewMoonManager newMoonManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    public Map1SelectionGUI(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    /**
     * Open the Map 1 selection GUI for a player.
     */
    public void open(Player player) {
        if (!newMoonManager.isEventActive()) {
            player.sendMessage("§c§l[New Moon] §cThe New Moon event is not currently active!");
            return;
        }

        UUID playerId = player.getUniqueId();

        Inventory inv = Bukkit.createInventory(null, 27, "§8§lBlack Bog - Select Difficulty");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // Normal mode option
        boolean meetsNormalReq = newMoonManager.meetsNormalRequirements(player);
        ItemStack normalItem = new ItemStack(meetsNormalReq ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta normalMeta = normalItem.getItemMeta();
        normalMeta.setDisplayName("§a§l[NORMAL] Black Bog");

        List<String> normalLore = new ArrayList<>();
        normalLore.add("");
        normalLore.add("§7Difficulty: §aNormal");
        normalLore.add("");
        normalLore.add("§7Requirements:");
        normalLore.add("§7  • Level 50+");
        normalLore.add("");
        normalLore.add("§7Rewards:");
        normalLore.add("§7  • 1x progress per mob");
        normalLore.add("§7  • 1x drakens");
        normalLore.add("§7  • [ N ] prefix items");
        normalLore.add("");

        if (meetsNormalReq) {
            normalLore.add("§a§lCLICK TO ENTER");
        } else {
            normalLore.add("§c✘ Requirements not met");
            normalLore.add("§7Your level: §e" + player.getLevel() + "§7/§e50");
        }

        normalMeta.setLore(normalLore);
        normalItem.setItemMeta(normalMeta);
        inv.setItem(11, normalItem);

        // Hard mode option
        boolean meetsHardReq = newMoonManager.meetsHardRequirements(player);
        int ipsAmount = PouchHelper.isAvailable() ? PouchHelper.getItemQuantity(player, "ips") : 0;

        ItemStack hardItem = new ItemStack(meetsHardReq ? Material.RED_CONCRETE : Material.GRAY_CONCRETE);
        ItemMeta hardMeta = hardItem.getItemMeta();
        hardMeta.setDisplayName("§c§l[HARD] Black Bog");

        List<String> hardLore = new ArrayList<>();
        hardLore.add("");
        hardLore.add("§7Difficulty: §cHard");
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

        if (meetsHardReq) {
            hardLore.add("§a§lCLICK TO ENTER");
            hardLore.add("§7(Will consume 15 IPS)");
        } else {
            hardLore.add("§c✘ Requirements not met");
            hardLore.add("§7Your level: §e" + player.getLevel() + "§7/§e75");
            hardLore.add("§7Your IPS: §e" + ipsAmount + "§7/§e15");
        }

        hardMeta.setLore(hardLore);
        hardItem.setItemMeta(hardMeta);
        inv.setItem(15, hardItem);

        // Info item
        ItemStack info = new ItemStack(Material.MAP);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lBlack Bog");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7A dark and mysterious swamp where");
        infoLore.add("§7the New Moon's influence is strongest.");
        infoLore.add("");
        infoLore.add("§7Complete quests and gather Fairy Wood");
        infoLore.add("§7to unlock portals to the Lord's realms!");
        infoLore.add("");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        openGUIs.put(playerId, inv);
        player.openInventory(inv);
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

        // Normal mode (slot 11)
        if (slot == 11) {
            if (!newMoonManager.meetsNormalRequirements(player)) {
                player.sendMessage("§c§l[New Moon] §cYou don't meet the requirements for Normal mode!");
                player.sendMessage("§7Required: §eLevel 50");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Set difficulty and teleport
            newMoonManager.setPlayerDifficulty(playerId, "normal");
            teleportToMap1(player, "normal");
            player.closeInventory();

        } else if (slot == 15) {
            // Hard mode (slot 15)
            if (!newMoonManager.meetsHardRequirements(player)) {
                player.sendMessage("§c§l[New Moon] §cYou don't meet the requirements for Hard mode!");
                player.sendMessage("§7Required: §eLevel 75 §7and §e15 IPS");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Consume 15 IPS
            if (!PouchHelper.consumeItem(player, "ips", 15)) {
                player.sendMessage("§c§l[New Moon] §cFailed to consume IPS!");
                player.closeInventory();
                return;
            }

            // Set difficulty and teleport
            newMoonManager.setPlayerDifficulty(playerId, "hard");
            teleportToMap1(player, "hard");
            player.closeInventory();
        }
    }

    /**
     * Teleport player to Map 1 (Black Bog) using Essentials warp.
     */
    private void teleportToMap1(Player player, String difficulty) {
        String warpName = newMoonManager.getConfig()
            .getSection("new_moon.warps")
            .getString(difficulty, "blackbog_" + difficulty);

        player.sendMessage("§a§l[New Moon] §aTeleporting to Black Bog §7[" +
            (difficulty.equals("hard") ? "§c§lHARD" : "§a§lNORMAL") + "§7]§a...");

        // Execute warp command as console (player may not have permission)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warp " + warpName + " " + player.getName());

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }
}
