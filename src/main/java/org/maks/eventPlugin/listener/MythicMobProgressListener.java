package org.maks.eventPlugin.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.eventsystem.EventManager;

import java.util.concurrent.ThreadLocalRandom;

public class MythicMobProgressListener implements Listener {
    private final java.util.Map<String, EventManager> events;
    private final BuffManager buffManager;

    public MythicMobProgressListener(java.util.Map<String, EventManager> events, BuffManager buffManager) {
        this.events = events;
        this.buffManager = buffManager;
    }

    @EventHandler
    public void onMobDeath(MythicMobDeathEvent event) {
        if (!(event.getKiller() instanceof Player player)) return;
        double multiplier = buffManager.hasBuff(player) ? 1.5 : 1.0;
        for (EventManager manager : events.values()) {
            manager.checkExpiry();
            if (manager.isActive()) {
                int amount = manager.getRandomProgress();
                manager.addProgress(player, amount, multiplier);
            }
        }
    }
}
