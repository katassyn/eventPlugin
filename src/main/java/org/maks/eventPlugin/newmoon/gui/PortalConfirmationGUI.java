package org.maks.eventPlugin.newmoon.gui;

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
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.newmoon.NewMoonManager;

import java.util.*;

/**
 * GUI for confirming portal entry to White/Black Realms.
 * Shows costs and allows player to confirm or cancel entry.
 *
 * Entry costs:
 * - Normal: 1x New Moon Parchment
 * - Hard: 1x New Moon Parchment + 30 IPS
 */
public class PortalConfirmationGUI implements Listener {

    private final NewMoonManager newMoonManager;
    private org.maks.eventPlugin.newmoon.listener.PortalListener portalListener;
    private final Map<UUID, PortalEntry> pendingEntries = new HashMap<>();
    private final Map<UUID, Inventory> openGUIs = new HashMap<>();
    private final Set<UUID> buttonClicked = new HashSet<>(); // Track if player clicked a button

    public PortalConfirmationGUI(NewMoonManager newMoonManager, org.maks.eventPlugin.newmoon.listener.PortalListener portalListener) {
        this.newMoonManager = newMoonManager;
        this.portalListener = portalListener;
    }

    /**
     * Check if player has this GUI open.
     */
    public boolean hasOpenGUI(UUID playerId) {
        return openGUIs.containsKey(playerId);
    }


