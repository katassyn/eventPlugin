package org.maks.eventPlugin.fullmoon.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Helper class for integrating with IngredientPouchPlugin API.
 * Uses reflection to avoid hard dependencies.
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
     * Get the quantity of an item in a player's pouch.
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
     * Check if a player has at least the specified amount of an item.
     *
     * @param player The player
     * @param itemId The item ID
     * @param requiredAmount The required amount
     * @return True if player has enough
     */
    public static boolean hasEnough(Player player, String itemId, int requiredAmount) {
        return getItemQuantity(player, itemId) >= requiredAmount;
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
     * Consume items from a player's pouch.
     *
     * @param player The player
     * @param itemId The item ID
     * @param amount The amount to consume (positive number)
     * @return True if successful, false if not enough items
     */
    public static boolean consumeItem(Player player, String itemId, int amount) {
        if (!apiAvailable) {
            player.sendMessage("§c§l[Full Moon] §cIngredientPouch plugin is not available!");
            return false;
        }

        if (!hasEnough(player, itemId, amount)) {
            return false;
        }

        try {
            Method updateItemQuantityMethod = pouchAPI.getClass()
                    .getMethod("updateItemQuantity", String.class, String.class, int.class);
            Object result = updateItemQuantityMethod.invoke(
                    pouchAPI,
                    player.getUniqueId().toString(),
                    itemId,
                    -amount // Negative to remove
            );
            return (Boolean) result;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[EventPlugin] Failed to consume item: " + e.getMessage());
            return false;
        }
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
}
