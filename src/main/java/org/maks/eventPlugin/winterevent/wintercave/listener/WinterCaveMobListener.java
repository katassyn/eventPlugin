package org.maks.eventPlugin.winterevent.wintercave.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.maks.eventPlugin.winterevent.wintercave.WinterCaveManager;

/**
 * Listens for Winter Cave mob deaths.
 * Marks mob as killed when player defeats it.
 */
public class WinterCaveMobListener implements Listener {
    private final WinterCaveManager caveManager;

    public WinterCaveMobListener(WinterCaveManager caveManager) {
        this.caveManager = caveManager;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // Check if killer is a player
        if (!(event.getKiller() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getKiller();
        String mobType = event.getMobType().getInternalName();

        // Check if this is the winter cave mob
        if (!mobType.equals("winter_cave_mob")) {
            return;
        }

        // Check if player has an active instance
        if (!caveManager.isInstanceLocked() || !player.getUniqueId().equals(caveManager.getActivePlayerId())) {
            return;
        }

        // Mark mob as killed
        caveManager.setMobKilled(player.getUniqueId());

        player.sendMessage("§f§l[Winter Event] §aYou defeated the creature! Find a player head and claim your reward.");
    }
}
