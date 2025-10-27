package org.maks.eventPlugin.fullmoon.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for Blood Vial summon mechanic.
 * When a player right-clicks on the designated block with a Blood Vial in their pouch,
 * Amarok boss is summoned.
 */
public class BloodVialSummonListener implements Listener {

    private final FullMoonManager fullMoonManager;
    private final ConfigManager config;

    // Anti-spam cooldown per player (1 second to prevent double-click)
    private final Map<UUID, Long> playerClickCooldown = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 1000; // 1 second

    // Boss spawn cooldown per difficulty (60 seconds between spawns)
    private final Map<String, Long> difficultySpawnCooldown = new HashMap<>();
    private static final long SPAWN_COOLDOWN_MS = 60000; // 60 seconds

    public BloodVialSummonListener(FullMoonManager fullMoonManager, ConfigManager config) {
        this.fullMoonManager = fullMoonManager;
        this.config = config;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        // Event must be active
        if (!fullMoonManager.isEventActive()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location clickedLoc = event.getClickedBlock().getLocation();

        // === POCZĄTEK POPRAWKI: Sprawdzanie obu kociołków ===
        var normalBlockSection = config.getSection("full_moon.coordinates.map1.normal.blood_vial_block");
        var hardBlockSection = config.getSection("full_moon.coordinates.map1.hard.blood_vial_block");

        if (normalBlockSection == null || hardBlockSection == null) return;

        String difficultyKey = null;
        String worldName = clickedLoc.getWorld().getName();
        int cX = clickedLoc.getBlockX();
        int cY = clickedLoc.getBlockY();
        int cZ = clickedLoc.getBlockZ();

        if (worldName.equalsIgnoreCase(normalBlockSection.getString("world", "world")) &&
                cX == normalBlockSection.getInt("x") && cY == normalBlockSection.getInt("y") && cZ == normalBlockSection.getInt("z")) {
            difficultyKey = "normal";
        } else if (worldName.equalsIgnoreCase(hardBlockSection.getString("world", "world")) &&
                cX == hardBlockSection.getInt("x") && cY == hardBlockSection.getInt("y") && cZ == hardBlockSection.getInt("z")) {
            difficultyKey = "hard";
        }

        if (difficultyKey == null) return;
        boolean isHard = difficultyKey.equalsIgnoreCase("hard");
        // === KONIEC POPRAWKI ===

        // (Poprawka "mikro CD" zapobiegająca podwójnym wiadomościom)
        long now = System.currentTimeMillis();
        Long lastClick = playerClickCooldown.get(playerId);
        if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        playerClickCooldown.put(playerId, now);

        // Check boss spawn cooldown (60s between spawns per difficulty)
        Long lastSpawn = difficultySpawnCooldown.get(difficultyKey);
        if (lastSpawn != null && (now - lastSpawn) < SPAWN_COOLDOWN_MS) {
            long remainingSeconds = (SPAWN_COOLDOWN_MS - (now - lastSpawn)) / 1000;
            player.sendMessage("§c§l[Full Moon] §cAmarok was recently summoned! Wait " + remainingSeconds + "s before summoning again.");
            event.setCancelled(true);
            return;
        }

        // --- POCZĄTEK POPRAWKI (Sprawdzanie Pouch + Ekwipunku) ---

        // Check if player has Blood Vial in pouch OR inventory
        if (!PouchHelper.isAvailable()) {
            player.sendMessage("§c§l[Full Moon] §cIngredientPouch plugin is not available!");
            // Kontynuuj, aby sprawdzić ekwipunek
        }

        int bloodVials = getBloodVialCount(player);
        if (bloodVials < 1) {
            player.sendMessage("§c§l[Full Moon] §cYou need a §4Blood Vial §cto summon Amarok!");
            player.sendMessage("§7Blood Vials can be obtained from mini-bosses.");
            return;
        }

        // Consume Blood Vial (try pouch first, then inventory)
        if (!consumeBloodVial(player)) {
            player.sendMessage("§c§l[Full Moon] §cFailed to consume Blood Vial!");
            return;
        }

        // --- KONIEC POPRAWKI ---

        // Get Amarok spawn location for the player's difficulty
        var amarokSpawnSection = config.getSection("full_moon.coordinates.map1." + difficultyKey + ".amarok_spawn");
        if (amarokSpawnSection == null) {
            player.sendMessage("§c§l[Full Moon] §cSpawn location not configured!");
            refundBloodVial(player); // Refund
            return;
        }

        int spawnX = amarokSpawnSection.getInt("x");
        int spawnY = amarokSpawnSection.getInt("y");
        int spawnZ = amarokSpawnSection.getInt("z");
        String spawnWorldName = amarokSpawnSection.getString("world", "world");

        Location spawnLoc = new Location(
                clickedLoc.getWorld(),
                spawnX + 0.5,
                spawnY + 1.0, // Spawn 1 block above ground to prevent spawning in floor
                spawnZ + 0.5
        );

        // Spawn Amarok
        String mobType = isHard ? "amarok_hard" : "amarok_normal";
        if (!spawnAmarok(spawnLoc, mobType)) {
            player.sendMessage("§c§l[Full Moon] §cFailed to summon Amarok!");
            refundBloodVial(player); // Refund
            return;
        }

        // Update difficulty spawn cooldown
        difficultySpawnCooldown.put(difficultyKey, now);

        // Success messages
        event.setCancelled(true);
        player.sendMessage("§c§l[Full Moon] §eYou have summoned §6Amarok, First Werewolf§e!");

        // Broadcast to nearby players
        for (Player nearbyPlayer : spawnLoc.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(spawnLoc) <= 50) {
                if (!nearbyPlayer.equals(player)) {
                    nearbyPlayer.sendMessage("§c§l[Full Moon] §6" + player.getName() + " §ehas summoned §6Amarok§e!");
                }
                nearbyPlayer.sendTitle(
                        "§c§lAMAROK AWAKENS",
                        "§eFirst Werewolf rises!",
                        10, 40, 20
                );
            }
        }
    }

