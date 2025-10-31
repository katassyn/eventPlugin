package org.maks.eventPlugin.fullmoon.listener;

import com.sk89q.worldedit.math.BlockVector3;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.map2.Map2BossSequenceManager;
import org.maks.eventPlugin.fullmoon.map2.Map2Instance;

import java.util.UUID;

/**
 * Listener for boss spawns and deaths in Map 2 (Blood Moon Arena).
 * Manages the boss sequence progression and entity tracking.
 */
public class Map2BossListener implements Listener {

    private final FullMoonManager fullMoonManager;

    public Map2BossListener(FullMoonManager fullMoonManager) {
        this.fullMoonManager = fullMoonManager;
    }

    /**
     * Handle MythicMob spawns to track entities spawned via console commands.
     * Automatically tags mobs based on their type and adds them to the instance.
     */
    @EventHandler
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        if (!fullMoonManager.isEventActive()) return;

        // Get entity and check if it's a LivingEntity
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        Location spawnLoc = entity.getLocation();

        // Get mob internal name
        String mobType = event.getMobType() != null ? event.getMobType().getInternalName() : null;
        if (mobType == null) return;

        // Find which instance this mob belongs to (check if spawn location is within any instance)
        Map2Instance matchedInstance = null;
        for (Map2Instance instance : fullMoonManager.getMap2InstanceManager().getAllInstances()) {
            BlockVector3 spawnVec = BlockVector3.at(
                    spawnLoc.getBlockX(),
                    spawnLoc.getBlockY(),
                    spawnLoc.getBlockZ()
            );
            if (instance.getRegion().contains(spawnVec)) {
                matchedInstance = instance;
                break;
            }
        }

        if (matchedInstance == null) return;

        // Track entity in instance
        matchedInstance.trackEntity(entity.getUniqueId());

        // Add instance tag to all mobs
        entity.addScoreboardTag("fullmoon_instance_" + matchedInstance.getInstanceId());

        // Determine mob role based on type and add appropriate tags
        if (mobType.equals("werewolf_blood_mage_disciple_normal") || mobType.equals("werewolf_blood_mage_disciple_hard")) {
            // Mini-boss
            entity.addScoreboardTag("fullmoon_miniboss");
            Bukkit.getLogger().info("[Full Moon] Tagged mini-boss " + mobType + " in instance " + matchedInstance.getInstanceId());
        } else if (mobType.equals("sanguis_normal") || mobType.equals("sanguis_hard")) {
            // Final boss
            entity.addScoreboardTag("fullmoon_finalboss");
            Bukkit.getLogger().info("[Full Moon] Tagged final boss " + mobType + " in instance " + matchedInstance.getInstanceId());
        } else {
            // Normal mob (bloody_werewolf or blood_sludgeling)
            entity.addScoreboardTag("fullmoon_normal_mob");
        }
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!fullMoonManager.isEventActive()) return;

        LivingEntity entity = (LivingEntity) event.getEntity();
        Map2BossSequenceManager sequenceManager = fullMoonManager.getMap2BossSequenceManager();

        // Check if this is a mini-boss or final boss
        boolean isMiniBoss = sequenceManager.isMiniBoss(entity);
        boolean isFinalBoss = sequenceManager.isFinalBoss(entity);

        if (!isMiniBoss && !isFinalBoss) return;

        // Get the instance ID from entity tags
        UUID instanceId = sequenceManager.getInstanceIdFromEntity(entity);
        if (instanceId == null) return;

        // Find the instance and player
        Map2Instance instance = null;
        Player player = null;

        for (Map2Instance inst : fullMoonManager.getMap2InstanceManager().getAllInstances()) {
            if (inst.getInstanceId().equals(instanceId)) {
                instance = inst;
                player = Bukkit.getPlayer(inst.getPlayerId());
                break;
            }
        }

        if (instance == null || player == null) return;

        // Handle boss death based on type
        if (isMiniBoss) {
            // Get difficulty from instance (not from player's current mode)
            sequenceManager.handleMiniBossDeath(instance, entity.getUniqueId(), player);
        } else if (isFinalBoss) {
            sequenceManager.handleFinalBossDeath(instance, player);

            // Note: Instance removal is handled by Map2BossSequenceManager.startCleanupCountdown()
            // which schedules cleanup after 60 seconds countdown with teleport
        }
    }
}
