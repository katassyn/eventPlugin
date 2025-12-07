package org.maks.eventPlugin.winterevent.wintercave.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.winterevent.wintercave.WinterCaveManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for Winter Cave entry confirmation.
 * 27-slot inventory with confirm/cancel buttons.
 */
public class WinterCaveGUI implements Listener {
    private final WinterCaveManager caveManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    public WinterCaveGUI(WinterCaveManager caveManager) {
        this.caveManager = caveManager;
    }

    public void open(Player player) {
        // Check if player can enter
        if (!caveManager.canEnterToday(player.getUniqueId())) {
            player.sendMessage("§c§l[Winter Event] §cYou already claimed today's reward!");
            return;
        }

        if (caveManager.isInstanceLocked()) {
            player.sendMessage("§c§l[Winter Event] §cSomeone is already in the Winter Cave!");
            return;
        }

        // Create inventory
        Inventory inv = Bukkit.createInventory(null, 27, "§8§lWinter Cave Entry");

        // Fill with black stained glass pane
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Confirm button (green wool)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a§lConfirm Entry");
        confirmMeta.setLore(Arrays.asList(
                "§7Click to enter the Winter Cave",
                "§7",
                "§e⚠ You can only enter once per day",
                "§e⚠ Time limit: 5 minutes"
        ));
        confirm.setItemMeta(confirmMeta);
        inv.setItem(11, confirm);

        // Cancel button (red wool)
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c§lCancel");
        cancelMeta.setLore(Arrays.asList(
                "§7Click to close"
        ));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInv = openGUIs.get(player.getUniqueId());

        if (clickedInv == null || !event.getInventory().equals(clickedInv)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // Confirm (green wool)
        if (clicked.getType() == Material.LIME_WOOL) {
            player.closeInventory();
            openGUIs.remove(player.getUniqueId());
            caveManager.startInstance(player);
        }
        // Cancel (red wool)
        else if (clicked.getType() == Material.RED_WOOL) {
            player.closeInventory();
            openGUIs.remove(player.getUniqueId());
            player.sendMessage("§f§l[Winter Event] §cEntry cancelled.");
        }
    }
}
