package org.maks.eventPlugin.fullmoon.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * Helper class for integrating with IngredientPouchPlugin API.
 * Uses reflection to avoid hard dependencies.
 * Checks BOTH player inventory AND pouch for items.
 */
public class PouchHelper {

    private static Object pouchAPI = null;
    private static boolean apiAvailable = false;
    private static boolean initialized = false;

    /**
     * Initialize the API connection.
     * Should be called during plugin startup.
     */
    public static void initialize() {
        if (initialized) return;

        try {
            Object ingredientPouchPlugin = Bukkit.getPluginManager().getPlugin("IngredientPouchPlugin");
            if (ingredientPouchPlugin != null) {
                Method getAPIMethod = ingredientPouchPlugin.getClass().getMethod("getAPI");
                pouchAPI = getAPIMethod.invoke(ingredientPouchPlugin);
                apiAvailable = true;
                Bukkit.getLogger().info("[EventPlugin] IngredientPouchPlugin API connected successfully");
            } else {
                Bukkit.getLogger().warning("[EventPlugin] IngredientPouchPlugin not found - pouch features disabled");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[EventPlugin] Failed to connect to IngredientPouchPlugin API: " + e.getMessage());
            apiAvailable = false;
        }

        initialized = true;
    }

    /**
     * Check if the API is available.
     */
    public static boolean isAvailable() {
        return apiAvailable;
    }

    /**
     * Get the quantity of an item in a player's pouch ONLY.
     *
     * @param player The player
     * @param itemId The item ID (e.g., "ips", "blood_vial")
     * @return The quantity, or 0 if API unavailable
     */
    public static int getItemQuantity(Player player, String itemId) {
        if (!apiAvailable) return 0;

        try {
            Method getItemQuantityMethod = pouchAPI.getClass()
                    .getMethod("getItemQuantity", String.class, String.class);
            Object result = getItemQuantityMethod.invoke(pouchAPI, player.getUniqueId().toString(), itemId);
            return (Integer) result;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[EventPlugin] Failed to get item quantity: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get the total quantity of an item from BOTH inventory AND pouch.
     *
     * @param player The player
     * @param itemId The item ID (e.g., "ips", "blood_vial")
     * @return The total quantity
     */
    public static int getTotalItemQuantity(Player player, String itemId) {
        int total = 0;

        // Check pouch
        if (apiAvailable) {
            total += getItemQuantity(player, itemId);
        }

        // Check inventory
        total += getItemQuantityInInventory(player, itemId);

        return total;
    }

    /**
     * Check if a player has at least the specified amount of an item.
     * Checks BOTH inventory AND pouch.
     *
     * @param player The player
     * @param itemId The item ID
     * @param requiredAmount The required amount
     * @return True if player has enough
     */
    public static boolean hasEnough(Player player, String itemId, int requiredAmount) {
        int totalAmount = 0;

        // Check pouch first
        if (apiAvailable) {
            totalAmount += getItemQuantity(player, itemId);
        }

        // Check inventory
        totalAmount += getItemQuantityInInventory(player, itemId);

        return totalAmount >= requiredAmount;
    }

    /**
     * Check if a player has at least 15 IPS (for Hard mode requirement).
     *
     * @param player The player
     * @return True if player has 15+ IPS
     */
    public static boolean hasEnoughIPS(Player player) {
        return hasEnough(player, "ips", 15);
    }

    /**
     * Consume items from a player's inventory and/or pouch.
     * Tries POUCH first, then INVENTORY.
     *
     * @param player The player
     * @param itemId The item ID
     * @param amount The amount to consume (positive number)
     * @return True if successful, false if not enough items
     */
    public static boolean consumeItem(Player player, String itemId, int amount) {
        if (!hasEnough(player, itemId, amount)) {
            return false;
        }

        int remaining = amount;

        // --- START FIX: Consume from Pouch FIRST ---
        if (apiAvailable) {
            int pouchQuantity = getItemQuantity(player, itemId);
            int toRemoveFromPouch = Math.min(remaining, pouchQuantity);

            if (toRemoveFromPouch > 0) {
                try {
                    Method updateItemQuantityMethod = pouchAPI.getClass()
                            .getMethod("updateItemQuantity", String.class, String.class, int.class);
                    Object result = updateItemQuantityMethod.invoke(
                            pouchAPI,
                            player.getUniqueId().toString(),
                            itemId,
                            -toRemoveFromPouch // Negative to remove
                    );
                    boolean success = (Boolean) result;

                    if (success) {
                        remaining -= toRemoveFromPouch;
                    } else {
                        Bukkit.getLogger().warning("[EventPlugin] Failed to remove " + toRemoveFromPouch + " " + itemId + " from pouch for player " + player.getName());
                        // Nie zwracaj false, spróbuj jeszcze ekwipunek
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[EventPlugin] Failed to consume item from pouch: " + e.getMessage());
                    // Nie zwracaj false, spróbuj jeszcze ekwipunek
                }
            }
        }
        // --- END FIX ---

        // Then consume from inventory if needed
        if (remaining > 0) {
            int consumedFromInventory = consumeItemFromInventory(player, itemId, remaining);
            remaining -= consumedFromInventory;
        }

        // Final check
        if (remaining > 0) {
            // To nie powinno się zdarzyć, jeśli hasEnough() działa poprawnie, ale jako zabezpieczenie
            Bukkit.getLogger().warning("[EventPlugin] Failed to consume full amount of " + itemId + " for " + player.getName() + ". Remaining: " + remaining);
            return false;
        }

        return true;
    }

    /**
     * Consume one Blood Vial from a player's pouch.
     *
     * @param player The player
     * @return True if successful, false if not enough vials
     */
    public static boolean consumeBloodVial(Player player) {
        return consumeItem(player, "blood_vial", 1);
    }

    /**
     * Get an ItemStack representing an item from the pouch system.
     *
     * @param itemId The item ID
     * @return The ItemStack, or null if not found
     */
    public static ItemStack getItemStack(String itemId) {
        if (!apiAvailable) return null;

        try {
            Method getItemMethod = pouchAPI.getClass()
                    .getMethod("getItem", String.class);
            Object result = getItemMethod.invoke(pouchAPI, itemId);
            return (ItemStack) result;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[EventPlugin] Failed to get item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Add items to a player's pouch.
     *
     * @param player The player
     * @param itemId The item ID
     * @param amount The amount to add
     * @return True if successful
     */
    public static boolean addItem(Player player, String itemId, int amount) {
        if (!apiAvailable) return false;

        try {
            Method updateItemQuantityMethod = pouchAPI.getClass()
                    .getMethod("updateItemQuantity", String.class, String.class, int.class);
            Object result = updateItemQuantityMethod.invoke(
                    pouchAPI,
                    player.getUniqueId().toString(),
                    itemId,
                    amount // Positive to add
            );
            return (Boolean) result;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[EventPlugin] Failed to add item: " + e.getMessage());
            return false;
        }
    }

    // ==================== INVENTORY HELPER METHODS ====================

    /**
     * Get the quantity of an item in the player's inventory.
     *
     * @param player The player
     * @param itemId The item ID (e.g., "ips", "blood_vial")
     * @return The quantity in inventory
     */
    private static int getItemQuantityInInventory(Player player, String itemId) {
        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            if (isItemMatch(item, itemId)) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Consume items from the player's inventory.
     *
     * @param player The player
     * @param itemId The item ID
     * @param maxAmount Maximum amount to consume
     * @return The actual amount consumed
     */
    private static int consumeItemFromInventory(Player player, String itemId, int maxAmount) {
        int remaining = maxAmount;

        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;

            if (isItemMatch(item, itemId)) {
                int amount = item.getAmount();
                if (amount <= remaining) {
                    // Remove entire stack
                    player.getInventory().setItem(i, null);
                    remaining -= amount;
                } else {
                    // Remove partial amount
                    item.setAmount(amount - remaining);
                    remaining = 0;
                }
            }
        }

        return maxAmount - remaining; // Return how much was actually consumed
    }

    /**
     * Check if an ItemStack matches a given item ID.
     *
     * @param item The ItemStack to check
     * @param itemId The item ID (e.g., "ips", "blood_vial")
     * @return True if the item matches
     */
    private static boolean isItemMatch(ItemStack item, String itemId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return false;
        }

        String displayName = meta.getDisplayName();

        // Match based on itemId
        switch (itemId.toLowerCase()) {
            case "ips":
                // Check for IPS: IRON_NUGGET with display name "§4Fragment of Infernal Passage"
                return item.getType() == Material.IRON_NUGGET &&
                        displayName.contains("Fragment of Infernal Passage");

            case "blood_vial":
                // Check for Blood Vial: BEETROOT_SOUP with display name "§4Blood Vial"
                return item.getType() == Material.BEETROOT_SOUP &&
                        displayName.contains("Blood Vial");

            default:
                // For other items, try to match by display name
                return displayName.toLowerCase().contains(itemId.toLowerCase());
        }
    }
}