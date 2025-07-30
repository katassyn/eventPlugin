package org.maks.eventPlugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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
    public void onMobDeath(Event genericEvent) {
        // Use reflection to safely check if this is a MythicMobDeathEvent
        if (!genericEvent.getEventName().equals("MythicMobDeathEvent")) {
            return;
        }
        
        try {
            // Get the killer using reflection
            Object killer = genericEvent.getClass().getMethod("getKiller").invoke(genericEvent);
            if (!(killer instanceof Player player)) return;
            
            double multiplier = buffManager.hasBuff(player) ? 1.5 : 1.0;
            for (EventManager manager : events.values()) {
                manager.checkExpiry();
                if (manager.isActive()) {
                    int amount = manager.getRandomProgress();
                    manager.addProgress(player, amount, multiplier);
                }
            }
        } catch (Exception e) {
            // Silently ignore reflection errors
        }
    }
}
