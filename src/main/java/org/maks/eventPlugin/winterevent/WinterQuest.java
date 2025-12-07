package org.maks.eventPlugin.winterevent;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Represents a Winter Event quest.
 * Based on New Moon dual-chain architecture.
 */
public record WinterQuest(
    int id,                      // 1-14 (1-7 Bear, 8-14 Krampus)
    String chainType,            // "bear" or "krampus"
    String description,          // "Kill 800 Sugar Goblin Miners"
    String targetMobType,        // "sugar_goblin_miner" or "candy_cane" (for collection)
    int requiredKills,           // Kill count or collection amount
    int orderIndex,              // 0-6 within each chain
    List<ItemStack> rewards,     // Admin-configurable rewards
    boolean isBloodOnly,         // true for quests 7 & 14
    boolean isCollectionQuest    // true for quests 3 & 10
) {
    // Validation
    public WinterQuest {
        if (id < 1 || id > 14) {
            throw new IllegalArgumentException("Quest ID must be 1-14");
        }
        if (!chainType.equals("bear") && !chainType.equals("krampus")) {
            throw new IllegalArgumentException("Chain type must be 'bear' or 'krampus'");
        }
        if (orderIndex < 0 || orderIndex > 6) {
            throw new IllegalArgumentException("Order index must be 0-6");
        }
    }

    // Helper methods
    public boolean isPortalUnlockQuest() {
        return false; // Winter Event has no portal unlocks
    }

    public String getChainDisplayName() {
        return chainType.equals("bear") ? "§6§lBear Chain" : "§5§lKrampus Chain";
    }
}