    // --- NOWE METODY POMOCNICZE (Fiolka Krwi) ---

    /**
     * Checks if a given ItemStack is the Blood Vial based on config.
     */
    private boolean isBloodVial(ItemStack item) {
        if (item == null || item.getType() != Material.BEETROOT_SOUP) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();

        // Sprawdź nazwę
        if (!meta.hasDisplayName() || !meta.getDisplayName().equals("§4Blood Vial")) {
            return false;
        }

        // Sprawdź lore
        if (!meta.hasLore()) {
            return false;
        }
        List<String> lore = meta.getLore();
        if (lore.isEmpty() || !lore.get(0).equals("§o§7Used to summon Amarok, First Werewolf")) {
            return false;
        }

        // Sprawdź Unbreakable
        if (!meta.isUnbreakable()) {
            return false;
        }

        return true;
    }

    /**
     * Gets total count of Blood Vials from Pouch and Inventory.
     */
    private int getBloodVialCount(Player player) {
        int pouchVials = 0;
        if (PouchHelper.isAvailable()) {
            pouchVials = PouchHelper.getItemQuantity(player, "blood_vial");
        }

        int inventoryVials = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isBloodVial(item)) {
                inventoryVials += item.getAmount();
            }
        }
        return pouchVials + inventoryVials;
    }

    /**
     * Consumes one Blood Vial, trying Pouch first, then Inventory.
     */
    private boolean consumeBloodVial(Player player) {
        // 1. Spróbuj pobrać z Pouch
        if (PouchHelper.isAvailable() && PouchHelper.consumeBloodVial(player)) {
            return true;
        }

        // 2. Jeśli nie ma w Pouch (lub Pouch jest niedostępny), przeszukaj ekwipunek
        for (ItemStack item : player.getInventory().getContents()) {
            if (isBloodVial(item)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }

        // Nie znaleziono
        return false;
    }

    /**
     * Refunds one Blood Vial, trying Pouch first, then Inventory.
     */
    private void refundBloodVial(Player player) {
        // 1. Spróbuj dodać do Pouch
        if (PouchHelper.isAvailable() && PouchHelper.addItem(player, "blood_vial", 1)) {
            return;
        }

        // 2. Jeśli Pouch niedostępny (lub pełny), stwórz item i daj do ekwipunku
        ItemStack bloodVialItem = new ItemStack(Material.BEETROOT_SOUP, 1);
        ItemMeta meta = bloodVialItem.getItemMeta();
        meta.setDisplayName("§4Blood Vial");
        meta.setLore(Arrays.asList("§o§7Used to summon Amarok, First Werewolf"));
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.setUnbreakable(true);
        // meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES); // HideFlags jest w Options w MM
        bloodVialItem.setItemMeta(meta);

        player.getInventory().addItem(bloodVialItem);
    }

    // --- KONIEC NOWYCH METOD POMOCNICZYCH ---

    /**
     * Spawn Amarok boss using MythicMobs console command.
     */
    private boolean spawnAmarok(Location location, String mobType) {
        try {
            // Build spawn command: mm m spawn <mob> <amount> <world>,<x>,<y>,<z>,<yaw>,<pitch>
            String command = String.format("mm m spawn %s 1 %s,%.2f,%.2f,%.2f,0,0",
                    mobType,
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ()
            );

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}