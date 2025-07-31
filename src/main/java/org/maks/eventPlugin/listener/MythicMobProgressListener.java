package org.maks.eventPlugin.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.eventsystem.EventManager;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

public class MythicMobProgressListener implements Listener {
    private final java.util.Map<String, EventManager> events;
    private final BuffManager buffManager;
    private Method isMythicMobMethod = null;
    private Object mythicMobsAPI = null;

    public MythicMobProgressListener(java.util.Map<String, EventManager> events, BuffManager buffManager) {
        this.events = events;
        this.buffManager = buffManager;
        
        // Try to get MythicMobs API using reflection
        try {
            Class<?> apiClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method getInstance = apiClass.getMethod("inst");
            mythicMobsAPI = getInstance.invoke(null);
            isMythicMobMethod = mythicMobsAPI.getClass().getMethod("isMythicMob", Entity.class);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to initialize MythicMobs API: " + e.getMessage());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // If we couldn't initialize the MythicMobs API, do nothing
        if (isMythicMobMethod == null || mythicMobsAPI == null) {
            return;
        }
        
        try {
            // Check if the entity is a MythicMob
            Entity entity = event.getEntity();
            boolean isMythicMob = (boolean) isMythicMobMethod.invoke(mythicMobsAPI, entity);
            
            if (!isMythicMob) {
                return;
            }
            
            // Get the killer (only LivingEntity has getKiller method)
            if (!(entity instanceof org.bukkit.entity.LivingEntity)) {
                return;
            }
            
            org.bukkit.entity.LivingEntity livingEntity = (org.bukkit.entity.LivingEntity) entity;
            Player player = livingEntity.getKiller();
            if (player == null) {
                return;
            }
            
            double multiplier = buffManager.hasBuff(player) ? 1.5 : 1.0;
            for (EventManager manager : events.values()) {
                manager.checkExpiry();
                if (manager.isActive()) {
                    int amount = manager.getRandomProgress();
                    manager.addProgress(player, amount, multiplier);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error processing MythicMob death: " + e.getMessage());
        }
    }
}
