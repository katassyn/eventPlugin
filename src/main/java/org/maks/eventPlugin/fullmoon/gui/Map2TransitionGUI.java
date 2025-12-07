package org.maks.eventPlugin.fullmoon.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.fullmoon.map2.Map2Instance;

import java.util.*;

/**
 * GUI shown after killing Amarok, offering the player a choice
 * to proceed to the Blood Moon Arena (Map 2) or stay on Map 1.
 */
public class Map2TransitionGUI implements Listener {

    private final FullMoonManager fullMoonManager;
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    private static final int ACCEPT_SLOT = 11;
    private static final int DECLINE_SLOT = 15;

    public Map2TransitionGUI(FullMoonManager fullMoonManager) {
        this.fullMoonManager = fullMoonManager;
    }

    /**
     * Open the transition GUI for a player.
     */
    public void open(Player player) {
        if (!fullMoonManager.isEventActive()) {
            return;
        }

        if (!fullMoonManager.getQuestManager().hasUnlockedMap2(player.getUniqueId())) {
            player.sendMessage("§c§l[Full Moon] §cYou haven't unlocked the Blood Moon Arena yet!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§8§lEnter Blood Moon Arena?");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // Check if player is in hard mode
        boolean isHard = fullMoonManager.isHardMode(player.getUniqueId());
        int entryCost = 0;
        boolean canAfford = true;

        if (isHard) {
            // Get hard mode entry cost from config
            entryCost = fullMoonManager.getConfig()
                    .getSection("full_moon.map2_entry_cost")
                    .getInt("hard", 30);
            canAfford = PouchHelper.hasEnough(player, "ips", entryCost);
        }

        // Accept button
        ItemStack accept = new ItemStack(canAfford ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE);
        ItemMeta acceptMeta = accept.getItemMeta();
        acceptMeta.setDisplayName(canAfford ? "§a§lYES - Enter Arena" : "§6§lYES - Enter Arena");
        List<String> acceptLore = new ArrayList<>();
        acceptLore.add("");
        acceptLore.add("§7Proceed to the §cBlood Moon Arena");
        acceptLore.add("§7A §csolo instance §7will be created");
        acceptLore.add("§7just for you!");
        acceptLore.add("");
        acceptLore.add("§7Face powerful enemies and");
        acceptLore.add("§7defeat §cSanguis the Blood Mage§7!");
        acceptLore.add("");

        if (isHard && entryCost > 0) {
            acceptLore.add("§7Entry Cost: §6" + entryCost + " IPS");
            if (!canAfford) {
                int playerIPS = PouchHelper.getTotalItemQuantity(player, "ips");
                acceptLore.add("§cYou have: §6" + playerIPS + " IPS");
                acceptLore.add("§cNeed: §6" + (entryCost - playerIPS) + " more IPS");
            }
            acceptLore.add("");
        }

        if (canAfford) {
            acceptLore.add("§a§lCLICK TO ENTER");
        } else {
            acceptLore.add("§c§lNOT ENOUGH IPS");
        }

        acceptMeta.setLore(acceptLore);
        accept.setItemMeta(acceptMeta);
        inv.setItem(ACCEPT_SLOT, accept);

        // Decline button
        ItemStack decline = new ItemStack(Material.RED_CONCRETE);
        ItemMeta declineMeta = decline.getItemMeta();
        declineMeta.setDisplayName("§c§lNO - Stay Here");
        List<String> declineLore = new ArrayList<>();
        declineLore.add("");
        declineLore.add("§7Stay on Map 1 and continue");
        declineLore.add("§7hunting werewolves.");
        declineLore.add("");
        declineLore.add("§7You can challenge §6Amarok");
        declineLore.add("§7again to get this option later!");
        declineLore.add("");
        declineLore.add("§c§lCLICK TO DECLINE");
        declineLore.add("");
        declineMeta.setLore(declineLore);
        decline.setItemMeta(declineMeta);
        inv.setItem(DECLINE_SLOT, decline);

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

        if (slot == ACCEPT_SLOT) {
            // Check if player can afford entry (for hard mode)
            boolean isHard = fullMoonManager.isHardMode(player.getUniqueId());
            int entryCost = 0;

            if (isHard) {
                entryCost = fullMoonManager.getConfig()
                        .getSection("full_moon.map2_entry_cost")
                        .getInt("hard", 30);

                if (!PouchHelper.hasEnough(player, "ips", entryCost)) {
                    player.sendMessage("§c§l[Full Moon] §cYou need §6" + entryCost + " IPS §cto enter Hard mode arena!");
                    player.closeInventory();
                    return;
                }

                // Consume IPS
                if (!PouchHelper.consumeItem(player, "ips", entryCost)) {
                    player.sendMessage("§c§l[Full Moon] §cFailed to consume IPS!");
                    player.closeInventory();
                    return;
                }

                // Silent - no consumption message
            }

            player.closeInventory();
            enterBloodMoonArena(player, entryCost); // Przekaż entryCost do zwrotu w razie błędu
        } else if (slot == DECLINE_SLOT) {
            player.closeInventory();
            // Silent - no decline message
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Create a Map 2 instance and teleport the player.
     */
    private void enterBloodMoonArena(Player player, int entryCost) {
        // Silent - no loading message
        boolean isHard = fullMoonManager.isHardMode(player.getUniqueId());

        // Debug logging
        String difficulty = fullMoonManager.getPlayerDifficulty(player.getUniqueId());
        Bukkit.getLogger().info("[Full Moon] Player " + player.getName() + " entering Map2 with difficulty: " + difficulty + " (isHard=" + isHard + ")");

        // Run instance creation asynchronously to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    Map2Instance instance = fullMoonManager.getMap2InstanceManager().createInstance(player, isHard);

                    if (instance == null) {
                        player.sendMessage("§c§l[Full Moon] §cFailed to create arena instance! (Perhaps no free slots)");
                        // Zwróć IPS jeśli wystąpił błąd
                        if (isHard && entryCost > 0) {
                            PouchHelper.addItem(player, "ips", entryCost);
                            player.sendMessage("§a§l[Full Moon] §aYour " + entryCost + " IPS have been refunded.");
                        }
                        return;
                    }

                    // Teleport and initialize on main thread
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("EventPlugin"),
                            () -> {
                                // --- POCZĄTEK POPRAWKI (Teleport gracza) ---
                                // Get player spawn location (gold block) by scanning
                                Location spawnLoc = fullMoonManager.getMap2BossSequenceManager().getPlayerSpawn(instance);

                                if (spawnLoc == null) {
                                    player.sendMessage("§c§l[Full Moon] §cCRITICAL ERROR: No player spawn block found in schematic!");
                                    // Wyczyść instancję i zwróć koszty
                                    fullMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());
                                    if (isHard && entryCost > 0) {
                                        PouchHelper.addItem(player, "ips", entryCost);
                                        player.sendMessage("§a§l[Full Moon] §aYour " + entryCost + " IPS have been refunded.");
                                    }
                                    return;
                                }

                                spawnLoc.add(0.5, 0.5, 0.5); // Center player on block
                                player.teleport(spawnLoc);
                                // --- KONIEC POPRAWKI ---

                                // Initialize boss sequence with 5 second delay (prevent instant death)
                                Bukkit.getScheduler().runTaskLater(
                                        Bukkit.getPluginManager().getPlugin("EventPlugin"),
                                        () -> {
                                            fullMoonManager.getMap2BossSequenceManager()
                                                    .initializeBossSequence(instance, player);
                                        },
                                        5 * 20L  // 5 seconds delay
                                );

                                // Silent - no title screen
                            }
                    );
                }
        );
    }
}