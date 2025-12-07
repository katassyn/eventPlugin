package org.maks.eventPlugin.newmoon;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Represents a quest in the New Moon event.
 *
 * New Moon has TWO quest chains (10 quests total):
 * - White Lord Chain (quests 1-5): Quest 3 unlocks portal to White Realm (Lord Silvanus)
 * - Black Lord Chain (quests 6-10): Quest 8 unlocks portal to Black Realm (Lord Malachai)
 *
 * QUEST UNLOCK SYSTEM:
 * - Quests unlock sequentially within each chain (white 1→2→3→4→5, black 1→2→3→4→5)
 * - Both chains can progress in parallel (can do white 1 and black 1 at same time)
 * - Player must complete AND claim previous quest in chain to unlock next quest
 *
 * @param id Quest ID (1-10, where 1-5 is White Chain, 6-10 is Black Chain)
 * @param chainType Which chain this quest belongs to ("white" or "black")
 * @param description Quest description shown to players
 * @param targetMobType MythicMobs internal name (e.g., "lunatic_goblin", "patriarch_of_the_lords")
 * @param requiredKills Number of kills required to complete (or progress points for non-physical items)
 * @param orderIndex Display order within the chain (0-4 for each chain, determines unlock order)
 * @param rewards List of rewards given when quest is completed
 * @param isHardMode Whether this quest requires Hard mode kills
 */
public record NewMoonQuest(
        int id,
        String chainType,
        String description,
        String targetMobType,
        int requiredKills,
        int orderIndex,
        List<ItemStack> rewards,
        boolean isHardMode
) {
    /**
     * Creates a quest with the specified parameters.
     */
    public NewMoonQuest {
        if (id < 1 || id > 10) {
            throw new IllegalArgumentException("Quest ID must be between 1 and 10");
        }
        if (orderIndex < 0 || orderIndex > 4) {
            throw new IllegalArgumentException("Order index must be between 0 and 4");
        }
        if (requiredKills < 1) {
            throw new IllegalArgumentException("Required kills must be positive");
        }
        if (!chainType.equals("white") && !chainType.equals("black")) {
            throw new IllegalArgumentException("Chain type must be 'white' or 'black'");
        }
    }

    /**
     * Returns a formatted display string for this quest.
     */
    public String getDisplayText() {
        String hardSuffix = isHardMode ? " §c(Hard)" : "";
        return "§e" + description + " §7(" + requiredKills + " required)" + hardSuffix;
    }

    /**
     * Check if this is the portal unlock quest (quest 3 in each chain).
     */
    public boolean isPortalUnlockQuest() {
        return orderIndex == 2; // Third quest (0-indexed) unlocks portal
    }

    /**
     * Get the chain name for display.
     */
    public String getChainDisplayName() {
        return chainType.equals("white") ? "§f§lWhite Lord Chain" : "§5§lBlack Lord Chain";
    }
}
