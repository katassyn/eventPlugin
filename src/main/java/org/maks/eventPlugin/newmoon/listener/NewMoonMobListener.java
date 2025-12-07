package org.maks.eventPlugin.newmoon.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.maks.eventPlugin.newmoon.NewMoonManager;

import java.util.Random;

/**
 * Handles MythicMob deaths for the New Moon event.
 * Awards quest progress and event progress when mobs are killed.
 *
 * Handles special cases:
 * - Walking Wood: Gives non-physical quest progress for quest 2 (white chain) with 10% droprate, 100% event progress
 * - Nighty Witch: Gives non-physical quest progress for quest 7 (black chain) with 15% droprate, 5% event progress (doubled amount)
 * - Lunatic Goblin, Lord's Squire, Lord's Legionnaire: 5% event progress droprate (doubled amount)
 * - All other mobs: Standard kill tracking
 */

public class NewMoonMobListener implements Listener {

    private final NewMoonManager newMoonManager;
    private final Random random = new Random();

    public NewMoonMobListener(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!newMoonManager.isEventActive()) {
            return;
        }

        // Check if killer is a player
        if (!(event.getKiller() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getKiller();
        String mobType = event.getMobType().getInternalName();

        // Determine if this was hard mode based on mob suffix
        boolean isHard = mobType.endsWith("_hard");

        // Get base mob type without _normal or _hard suffix
        String baseMobType = mobType;
        if (mobType.endsWith("_normal")) {
            baseMobType = mobType.substring(0, mobType.length() - 7);
        } else if (mobType.endsWith("_hard")) {
            baseMobType = mobType.substring(0, mobType.length() - 5);
        }

        // Check if this is Walking Wood (special case - gives quest progress directly)
        if (baseMobType.equals("walking_wood")) {
            handleWalkingWoodDeath(player, isHard);
            return;
        }
        // Check if this is Nighty Witch (special case - gives quest progress with droprate)
        if (baseMobType.equals("nighty_witch")) {
            handleNightyWitchDeath(player, isHard);
            return;
        }

        // Determine progress amount based on mob type
        int eventProgressAmount = getMobProgressAmount(baseMobType, isHard);

        // Check if this mob has a droprate (5% for specific mobs)
        if (hasMobDroprate(baseMobType)) {
            // 5% chance to drop EVENT progress
            double roll = random.nextDouble();
            if (roll >= 0.05) {
                // No EVENT progress this time (95% chance)
                // But STILL count quest progress by passing 0 for event progress
                eventProgressAmount = 0;
            }
            // 5% chance - continue to give full event progress
        }

        // Check for Attrie buff (from IngredientPouch)
        // For now we assume no buff, but this can be enhanced later
        double buffMultiplier = 1.0;

        // Handle mob kill - ALWAYS updates quest progress, but event progress depends on droprate
        newMoonManager.handleMobKill(player, baseMobType, isHard, eventProgressAmount, buffMultiplier);

        // Send feedback to player based on mob type
        sendMobKillFeedback(player, baseMobType, isHard);
    }

    /**
     * Handle Walking Wood death - gives non-physical quest progress.
     * Walking Wood gives progress for quest 2 (white chain) and quest 7 (black chain).
     *
     * According to specification:
     * - Quest requires 100 progress total
     * - We'll give random amount based on rarity
     */
    /**
     * Handle Walking Wood death - gives non-physical quest progress.
     * Walking Wood gives progress for quest 2 (white chain) with 10% droprate.
     *
     * According to specification:
     * - Quest 2 requires 30 progress total
     * - 10% chance to get progress per kill
     */
    private void handleWalkingWoodDeath(Player player, boolean isHard) {
        // Always give event progress (25 or 35 randomly)
        int eventProgressAmount = getMobProgressAmount("walking_wood", isHard);
        double buffMultiplier = 1.0;
        newMoonManager.handleMobKill(player, "walking_wood", isHard, eventProgressAmount, buffMultiplier);

        // 10% chance to get quest progress
        double roll = random.nextDouble();

        if (roll >= 0.10) {
            // No quest progress this time (90% chance)
            return;
        }

        // 10% chance - give 1 quest progress
        int questProgressAmount = 1;

        // Give progress to walking_wood target (quest 2 only)
        newMoonManager.handleQuestProgressWithFeedback(player, "walking_wood", questProgressAmount, isHard);
    }
    /**
     * Handle Nighty Witch death - updates quest progress with droprate AND normal mob kills.
     * Nighty Witches:
     * - ALWAYS count towards quest 6 (kill 100 Nighty Witches) - 100% chance
     * - 15% chance to give progress for quest 7 (black chain essence) - non-physical quest progress
     * - 5% chance to give event progress (2 normal, 4 hard)
     *
     * According to specification:
     * - Quest 6: 100 kills required, 100% tracking
     * - Quest 7: 50 progress total, 15% droprate
     * - Event progress: 5% droprate, doubled amount
     */
    private void handleNightyWitchDeath(Player player, boolean isHard) {
        // ALWAYS track kill quest (quest 6) - pass 0 event progress initially
        int eventProgressAmount = 0;

        // Check 5% chance for event progress
        double eventProgressRoll = random.nextDouble();
        if (eventProgressRoll < 0.05) {
            // 5% chance - give event progress
            eventProgressAmount = getMobProgressAmount("nighty_witch", isHard);
        }

        // ALWAYS call handleMobKill to track quest 6 (kill count), with or without event progress
        double buffMultiplier = 1.0;
        newMoonManager.handleMobKill(player, "nighty_witch", isHard, eventProgressAmount, buffMultiplier);

        // Then, 15% chance to get non-physical quest progress (for quest 7 - essence)
        double questProgressRoll = random.nextDouble();

        if (questProgressRoll < 0.15) {
            // 15% chance - give 1 quest progress for essence
            int questProgressAmount = 1;

            // Give progress to nighty_witch_essence target (quest 7 only)
            newMoonManager.handleQuestProgressWithFeedback(player, "nighty_witch_essence", questProgressAmount, isHard);
        }
    }


    /**
     * Check if this mob type has a droprate (5% chance).
     * According to New Moon specification:
     * - Lunatic Goblin: 5% (doubled amount)
     * - Lord's Squire: 5% (doubled amount)
     * - Lord's Legionnaire: 5% (doubled amount)
     *
     * Note: Nighty Witch also has 5% but is handled separately
     * Note: Walking Wood has 100% droprate (no restriction)
     *
     * @param baseMobType The base mob type (without _normal/_hard suffix)
     * @return true if this mob has 5% droprate
     */
    private boolean hasMobDroprate(String baseMobType) {
        return switch (baseMobType) {
            case "lunatic_goblin", "lords_squire", "lords_legionnaire" -> true;
            default -> false;
        };
    }

    /**
     * Get the progress amount for a mob type.
     * This determines how much event progress the mob gives.
     *
     * Note: Hard mode multiplier (2x) is applied in NewMoonManager.handleMobKill(),
     * so base values here are for normal mode unless otherwise noted.
     *
     * @param baseMobType The base mob type (without _normal/_hard suffix)
     * @param isHard Whether this is hard mode kill
     * @return Progress amount
     */
    private int getMobProgressAmount(String baseMobType, boolean isHard) {
        return switch (baseMobType) {
            // Map 1 mobs (normal mobs doubled back to original values)
            case "lunatic_goblin" -> {
                // Normal: 2-4, Hard: 4-8 (2x multiplier applied automatically)
                yield (random.nextInt(2) + 1) * 2; // 2 or 4 (doubled from 1 or 2)
            }
            case "nighty_witch" -> {
                // Normal: 2, Hard: 4 (2x multiplier applied automatically)
                yield 2; // 2 (doubled from 1)
            }
            case "walking_wood" -> {
                // Normal: 12-13 or 17-18, Hard: 25-26 or 35-36 (2x multiplier applied automatically)
                yield random.nextBoolean() ? 13 : 18; // 13 or 18 (unchanged - not a normal mob)
            }
            case "patriarch_of_the_lords" -> {
                // Special case: Normal gives 50, Hard gives 150 (3x, not 2x!)
                // So we need to return 75 for hard mode to get 150 after 2x multiplier
                // For normal: return 50 to get 50 after 1x multiplier
                yield isHard ? 75 : 50; // (unchanged - not a normal mob)
            }

            // Map 2 mobs (normal mobs doubled back to original values)
            case "lords_squire" -> 6;  // Normal: 6, Hard: 12 (2x) - doubled from 3
            case "lords_legionnaire" -> 4;  // Normal: 4, Hard: 8 (2x) - doubled from 2
            case "lords_guard" -> 50;  // Normal: 50, Hard: 100 (2x) - unchanged (mini-boss)

            // Bosses (Lords) (unchanged - not normal mobs)
            case "lord_silvanus", "lord_malachai" -> 250;  // Normal: 250, Hard: 500 (2x) - unchanged

            default -> 1;
        };
    }

    /**
     * Send feedback to player when they kill a mob.
     */
    private void sendMobKillFeedback(Player player, String baseMobType, boolean isHard) {
        String difficultyTag = isHard ? " §c(Hard)" : "";

        switch (baseMobType) {
            case "patriarch_of_the_lords":
                player.sendMessage("§6§l[New Moon] §eYou defeated the Patriarch of the Lords!" + difficultyTag);
                player.sendTitle("§6§lPatriarch Defeated!", "", 10, 40, 10);
                break;

            case "lords_guard":
                player.sendMessage("§6§l[New Moon] §eYou defeated a Lord's Guard!" + difficultyTag);
                break;

            case "lord_silvanus":
                player.sendMessage("§f§l[New Moon] §eYou defeated Lord Silvanus, White King!" + difficultyTag);
                player.sendTitle("§f§l§oLord Silvanus Defeated!", "", 10, 60, 10);
                break;

            case "lord_malachai":
                player.sendMessage("§0§l[New Moon] §eYou defeated Lord Malachai, Black King!" + difficultyTag);
                player.sendTitle("§0§l§oLord Malachai Defeated!", "", 10, 60, 10);
                break;
        }
    }
}
