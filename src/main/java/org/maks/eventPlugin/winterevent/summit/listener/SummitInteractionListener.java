package org.maks.eventPlugin.winterevent.summit.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.winterevent.WinterEventManager;
import org.maks.eventPlugin.winterevent.summit.WinterSummitManager;

/**
 * Listens for player interactions with boss entrance blocks.
 * Checks requirements and creates boss instances.
 */
public class SummitInteractionListener implements Listener {
    private final WinterSummitManager summitManager;
    private final WinterEventManager winterEventManager;
    private final ConfigManager config;

    public SummitInteractionListener(WinterSummitManager summitManager, WinterEventManager winterEventManager, ConfigManager config) {
        this.summitManager = summitManager;
        this.winterEventManager = winterEventManager;
        this.config = config;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Location clickedLoc = event.getClickedBlock().getLocation();

        // Check if event is active
        if (!winterEventManager.isEventActive()) {
            return;
        }

        // Check all 6 interaction points (3 difficulties × 2 bosses)
        String[] difficulties = {"infernal", "hell", "blood"};
        String[] bossTypes = {"bear", "krampus"};

        for (String difficulty : difficulties) {
            for (String bossType : bossTypes) {
                String configPath = "winter_event.summit.interactions." + difficulty + "." + bossType;

                if (matchesLocation(clickedLoc, configPath)) {
                    event.setCancelled(true);
                    handleBossEntry(player, bossType, difficulty);
                    return;
                }
            }
        }
    }

    /**
     * Check if clicked location matches configured interaction point.
     */
    private boolean matchesLocation(Location clicked, String configPath) {
        String world = config.getSection(configPath).getString("world");
        int x = config.getSection(configPath).getInt("x");
        int y = config.getSection(configPath).getInt("y");
        int z = config.getSection(configPath).getInt("z");

        return clicked.getWorld().getName().equals(world) &&
                clicked.getBlockX() == x &&
                clicked.getBlockY() == y &&
                clicked.getBlockZ() == z;
    }

    /**
     * Handle boss entry - check requirements and create instance.
     */
    private void handleBossEntry(Player player, String bossType, String difficulty) {
        // Check if player already has instance
        if (summitManager.hasActiveInstance(player.getUniqueId())) {
            player.sendMessage("§c§l[Winter Event] §cYou already have an active boss instance!");
            return;
        }

        // Check level requirement
        String reqPath = "winter_event.summit.requirements." + difficulty;
        int requiredLevel = config.getSection(reqPath).getInt("level");
        if (player.getLevel() < requiredLevel) {
            player.sendMessage("§c§l[Winter Event] §cYou need level " + requiredLevel + " to enter " + difficulty + " mode!");
            return;
        }

        // Check IPS requirement
        int requiredIPS = config.getSection(reqPath).getInt("ips");
        if (requiredIPS > 0 && PouchHelper.isAvailable()) {
            if (!PouchHelper.hasEnough(player, "ips", requiredIPS)) {
                player.sendMessage("§c§l[Winter Event] §cYou need " + requiredIPS + " IPS to enter " + difficulty + " mode!");
                return;
            }
        }

        // Check boss entry cost
        String costPath = "winter_event.summit.boss_costs." + bossType + "." + difficulty;
        int itemCost = config.getSection(costPath).getInt(difficulty, 1);
        String itemId = bossType.equals("bear") ? "honey_bait" : "winter_solstice_log";

        if (PouchHelper.isAvailable()) {
            if (!PouchHelper.hasEnough(player, itemId, itemCost)) {
                player.sendMessage("§c§l[Winter Event] §cYou need " + itemCost + "x " + itemId + " to enter!");
                return;
            }

            // Consume IPS
            if (requiredIPS > 0) {
                PouchHelper.consumeItem(player, "ips", requiredIPS);
            }

            // Consume entry item
            if (!PouchHelper.consumeItem(player, itemId, itemCost)) {
                player.sendMessage("§c§l[Winter Event] §cFailed to consume entry items!");
                return;
            }
        }

        // Create instance
        player.sendMessage("§f§l[Winter Event] §aCreating boss arena...");
        summitManager.createBossInstance(player, bossType, difficulty);
    }
}
