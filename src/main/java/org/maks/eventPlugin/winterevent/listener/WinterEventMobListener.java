package org.maks.eventPlugin.winterevent.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.winterevent.WinterEventManager;

import java.util.*;

/**
 * Listens to Winter Event mob deaths (x_mas_* mobs).
 * Tracks event progress based on difficulty and mob type.
 */
public class WinterEventMobListener implements Listener {
    private final WinterEventManager winterEventManager;
    private final BuffManager buffManager;

    // Track players who damaged a boss (for participation rewards)
    private final Map<UUID, Set<UUID>> mobParticipants = new HashMap<>();

    public WinterEventMobListener(WinterEventManager winterEventManager, BuffManager buffManager) {
        this.winterEventManager = winterEventManager;
        this.buffManager = buffManager;
    }

    /**
     * Track damage to mobs for participation tracking.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!winterEventManager.isEventActive()) return;

        Player damager = getDamager(event.getDamager());
        if (damager == null) return;

        UUID mobId = event.getEntity().getUniqueId();
        mobParticipants.computeIfAbsent(mobId, k -> new HashSet<>()).add(damager.getUniqueId());
    }

    /**
     * Handle Winter Event mob death.
     */
    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!winterEventManager.isEventActive()) return;

        String mobType = event.getMobType().getInternalName();

        // Only handle x_mas_* mobs
        if (!mobType.toLowerCase().startsWith("sugar_goblin_miner_") &&
            !mobType.toLowerCase().startsWith("snow_wolf_") &&
            !mobType.toLowerCase().startsWith("dough_troll_kneader_") &&
            !mobType.toLowerCase().startsWith("cookie_commander_wolf_") &&
            !mobType.toLowerCase().startsWith("cookie_thief_goblin_") &&
            !mobType.toLowerCase().startsWith("cookie_destroyer_sludge_") &&
            !mobType.toLowerCase().startsWith("walking_christmas_tree_") &&
            !mobType.toLowerCase().startsWith("gluttonous_bear_") &&
            !mobType.toLowerCase().startsWith("krampus_spirit_")) {
            return; // Not a Winter Event mob
        }

        // Get killer
        Entity killerEntity = event.getKiller();
        Player killer = getPlayer(killerEntity);

        if (killer == null) {
            // Check participants if no direct killer
            Set<UUID> participants = mobParticipants.getOrDefault(event.getEntity().getUniqueId(), new HashSet<>());
            if (participants.isEmpty()) {
                mobParticipants.remove(event.getEntity().getUniqueId());
                return;
            }
            killer = Bukkit.getPlayer(participants.iterator().next());
            if (killer == null) {
                mobParticipants.remove(event.getEntity().getUniqueId());
                return;
            }
        } else {
            mobParticipants.computeIfAbsent(event.getEntity().getUniqueId(), k -> new HashSet<>()).add(killer.getUniqueId());
        }

        // Get all participants
        Set<UUID> participants = mobParticipants.getOrDefault(event.getEntity().getUniqueId(), new HashSet<>());

        // Determine difficulty from mob suffix
        String difficulty = "infernal"; // default
        if (mobType.endsWith("_hell")) {
            difficulty = "hell";
        } else if (mobType.endsWith("_blood")) {
            difficulty = "blood";
        }

        // Get progress amount based on mob tier and difficulty
        int baseProgress = winterEventManager.getProgressForMob(mobType, difficulty);

        // Award progress to all participants
        for (UUID participantId : participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline()) continue;

            // Update player difficulty tracking
            winterEventManager.setPlayerDifficulty(participantId, difficulty);

            // Get buff multiplier
            double buffMultiplier = buffManager.hasBuff(participant) ? 1.5 : 1.0;

            // Handle mob kill (event progress)
            winterEventManager.handleMobKill(participant, mobType, baseProgress, buffMultiplier);

            // === QUEST SYSTEM INTEGRATION ===
            if (winterEventManager.getQuestManager() != null) {
                // Strip difficulty suffix for quest matching
                String baseMobType = stripDifficultySuffix(mobType);
                boolean isBlood = difficulty.equals("blood");

                // NON-PHYSICAL COLLECTION: Candy Cane (from Sugar Goblins)
                if (baseMobType.equals("sugar_goblin_miner")) {
                    if (Math.random() < 0.175) { // 17.5% chance
                        boolean completed = winterEventManager.getQuestManager().addQuestProgress(
                            participantId, "candy_cane", 1, false);

                        int progress = winterEventManager.getQuestManager().getQuestProgress(participantId, 3);
                        participant.sendMessage("§e§l[Winter Event] §a+1 Candy Cane! §7(" + progress + "/150)");

                        if (completed) {
                            participant.sendMessage("§a§lQuest completed!");
                            participant.sendTitle("§aQuest Complete!", "§7Sweet Collector", 10, 40, 10);
                        }
                    }
                }

                // NON-PHYSICAL COLLECTION: Frozen Shard (from Snow Wolves)
                if (baseMobType.equals("snow_wolf")) {
                    if (Math.random() < 0.175) { // 17.5% chance
                        boolean completed = winterEventManager.getQuestManager().addQuestProgress(
                            participantId, "frozen_shard", 1, false);

                        int progress = winterEventManager.getQuestManager().getQuestProgress(participantId, 10);
                        participant.sendMessage("§e§l[Winter Event] §b+1 Frozen Shard! §7(" + progress + "/150)");

                        if (completed) {
                            participant.sendMessage("§a§lQuest completed!");
                            participant.sendTitle("§aQuest Complete!", "§7Winter Shards", 10, 40, 10);
                        }
                    }
                }

                // NORMAL QUEST PROGRESS (kill quests)
                boolean questCompleted = winterEventManager.getQuestManager().addQuestProgress(
                    participantId, baseMobType, 1, isBlood);

                if (questCompleted) {
                    participant.sendMessage("§a§l[Winter Event] §aQuest completed!");
                    participant.sendTitle("§aQuest Complete!", "", 10, 40, 10);
                }
            }
        }

        // Clean up participants tracking
        mobParticipants.remove(event.getEntity().getUniqueId());
    }

    /**
     * Strip difficulty suffix from mob type for quest matching.
     */
    private String stripDifficultySuffix(String mobType) {
        if (mobType.endsWith("_inf") || mobType.endsWith("_infernal")) {
            return mobType.substring(0, mobType.lastIndexOf('_'));
        }
        if (mobType.endsWith("_hell")) {
            return mobType.substring(0, mobType.lastIndexOf('_'));
        }
        if (mobType.endsWith("_blood")) {
            return mobType.substring(0, mobType.lastIndexOf('_'));
        }
        return mobType;
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
