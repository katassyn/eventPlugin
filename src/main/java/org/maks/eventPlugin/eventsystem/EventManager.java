package org.maks.eventPlugin.eventsystem;

import org.bukkit.entity.Player;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.util.ItemUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;

public class EventManager {
    private final DatabaseManager database;
    private boolean active = false;
    private final int maxProgress = 25000; // hard-coded max progress
    private final Map<UUID, Integer> progressMap = new HashMap<>();
    private final Map<UUID, java.util.Set<Integer>> claimedMap = new HashMap<>();
    private final List<Reward> rewards = new ArrayList<>();

    public EventManager(DatabaseManager database) {
        this.database = database;
        loadRewards();
        loadProgress();
    }
    public boolean isActive() {
        return active;
    }

    public void toggle() {
        active = !active;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public int getProgress(Player player) {
        return progressMap.getOrDefault(player.getUniqueId(), 0);
    }

    public void addProgress(Player player, int amount, double multiplier) {
        int current = progressMap.getOrDefault(player.getUniqueId(), 0);
        int newProgress = current + (int) Math.round(amount * multiplier);
        if (newProgress > maxProgress) newProgress = maxProgress;
        progressMap.put(player.getUniqueId(), newProgress);
        saveProgress(player.getUniqueId(), newProgress);
    }

    public void addReward(int required, ItemStack item) {
        rewards.add(new Reward(required, item));
        saveReward(required, item);
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public boolean claimReward(Player player, int required) {
        int progress = getProgress(player);
        if (progress < required) return false;
        var set = claimedMap.computeIfAbsent(player.getUniqueId(), k -> new java.util.HashSet<>());
        if (set.contains(required)) return false;
        set.add(required);
        saveClaimed(player.getUniqueId(), required);
        return true;
    }

    private void loadProgress() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, progress FROM event_progress")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    int prog = rs.getInt(2);
                    progressMap.put(id, prog);
                }
            }
        } catch (SQLException ignored) {
        }
        // load claimed rewards
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, reward FROM event_claimed")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    int rew = rs.getInt(2);
                    claimedMap.computeIfAbsent(id, k -> new java.util.HashSet<>()).add(rew);
                }
            }
        } catch (SQLException ignored) {
        }
    }

    private void saveProgress(UUID uuid, int progress) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO event_progress(player_uuid, progress) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, progress);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void loadRewards() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT required, item FROM event_rewards")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int req = rs.getInt(1);
                    String data = rs.getString(2);
                    ItemStack item = ItemUtil.deserialize(data);
                    if (item != null)
                        rewards.add(new Reward(req, item));
                }
            }
        } catch (SQLException ignored) {
        }
    }

    private void saveReward(int required, ItemStack item) {
        String data = ItemUtil.serialize(item);
        if (data == null) return;
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO event_rewards(required, item) VALUES (?,?)")) {
            ps.setInt(1, required);
            ps.setString(2, data);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void saveClaimed(UUID uuid, int reward) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO event_claimed(player_uuid, reward) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, reward);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
