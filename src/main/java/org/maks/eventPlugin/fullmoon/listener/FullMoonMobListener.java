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

    // +++ POCZĄTEK MODYFIKACJI +++
    // Usunięto statyczną PROGRESS_MAP
    // Dodano randomizer dla zakresów progresu i szans
    private final java.util.Random random = new java.util.Random();

    /**
     * Get the BASE (Normal) progress amount for a mob type.
     * This method handles the 5% chance for normal mobs and 100% for bosses,
     * and returns the value ranges from Full_Moon_PLAN.md.
     */
    private int getBaseProgressForMob(String mobType) {
        // Get base mob type (strip suffixes)
        String baseMobType = mobType;
        if (mobType.endsWith("_normal")) {
            baseMobType = mobType.substring(0, mobType.length() - 7);
        } else if (mobType.endsWith("_hard")) {
            baseMobType = mobType.substring(0, mobType.length() - 5);
        }

        // 100% chance mobs (Bosses / Mini-bosses)
        switch (baseMobType.toLowerCase()) {
            case "werewolf_commander":
                return random.nextBoolean() ? 25 : 35; // Normal: 25 or 35
            case "amarok":
                return 100; // Normal: 100
            case "werewolf_blood_mage_disciple":
                return 100; // Normal: 100
            case "sanguis":
                return 500; // Normal: 500
            case "crystallized_curse":
                return 300; // Special boss
        }

        // 5% chance mobs (Normal mobs)
        if (random.nextDouble() > 0.05) {
            return 0; // 95% chance to get no progress
        }

        // Passed 5% chance, calculate progress
        switch (baseMobType.toLowerCase()) {
            case "werewolf":
                return random.nextInt(3) + 1; // Normal: 1-3
            case "wolf":
                return random.nextInt(2) + 1; // Normal: 1-2
            case "bloody_werewolf":
                return 6; // Normal: 6
            case "blood_sludgeling":
                return 4; // Normal: 4
        }

        return 0; // Default no progress if mob not in plan
    }
    // +++ KONIEC MODYFIKACJI +++

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

        if (killer == null) {
            // Jeśli zabójca nie jest graczem, sprawdź uczestników
            Set<UUID> participants = bossParticipants.getOrDefault(event.getEntity().getUniqueId(), new HashSet<>());
            if (participants.isEmpty()) {
                // Nikt nie uderzył moba, wyczyść i wyjdź
                bossParticipants.remove(event.getEntity().getUniqueId());
                return;
            }
            // Użyj pierwszego uczestnika jako "zabójcy" do celów logiki, jeśli killer jest nullem
            killer = Bukkit.getPlayer(participants.iterator().next());
            if (killer == null) {
                bossParticipants.remove(event.getEntity().getUniqueId());
                return; // Gracz jest offline
            }
        } else {
            // Jeśli killer jest graczem, upewnij się, że jest na liście uczestników
            bossParticipants.computeIfAbsent(event.getEntity().getUniqueId(), k -> new HashSet<>()).add(killer.getUniqueId());
        }

        // Get all participants who damaged this mob
        Set<UUID> participants = bossParticipants.getOrDefault(event.getEntity().getUniqueId(), new HashSet<>());

        // +++ POCZĄTEK MODYFIKACJI: Przebudowana logika progresu +++

        // Get base progress and chance (0 if 95% chance failed for normal mobs)
        int baseProgress = getBaseProgressForMob(mobType);

        // ==== POPRAWKA BŁĘDU (Zliczanie questów): Zdejmujemy blokadę "return;" ====
        // Usunięto:
        // if (baseProgress == 0) {
        //     bossParticipants.remove(event.getEntity().getUniqueId());
        //     return;
        // }
        // Funkcja handleMobKill musi być wywołana ZAWSZE,
        // aby postęp questa (+1) został zaliczony, nawet jeśli postęp eventu (baseProgress) wynosi 0.
        // ==== KONIEC POPRAWKI BŁĘDU ====


        // Award progress and quest updates to all participants
        for (UUID participantId : participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline()) continue;

            // Determine if this participant is in hard mode
            boolean isHard = fullMoonManager.isHardMode(participantId);

            // Get Attrie buff status
            double buffMultiplier = buffManager.hasBuff(participant) ? 1.5 : 1.0;

            // Handle mob kill (quest + event progress)
            // Pass base progress, hard mode status, and buff multiplier to the manager
            // To wywołanie zaliczy +1 do questa (zawsze) i +baseProgress do eventu (jeśli > 0)
            fullMoonManager.handleMobKill(participant, mobType, isHard, baseProgress, buffMultiplier);
        }
        // +++ KONIEC MODYFIKACJI +++


        // Special handling for Amarok (Map 1 boss) - show transition GUI to all participants
        if (mobType.equalsIgnoreCase("amarok_normal") || mobType.equalsIgnoreCase("amarok_hard")) {
            for (UUID participantId : participants) {
                Player participant = Bukkit.getPlayer(participantId);
                if (participant == null || !participant.isOnline()) continue;

                // Check if player has unlocked Map 2 (Quest 4 completed)
                if (fullMoonManager.getQuestManager().hasUnlockedMap2(participantId)) {

                    // --- POCZĄTEK POPRAWKI (Opóźnienie GUI) ---
                    // Delay GUI opening slightly to allow death animation to finish
                    Bukkit.getScheduler().runTaskLater(
                            Bukkit.getPluginManager().getPlugin("EventPlugin"),
                            () -> transitionGUI.open(participant),
                            100L // 5 sekund opóźnienia (5 * 20L)
                    );
                    // --- KONIEC POPRAWKI ---

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