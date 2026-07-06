package org.maks.eventPlugin.winterevent.bigpresent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.db.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class BigPresentManager {
    public static final String POUCH_ITEM_ID = "snow_flakes";

    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final String eventId;

    public BigPresentManager(JavaPlugin plugin, DatabaseManager database, String eventId) {
        this.plugin = plugin;
        this.database = database;
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public int getRequiredFlakes(BigPresentTier tier) {
        switch (tier) {
            case INFERNAL: return 500;
            case HELL: return 1000;
            case BLOOD: return 1500;
            default: return 0;
        }
    }

    // ===== Rewards Storage =====
    public List<ItemStack> loadRewards(BigPresentTier tier) {
        List<ItemStack> rewards = new ArrayList<>();
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT item FROM big_present_rewards WHERE event_id=? AND tier=?")) {
            ps.setString(1, eventId);
            ps.setString(2, tier.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String serialized = rs.getString(1);
                    ItemStack item = deserializeItem(serialized);
                    if (item != null) rewards.add(item);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to load Big Present rewards: " + e.getMessage());
        }
        return rewards;
    }

    public void saveRewards(BigPresentTier tier, List<ItemStack> rewards) {
        try (var conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try (var del = conn.prepareStatement("DELETE FROM big_present_rewards WHERE event_id=? AND tier=?");
                 var ins = conn.prepareStatement("INSERT INTO big_present_rewards(event_id, tier, item) VALUES (?,?,?)")) {
                del.setString(1, eventId);
                del.setString(2, tier.name());
                del.executeUpdate();

                for (ItemStack item : rewards) {
                    if (item == null) continue;
                    ins.setString(1, eventId);
                    ins.setString(2, tier.name());
                    ins.setString(3, serializeItem(item));
                    ins.addBatch();
                }
                ins.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to save Big Present rewards: " + e.getMessage());
        }
    }

    // ===== Opened Tracking =====
    public boolean isOpened(UUID playerId, BigPresentTier tier) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT 1 FROM big_present_opened WHERE event_id=? AND player_uuid=? AND tier=?")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setString(3, tier.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to check Big Present opened: " + e.getMessage());
            return true; // fail-safe: prevent duping
        }
    }

    public void markOpened(UUID playerId, BigPresentTier tier) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                     "REPLACE INTO big_present_opened(event_id, player_uuid, tier) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setString(3, tier.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to mark Big Present opened: " + e.getMessage());
        }
    }

    /**
     * Reset all Big Present open states for the current event id.
     * Call this when starting a new Winter Event run so players can open again.
     */
    public void resetOpenedForEvent() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("DELETE FROM big_present_opened WHERE event_id=?")) {
            ps.setString(1, eventId);
            int deleted = ps.executeUpdate();
            Bukkit.getLogger().info("[Winter Event] Reset Big Present opened states for event '" + eventId + "' (" + deleted + " rows)");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to reset Big Present opened states: " + e.getMessage());
        }
    }

    // ===== Inventory space check =====
    public int additionalSlotsNeeded(Player player, List<ItemStack> rewards) {
        // Clone current inventory contents to simulate stacking
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] sim = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            sim[i] = (contents[i] == null) ? null : contents[i].clone();
        }

        int emptySlots = 0;
        for (ItemStack s : sim) if (s == null) emptySlots++;
        int requiredNewStacks = 0;

        for (ItemStack reward : rewards) {
            if (reward == null) continue;
            ItemStack toAdd = reward.clone();
            int amount = toAdd.getAmount();
            int max = toAdd.getMaxStackSize();

            // Try to merge into existing stacks first
            for (int i = 0; i < sim.length && amount > 0; i++) {
                ItemStack slot = sim[i];
                if (slot == null) continue;
                if (slot.isSimilar(toAdd)) {
                    int free = slot.getMaxStackSize() - slot.getAmount();
                    if (free > 0) {
                        int move = Math.min(free, amount);
                        slot.setAmount(slot.getAmount() + move);
                        amount -= move;
                    }
                }
            }

            if (amount <= 0) continue;

            // Now we need new stacks
            while (amount > 0) {
                int thisStack = Math.min(max, amount);
                amount -= thisStack;
                if (emptySlots > 0) {
                    emptySlots--; // occupy a slot in simulation
                } else {
                    requiredNewStacks++;
                }
            }
        }

        return requiredNewStacks; // number of additional empty slots needed beyond current
    }

    // ===== Serialization helpers =====
    private String serializeItem(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack deserializeItem(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Winter Event] Failed to deserialize Big Present item: " + e.getMessage());
            return null;
        }
    }

    // ===== Messages =====
    public String buildInfoMessage(BigPresentTier tier) {
        int cost = getRequiredFlakes(tier);
        return "§b§lWinter Event §7» §fA great Christmas Present awaits the greatest savior who defeated the evil holiday spirit and recovered the stolen sweets. " +
                "Bring §b" + cost + " Snow Flakes§f to open it. §7(Each tier can be opened once per event)";
    }
}