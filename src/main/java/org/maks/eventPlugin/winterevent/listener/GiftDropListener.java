package org.maks.eventPlugin.winterevent.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.maks.eventPlugin.winterevent.WinterEventManager;

/**
 * Listens to ALL MythicMob deaths globally and gives Winter gifts with 0.1% chance.
 * This is independent of which mob type is killed.
 */
public class GiftDropListener implements Listener {
    private final WinterEventManager winterEventManager;

    public GiftDropListener(WinterEventManager winterEventManager) {
        this.winterEventManager = winterEventManager;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // Only drop gifts if Winter Event is active
        if (!winterEventManager.isEventActive()) {
            return;
        }

        // Check if killer is a player
        if (!(event.getKiller() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getKiller();

        // Roll for gift drop (0.1% chance)
        double dropChance = winterEventManager.getGlobalDropChance();
        if (Math.random() > dropChance) {
            return; // No drop
        }

        // Select random rarity
        String rarity = winterEventManager.selectRandomGiftRarity();
        String boxId = winterEventManager.getGiftBoxId(rarity);
        String color = winterEventManager.getRarityColor(rarity);
        String rarityName = winterEventManager.getRarityName(rarity);

        // Give gift via EliteLootbox
        String command = "el give " + player.getName() + " " + boxId;

        // Debug: Log the command being executed
        Bukkit.getLogger().info("[Winter Event] Executing command: " + command);

        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!success) {
            Bukkit.getLogger().warning("[Winter Event] Failed to give lootbox! Command: " + command);
        }

        // Notify player
        player.sendMessage("§f§l[Winter Event] §fYou found a " + color + rarityName + " Gift§f!");
        //player.sendTitle("§f⛄ Winter Gift!", color + rarityName, 10, 60, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
    }
}
