package org.maks.eventPlugin.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.eventsystem.EventManager;

import java.util.Map;

/**
 * Handles progress awarding for MythicMob kills. Any Mythic mob killed by a
 * player (directly, via projectile, or through a tamed entity) will grant
 * progress towards all active events, such as Monster Hunter.
 */
public class MythicMobProgressListener implements Listener {
    private final Map<String, EventManager> events;
    private final BuffManager buffManager;

    public MythicMobProgressListener(Map<String, EventManager> events, BuffManager buffManager) {
        this.events = events;
        this.buffManager = buffManager;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        Entity killerEntity = event.getKiller();
        Player player = null;

        if (killerEntity instanceof Player) {
            player = (Player) killerEntity;
        } else if (killerEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
            player = (Player) projectile.getShooter();
        } else if (killerEntity instanceof Tameable tameable && tameable.getOwner() instanceof Player) {
            player = (Player) tameable.getOwner();
        }

        if (player == null) {
            return;
        }

        double multiplier = buffManager.hasBuff(player) ? 1.5 : 1.0;
        for (EventManager manager : events.values()) {
            // +++ POCZĄTEK MODYFIKACJI +++
            // Ignore the full_moon event; it is handled by FullMoonMobListener
            // Ignore the winter_event; it is handled by WinterEventMobListener
            if (manager.getEventId().equalsIgnoreCase("full_moon") ||
                manager.getEventId().equalsIgnoreCase("winter_event")) {
                continue;
            }
            // +++ KONIEC MODYFIKACJI +++

            manager.checkExpiry();
            if (manager.isActive()) {
                int amount = manager.getRandomProgress();
                manager.addProgress(player, amount, multiplier);
            }
        }
    }
}