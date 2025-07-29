package org.maks.eventPlugin.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.eventsystem.EventManager;

import java.util.concurrent.ThreadLocalRandom;

public class MythicMobProgressListener implements Listener {
    private final EventManager eventManager;
    private final BuffManager buffManager;

    public MythicMobProgressListener(EventManager eventManager, BuffManager buffManager) {
        this.eventManager = eventManager;
        this.buffManager = buffManager;
    }

    @EventHandler
    public void onMobDeath(MythicMobDeathEvent event) {
        if (!eventManager.isActive()) return;
        if (event.getKiller() instanceof Player player) {
            int amount = ThreadLocalRandom.current().nextInt(0, 6);
            double multiplier = buffManager.hasBuff(player) ? 1.5 : 1.0;
            eventManager.addProgress(player, amount, multiplier);
        }
    }
}