    /**
     * Open portal confirmation GUI.
     *
     * @param player The player
     * @param realm "white" or "black"
     * @param difficulty "normal" or "hard"
     * @param portalLocation The location of the portal (for teleport back if needed)
     */
    public void open(Player player, String realm, String difficulty, Location portalLocation) {
        UUID playerId = player.getUniqueId();

        // Store pending entry
        pendingEntries.put(playerId, new PortalEntry(realm, difficulty, portalLocation));

        Inventory inv = Bukkit.createInventory(null, 27, "§8§lEnter " +
                (realm.equals("white") ? "§f§lWhite" : "§5§lBlack") + " Realm§8§l?");

        // Fill with background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // Create info item
        ItemStack info = new ItemStack(Material.ENDER_EYE);
        ItemMeta infoMeta = info.getItemMeta();
        String realmColor = realm.equals("white") ? "§f" : "§5";
        String difficultyColor = difficulty.equals("hard") ? "§c" : "§a";

        infoMeta.setDisplayName(realmColor + "§l" + realm.toUpperCase() + " REALM " +
                difficultyColor + "§l[" + difficulty.toUpperCase() + "]");

        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7You are about to enter:");
        infoLore.add(realmColor + "§lThe " + (realm.equals("white") ? "White" : "Black") + " Realm");
        infoLore.add("§7Difficulty: " + difficultyColor + difficulty.toUpperCase());
        infoLore.add("");
        infoLore.add("§7Entry Cost:");
        infoLore.add("  §e• 1x New Moon Parchment");

        if (difficulty.equals("hard")) {
            infoLore.add("  §c• 30x Fragment of Infernal Passage");
        }

        infoLore.add("");

        // Check if player has enough
        boolean hasEnough = checkRequirements(player, realm, difficulty);
        if (hasEnough) {
            infoLore.add("§a✔ You have the required items!");
        } else {
            infoLore.add("§c✘ You don't have enough items!");
        }

        infoLore.add("");
        infoLore.add("§7Click §a§lCONFIRM §7to enter");
        infoLore.add("§7or §c§lCANCEL §7to go back");

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a§l✔ CONFIRM ENTRY");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add("§7Click to enter the realm!");
        if (!hasEnough) {
            confirmLore.add("");
            confirmLore.add("§c✘ Not enough items!");
        }
        confirmMeta.setLore(confirmLore);
        confirm.setItemMeta(confirmMeta);
        inv.setItem(11, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c§l✘ CANCEL");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add("§7Click to go back");
        cancelMeta.setLore(cancelLore);
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        openGUIs.put(playerId, inv);
        player.openInventory(inv);
    }

    /**
     * Check if player meets requirements for portal entry.
     */
    private boolean checkRequirements(Player player, String realm, String difficulty) {
        if (realm.equals("white")) {
            if (difficulty.equals("normal")) {
                return newMoonManager.canEnterWhiteRealmNormal(player);
            } else {
                return newMoonManager.canEnterWhiteRealmHard(player);
            }
        } else {
            if (difficulty.equals("normal")) {
                return newMoonManager.canEnterBlackRealmNormal(player);
            } else {
                return newMoonManager.canEnterBlackRealmHard(player);
            }
        }
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
        PortalEntry entry = pendingEntries.get(playerId);

        if (entry == null) {
            player.closeInventory();
            return;
        }

        // Confirm button (slot 11)
        if (slot == 11) {
            org.bukkit.Bukkit.getLogger().info("[Portal GUI DEBUG] " + player.getName() + " clicked CONFIRM button");

            // Mark that button was clicked
            buttonClicked.add(playerId);

            // Check requirements again
            if (!checkRequirements(player, entry.realm, entry.difficulty)) {
                org.bukkit.Bukkit.getLogger().info("[Portal GUI DEBUG] " + player.getName() + " does not meet requirements - removing from buttonClicked and closing");
                // Remove from buttonClicked so onClose() treats this as ESC (sets cooldown)
                buttonClicked.remove(playerId);
                player.sendMessage("§c§l[New Moon] §cYou don't have the required items!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.closeInventory();
                return;
            }

            // Consume items
            if (!PouchHelper.consumeItem(player, "new_moon_scrol", 1)) {
                player.sendMessage("§c§l[New Moon] §cFailed to consume New Moon Parchment!");
                player.closeInventory();
                return;
            }

            if (entry.difficulty.equals("hard")) {
                if (!PouchHelper.consumeItem(player, "ips", 30)) {
                    player.sendMessage("§c§l[New Moon] §cFailed to consume IPS!");
                    // Refund parchment
                    PouchHelper.addItem(player, "new_moon_scrol", 1);
                    player.closeInventory();
                    return;
                }
            }

            // Set player realm and difficulty
            newMoonManager.setPlayerRealm(playerId, entry.realm);
            newMoonManager.setPlayerDifficulty(playerId, entry.difficulty);

            player.sendMessage("§a§l[New Moon] §aEntering the " +
                    (entry.realm.equals("white") ? "§f§lWhite" : "§5§lBlack") + " Realm§a...");

            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            // Clear GUI cooldown since player is entering (will be teleported away)
            if (portalListener != null) {
                portalListener.clearGUICooldown(playerId);
            }

            player.closeInventory();

            // Create Map2 instance asynchronously to avoid blocking
            boolean isHard = entry.difficulty.equals("hard");
            String realmType = entry.realm; // "white" or "black"

            Bukkit.getScheduler().runTaskAsynchronously(
                    Bukkit.getPluginManager().getPlugin("EventPlugin"),
                    () -> {
                        var instance = newMoonManager.getMap2InstanceManager().createInstance(player, isHard, realmType);

                        if (instance == null) {
                            player.sendMessage("§c§l[New Moon] §cFailed to create realm instance! (Perhaps no free slots)");
                            // Refund items
                            PouchHelper.addItem(player, "new_moon_parchment", 1);
                            if (isHard) {
                                PouchHelper.addItem(player, "ips", 30);
                            }
                            return;
                        }

                        // Teleport player to instance on main thread
                        Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("EventPlugin"),
                                () -> {
                                    Location spawnLoc = instance.getPlayerSpawnLocation();

                                    if (spawnLoc == null) {
                                        player.sendMessage("§c§l[New Moon] §cCRITICAL ERROR: No player spawn block found in schematic!");
                                        // Cleanup instance and refund
                                        newMoonManager.getMap2InstanceManager().removeInstance(player.getUniqueId());
                                        PouchHelper.addItem(player, "new_moon_parchment", 1);
                                        if (isHard) {
                                            PouchHelper.addItem(player, "ips", 30);
                                        }
                                        return;
                                    }

                                    player.teleport(spawnLoc);
                                    player.sendMessage("§a§l[New Moon] §aWelcome to the " +
                                            (realmType.equals("white") ? "§f§lWhite" : "§5§lBlack") + " Realm§a!");
                                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                                    // Spawn mobs and mini-bosses from markers
                                    newMoonManager.getMap2MobSpawner().initializeMobSpawning(instance, player);

                                    // Create holograms for lord respawn blocks
                                    newMoonManager.getHologramManager().createHolograms(instance);
                                }
                        );
                    }
            );

        } else if (slot == 15) {
            org.bukkit.Bukkit.getLogger().info("[Portal GUI DEBUG] " + player.getName() + " clicked CANCEL button");

            // Mark that button was clicked
            buttonClicked.add(playerId);

            // Cancel button - set decline cooldown (5s)
            if (portalListener != null) {
                portalListener.setDeclineCooldown(playerId);
            }
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Player player = (Player) event.getPlayer();

        // Clean up GUI tracking
        Inventory closedInv = openGUIs.remove(playerId);
        PortalEntry entry = pendingEntries.remove(playerId);

        // Check if this was our GUI
        if (closedInv == null || entry == null) {
            return; // Not our GUI
        }

        org.bukkit.Bukkit.getLogger().info("[Portal GUI DEBUG] " + player.getName() + " GUI closed");

        // Check if this was our GUI and if player clicked a button
        boolean wasButtonClick = buttonClicked.remove(playerId);

        // Notify PortalListener about GUI close
        if (portalListener != null) {
            // If GUI was closed by ESC/X (not by clicking button), set 5s cooldown
            // (same as CANCEL button to prevent spam)
            if (!wasButtonClick) {
                // GUI was closed without clicking a button (ESC/X)
                org.bukkit.Bukkit.getLogger().info("[Portal GUI DEBUG] " + player.getName() + " closed by ESC - setting 5s cooldown");
                // Set 5s cooldown (same as CANCEL)
                portalListener.setDeclineCooldown(playerId);
            } else {
                org.bukkit.Bukkit.getLogger().info("[Portal GUI DEBUG] " + player.getName() + " closed by button click");
            }
            // If CONFIRM was clicked, clearGUICooldown() is already called in the click handler
            // If CANCEL was clicked, setDeclineCooldown() is already called in the click handler
        }
    }

    /**
     * Helper class to store pending portal entry data.
     */
    private static class PortalEntry {
        final String realm;
        final String difficulty;
        final Location portalLocation;

        PortalEntry(String realm, String difficulty, Location portalLocation) {
            this.realm = realm;
            this.difficulty = difficulty;
            this.portalLocation = portalLocation;
        }
    }
}