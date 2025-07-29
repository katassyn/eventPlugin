package org.maks.eventPlugin.eventsystem;

import org.bukkit.inventory.ItemStack;

public record Reward(int requiredProgress, ItemStack item) {
}
