package org.maks.eventPlugin.fullmoon.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.gui.Map2TransitionGUI;

import java.util.*;

/**
 * Listens to MythicMobs deaths in the Full Moon event.
 * Tracks quest progress, event progress, and boss kill participation.
 */
public class FullMoonMobListener implements Listener {

    private final FullMoonManager fullMoonManager;
    private final BuffManager buffManager;
    private final Map2TransitionGUI transitionGUI;

    // Track players who damaged a boss (for participation rewards)
    // Boss UUID -> Set of Player UUIDs
    private final Map<UUID, Set<UUID>> bossParticipants = new HashMap<>();

    // Define progress amounts for each mob type
    private static final Map<String, Integer> PROGRESS_MAP = new HashMap<>();

    static {
        // Map 1 mobs (normal)
        PROGRESS_MAP.put("werewolf_normal", 2);  // Random 1-3, use average
        PROGRESS_MAP.put("wolf_normal", 2);      // Random 1-2, use average
        PROGRESS_MAP.put("werewolf_commander_normal", 30); // 25 or 35, use average
        PROGRESS_MAP.put("amarok_normal", 100);

        // Map 1 mobs (hard)
        PROGRESS_MAP.put("werewolf_hard", 4);    // Random 2-6, use average
        PROGRESS_MAP.put("wolf_hard", 3);        // Random 2-4, use average
        PROGRESS_MAP.put("werewolf_commander_hard", 62); // 50 or 75, use average
        PROGRESS_MAP.put("amarok_hard", 300);

        // Map 2 mobs (normal)
        PROGRESS_MAP.put("bloody_werewolf_normal", 6);
        PROGRESS_MAP.put("werewolf_blood_mage_disciple_normal", 100);
        PROGRESS_MAP.put("sanguis_normal", 500);

        // Map 2 mobs (hard)
        PROGRESS_MAP.put("bloody_werewolf_hard", 12);
        PROGRESS_MAP.put("werewolf_blood_mage_disciple_hard", 200);
        PROGRESS_MAP.put("sanguis_hard", 1000);

        // blood_sludgeling - existing mob (same for both modes, progress: 4 normal, 8 hard via 2x multiplier)
        PROGRESS_MAP.put("blood_sludgeling", 4);

        // Special boss
        PROGRESS_MAP.put("crystallized_curse", 300); // Single difficulty, very hard
    }

    public FullMoonMobListener(FullMoonManager fullMoonManager, BuffManager buffManager, Map2TransitionGUI transitionGUI) {
        this.fullMoonManager = fullMoonManager;
        this.buffManager = buffManager;
        this.transitionGUI = transitionGUI;
    }

    /**
     * Track damage to potential bosses for participation tracking.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!fullMoonManager.isEventActive()) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player damager = getDamager(event.getDamager());
        if (damager == null) return;

        // Track participation for all damaged mobs
        UUID mobId = event.getEntity().getUniqueId();
        bossParticipants.computeIfAbsent(mobId, k -> new HashSet<>()).add(damager.getUniqueId());
    }

    /**
     * Handle MythicMob death.
     */
    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!fullMoonManager.isEventActive()) return;

        String mobType = event.getMobType().getInternalName();
        Entity killerEntity = event.getKiller();
        Player killer = getPlayer(killerEntity);

        if (killer == null) return;

        // Get all participants who damaged this mob
        Set<UUID> participants = bossParticipants.getOrDefault(event.getEntity().getUniqueId(), new HashSet<>());
        participants.add(killer.getUniqueId()); // Ensure killer is included

        // Determine if this is hard mode based on mob name
        boolean isHard = mobType.endsWith("_hard");

        // Get progress amount for this mob type
        Integer baseProgress = PROGRESS_MAP.get(mobType);
        if (baseProgress == null) {
            baseProgress = 1; // Default if not defined
        }

        // Award progress and quest updates to all participants
        for (UUID participantId : participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline()) continue;

            // Apply Attrie buff if player has it
            double buffMultiplier = buffManager.hasBuff(participant) ? 1.5 : 1.0;
            double totalMultiplier = buffMultiplier;

            // Handle mob kill (quest + event progress with hard mode 2x bonus)
            fullMoonManager.handleMobKill(participant, mobType, isHard, baseProgress);

            // Additional Attrie buff for event progress only (not quest kills)
            if (buffMultiplier > 1.0) {
                int additionalProgress = (int) Math.round(baseProgress * (buffMultiplier - 1.0));
                if (isHard) additionalProgress *= 2; // Apply hard mode multiplier
                fullMoonManager.getEventManager().addProgress(participant, additionalProgress, 1.0);
            }
        }

        // Special handling for Amarok (Map 1 boss) - show transition GUI to all participants
        if (mobType.equalsIgnoreCase("amarok_normal") || mobType.equalsIgnoreCase("amarok_hard")) {
            for (UUID participantId : participants) {
                Player participant = Bukkit.getPlayer(participantId);
                if (participant == null || !participant.isOnline()) continue;

                // Check if player has unlocked Map 2 (Quest 4 completed)
                if (fullMoonManager.getQuestManager().hasUnlockedMap2(participantId)) {
                    // Delay GUI opening slightly to allow death animation to finish
                    Bukkit.getScheduler().runTaskLater(
                            Bukkit.getPluginManager().getPlugin("EventPlugin"),
                            () -> transitionGUI.open(participant),
                            20L // 1 second delay
                    );
                } else {
                    participant.sendMessage("§c§l[Full Moon] §eComplete more quests to unlock the Blood Moon Arena!");
                }
            }
        }

        // Clean up participants tracking for this mob
        bossParticipants.remove(event.getEntity().getUniqueId());
    }

    /**
     * Get the player who caused damage (direct, projectile, or tamed pet).
     */
    private Player getDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
            return (Player) projectile.getShooter();
        } else if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player) {
            return (Player) tameable.getOwner();
        }
        return null;
    }

    /**
     * Get the player from a killer entity (direct, projectile, or tamed pet).
     */
    private Player getPlayer(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        } else if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
            return (Player) projectile.getShooter();
        } else if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player) {
            return (Player) tameable.getOwner();
        }
        return null;
    }
}
