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
import java.time.Instant;

public class EventManager {
    private final DatabaseManager database;
    private final String eventId;
    private org.maks.eventPlugin.config.ConfigManager configManager;
    private boolean active;
    private int maxProgress;
    private String name;
    private String description;
    private long endTime;
    private final Map<UUID, Integer> progressMap = new HashMap<>();
    private final Map<UUID, java.util.Set<Integer>> claimedMap = new HashMap<>();
    private final List<Reward> rewards = new ArrayList<>();
    private Map<Integer, Double> dropChances = new HashMap<>();


    public EventManager(DatabaseManager database, String eventId) {
        this.database = database;
        this.eventId = eventId;
        loadEvent();
        loadRewards();
        loadProgress();
    }

    // +++ POCZĄTEK MODYFIKACJI +++
    public String getEventId() {
        return eventId;
    }
    // +++ KONIEC MODYFIKACJI +++

    public void setConfigManager(org.maks.eventPlugin.config.ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void setDropChances(Map<Integer, Double> chances) {
        this.dropChances = chances;
    }

    public int getRandomProgress() {
        if (dropChances == null || dropChances.isEmpty()) {
            return java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 6);
        }
        double rnd = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;
        for (var entry : dropChances.entrySet()) {
            cumulative += entry.getValue();
            if (rnd <= cumulative) return entry.getKey();
        }
        return 0;
    }

    public boolean isActive() {
        return active;
    }

    public void toggle() {
        active = !active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMaxProgress(int max) {
        this.maxProgress = max;
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

        // Notify player when they cross a reward threshold
        for (Reward reward : rewards) {
            if (current < reward.requiredProgress() && newProgress >= reward.requiredProgress()) {
                String title = "Reward unlocked!";
                String sub = "For " + reward.requiredProgress() + " progress";
                player.sendTitle(title, sub, 10, 60, 10);
            }
        }
    }

    public void addReward(int required, ItemStack item) {
        rewards.add(new Reward(required, item));
        saveReward(required, item);
    }

    public void setRewards(List<Reward> newRewards) {
        clearRewards();
        for (Reward r : newRewards) {
            addReward(r.requiredProgress(), r.item());
        }
    }

    private void clearRewards() {
        rewards.clear();
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("DELETE FROM event_rewards WHERE event_id=?")) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    /**
     * Check if the player already claimed the reward for the given required progress.
     */
    public boolean hasClaimed(Player player, int required) {
        var set = claimedMap.get(player.getUniqueId());
        return set != null && set.contains(required);
    }

    public boolean claimReward(Player player, int required) {
        int progress = getProgress(player);
        if (progress < required) return false;
        var set = claimedMap.computeIfAbsent(player.getUniqueId(), k -> new java.util.HashSet<>());
        if (set.contains(required)) return false;
        set.add(required);
        saveClaimed(player.getUniqueId(), required);

        // Give player all rewards for this required progress
        for (var reward : rewards) {
            if (reward.requiredProgress() == required) {
                player.getInventory().addItem(reward.item().clone());
            }
        }
        return true;
    }

    private void loadProgress() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, progress FROM event_progress WHERE event_id=?")) {
            ps.setString(1, eventId);

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
             var ps = conn.prepareStatement("SELECT player_uuid, reward FROM event_claimed WHERE event_id=?")) {
            ps.setString(1, eventId);

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
             var ps = conn.prepareStatement("REPLACE INTO event_progress(event_id, player_uuid, progress) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, uuid.toString());
            ps.setInt(3, progress);

            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void loadRewards() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT required, item FROM event_rewards WHERE event_id=?")) {
            ps.setString(1, eventId);

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
             var ps = conn.prepareStatement("INSERT INTO event_rewards(event_id, required, item) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setInt(2, required);
            ps.setString(3, data);

            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void saveClaimed(UUID uuid, int reward) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO event_claimed(event_id, player_uuid, reward) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, uuid.toString());
            ps.setInt(3, reward);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    /* Load basic event info from database */
    private void loadEvent() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT name, description, end_time, max_progress, active FROM events WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString(1);
                    description = rs.getString(2);
                    endTime = rs.getLong(3);
                    maxProgress = rs.getInt(4);
                    active = rs.getBoolean(5);
                } else {
                    // defaults if not defined
                    name = eventId;
                    description = "";
                    endTime = 0L;
                    maxProgress = 25000;
                    active = false;
                    saveEvent();
                }
            }
        } catch (SQLException ignored) {
        }
    }

    private void saveEvent() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO events(event_id, name, description, end_time, max_progress, active) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setLong(4, endTime);
            ps.setInt(5, maxProgress);
            ps.setBoolean(6, active);

            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void resetProgress() {
        progressMap.clear();
        claimedMap.clear();
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("DELETE FROM event_progress WHERE event_id=?")) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("DELETE FROM event_claimed WHERE event_id=?")) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void start(String name, String description, int maxProgress, long durationSeconds) {
        resetProgress();
        this.name = name;
        this.description = description;
        this.maxProgress = maxProgress;
        this.endTime = durationSeconds > 0 ? Instant.now().plusSeconds(durationSeconds).toEpochMilli() : 0L;
        this.active = true;
        saveEvent();
    }

    public void stop() {
        this.active = false;
        this.endTime = 0L;
        resetProgress();
        saveEvent();
        if (configManager != null) {
            configManager.set("events." + eventId + ".active", false);
        }
    }

    public void checkExpiry() {
        if (active && endTime > 0 && Instant.now().toEpochMilli() >= endTime) {
            stop();
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getTimeRemaining() {
        return Math.max(0, endTime - Instant.now().toEpochMilli());
    }

}