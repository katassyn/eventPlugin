package org.maks.eventPlugin.fullmoon;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Represents a quest in the Full Moon event.
 *
 * @param id Quest ID (1-6)
 * @param description Quest description shown to players
 * @param targetMobType MythicMobs internal name (e.g., "werewolf_normal", "amarok_normal")
 * @param requiredKills Number of kills required to complete
 * @param orderIndex Order in which quest unlocks (sequential)
 * @param rewards List of rewards given when quest is completed
 */
public record Quest(
        int id,
        String description,
        String targetMobType,
        int requiredKills,
        int orderIndex,
        List<ItemStack> rewards
) {
    /**
     * Creates a quest with the specified parameters.
     */
    public Quest {
        if (id < 1 || id > 6) {
            throw new IllegalArgumentException("Quest ID must be between 1 and 6");
        }
        if (orderIndex < 0) {
            throw new IllegalArgumentException("Order index must be non-negative");
        }
        if (requiredKills < 1) {
            throw new IllegalArgumentException("Required kills must be positive");
        }
    }

    /**
     * Returns a formatted display string for this quest.
     */
    public String getDisplayText() {
        return "§e" + description + " §7(" + requiredKills + " kills)";
    }
}
