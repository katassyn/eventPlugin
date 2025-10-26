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
                int playerIPS = PouchHelper.getItemQuantity(player, "ips");
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
            if (isHard) {
                int entryCost = fullMoonManager.getConfig()
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

                player.sendMessage("§e§l[Full Moon] §7Consumed §6" + entryCost + " IPS");
            }

            player.closeInventory();
            enterBloodMoonArena(player);
        } else if (slot == DECLINE_SLOT) {
            player.closeInventory();
            player.sendMessage("§e§l[Full Moon] §eYou chose to stay. Good luck hunting!");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Create a Map 2 instance and teleport the player.
     */
    private void enterBloodMoonArena(Player player) {
        player.sendMessage("§c§l[Full Moon] §eCreating Blood Moon Arena instance...");

        // Run instance creation asynchronously to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                () -> {
                    Map2Instance instance = fullMoonManager.getMap2InstanceManager().createInstance(player);

                    if (instance == null) {
                        player.sendMessage("§c§l[Full Moon] §cFailed to create arena instance!");
                        return;
                    }

                    // Teleport and initialize on main thread
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("EventPlugin"),
                            () -> {
                                // Get player spawn location (gold block)
                                var playerSpawnSection = fullMoonManager.getConfig()
                                        .getSection("full_moon.coordinates.map2.player_spawn");

                                if (playerSpawnSection != null) {
                                    int relX = playerSpawnSection.getInt("x");
                                    int relY = playerSpawnSection.getInt("y");
                                    int relZ = playerSpawnSection.getInt("z");

                                    Location spawnLoc = instance.getRelativeLocation(relX, relY, relZ);
                                    spawnLoc.add(0.5, 0, 0.5); // Center player on block
                                    player.teleport(spawnLoc);
                                }

                                // Initialize boss sequence
                                boolean isHard = fullMoonManager.isHardMode(player.getUniqueId());
                                fullMoonManager.getMap2BossSequenceManager()
                                        .initializeBossSequence(instance, player, isHard);

                                player.sendTitle(
                                        "§4§lBLOOD MOON ARENA",
                                        "§cDefeat the Blood Mage Disciples!",
                                        10, 80, 20
                                );
                            }
                    );
                }
        );
    }
}
