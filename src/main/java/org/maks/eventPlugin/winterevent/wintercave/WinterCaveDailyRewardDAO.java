package org.maks.eventPlugin.winterevent.wintercave;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.util.ItemUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Data Access Object for Winter Cave daily rewards.
 * Handles database operations for rewards and claims.
 */
public class WinterCaveDailyRewardDAO {
    private final DatabaseManager database;
    private final String eventId;

    public WinterCaveDailyRewardDAO(DatabaseManager database, String eventId) {
        this.database = database;
        this.eventId = eventId;
    }

    /**
     * Set reward for a specific day (1-30).
     */
    public void setDayReward(int day, ItemStack item) {
        String sql = "REPLACE INTO winter_cave_daily_rewards (event_id, day, item) VALUES (?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setInt(2, day);
            ps.setString(3, ItemUtil.serialize(item));
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to set day reward: " + e.getMessage());
        }
    }

    /**
     * Get reward for a specific day. Returns null if not set.
     */
    public ItemStack getDayReward(int day) {
        String sql = "SELECT item FROM winter_cave_daily_rewards WHERE event_id = ? AND day = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setInt(2, day);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String itemData = rs.getString("item");
                return ItemUtil.deserialize(itemData);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to get day reward: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if player has claimed reward for this event day.
     */
    public boolean hasClaimed(UUID playerId, int eventDay) {
        String sql = "SELECT 1 FROM winter_cave_claims WHERE event_id = ? AND player_uuid = ? AND event_day = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, eventDay);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to check claim status: " + e.getMessage());
        }
        return false;
    }

    /**
     * Record that player claimed reward for this event day.
     */
    public void recordClaim(UUID playerId, int eventDay) {
        String sql = "INSERT INTO winter_cave_claims (event_id, player_uuid, event_day, claimed_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, eventDay);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to record claim: " + e.getMessage());
        }
    }

    /**
     * Clear all rewards (admin reset).
     */
    public void clearAllRewards() {
        String sql = "DELETE FROM winter_cave_daily_rewards WHERE event_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to clear rewards: " + e.getMessage());
        }
    }

    /**
     * Clear all claims (admin reset).
     */
    public void clearAllClaims() {
        String sql = "DELETE FROM winter_cave_claims WHERE event_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Cave] Failed to clear claims: " + e.getMessage());
        }
    }
}
