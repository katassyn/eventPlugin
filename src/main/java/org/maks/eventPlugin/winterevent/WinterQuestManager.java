package org.maks.eventPlugin.winterevent;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Manages quests for the Winter Event.
 * Dual-chain system: Bear Chain (1-7) and Krampus Chain (8-14).
 */
public class WinterQuestManager {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;
    private final String eventId;

    // All 14 quests in the Winter Event
    private final List<WinterQuest> quests = new ArrayList<>();

    // Player quest progress: UUID -> Quest ID -> Progress count
    private final Map<UUID, Map<Integer, Integer>> playerProgress = new HashMap<>();

    // Player completed quests: UUID -> Set of completed quest IDs
    private final Map<UUID, Set<Integer>> completedQuests = new HashMap<>();

    // Player accepted quests: UUID -> Set of accepted quest IDs
    private final Map<UUID, Set<Integer>> acceptedQuests = new HashMap<>();

    // Player claimed rewards: UUID -> Set of claimed quest IDs
    private final Map<UUID, Set<Integer>> claimedRewards = new HashMap<>();

    public WinterQuestManager(JavaPlugin plugin, DatabaseManager database, ConfigManager config, String eventId) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.eventId = eventId;
        initializeQuests();
        loadAllProgress();
    }

    /**
     * Initialize all 14 quests from config.
     */
    private void initializeQuests() {
        Bukkit.getLogger().info("[Winter Event] Loading quests from config...");
        var questsSection = config.getSection("winter_event.summit.quests");

        if (questsSection == null) {
            Bukkit.getLogger().severe("[Winter Event] Quest section 'winter_event.summit.quests' not found in config!");
            return;
        }

        boolean enabled = questsSection.getBoolean("enabled", true);
        Bukkit.getLogger().info("[Winter Event] Quest system enabled: " + enabled);

        if (!enabled) {
            Bukkit.getLogger().warning("[Winter Event] Quest system is disabled");
            return;
        }

        var keys = questsSection.getKeys(false);
        Bukkit.getLogger().info("[Winter Event] Found quest keys: " + keys);

        for (String questIdStr : keys) {
            if (questIdStr.equals("enabled")) continue;

            try {
                int questId = Integer.parseInt(questIdStr);
                var questSection = questsSection.getConfigurationSection(questIdStr);
                if (questSection == null) {
                    Bukkit.getLogger().warning("[Winter Event] Quest section for ID " + questId + " is null");
                    continue;
                }

                String description = questSection.getString("description", "Unknown Quest");
                String chain = questSection.getString("chain", "bear");
                String targetMob = questSection.getString("target_mob", "");
                int requiredKills = questSection.getInt("required_kills", 1);
                int orderIndex = questSection.getInt("order_index", 0);
                boolean isBloodOnly = questSection.getBoolean("is_blood_only", false);
                boolean isCollection = questSection.getBoolean("is_collection", false);

                Bukkit.getLogger().info("[Winter Event] Loading quest " + questId + ": " + description + " (chain: " + chain + ")");

                // Load rewards from database
                List<ItemStack> rewards = loadRewardsFromDatabase(questId);

                quests.add(new WinterQuest(questId, chain, description, targetMob, requiredKills,
                    orderIndex, rewards, isBloodOnly, isCollection));
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("[Winter Event] Skipping non-numeric quest key: " + questIdStr);
            }
        }

        // Sort by ID
        quests.sort(Comparator.comparingInt(WinterQuest::id));
        Bukkit.getLogger().info("[Winter Event] Successfully loaded " + quests.size() + " quests");
    }

    /**
     * Get all quests.
     */
    public List<WinterQuest> getAllQuests() {
        return new ArrayList<>(quests);
    }

    /**
     * Get a quest by ID.
     */
    public WinterQuest getQuest(int questId) {
        return quests.stream()
                .filter(q -> q.id() == questId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all quests for a specific chain.
     */
    public List<WinterQuest> getQuestsForChain(String chainType) {
        return quests.stream()
                .filter(q -> q.chainType().equals(chainType))
                .sorted(Comparator.comparingInt(WinterQuest::orderIndex))
                .toList();
    }

    /**
     * Check if a quest is unlocked for a player.
     * First quest in each chain (orderIndex 0) is always unlocked.
     * Other quests require previous quest in SAME CHAIN to be completed AND claimed.
     */
    public boolean isQuestUnlocked(UUID playerId, int questId) {
        WinterQuest quest = getQuest(questId);
        if (quest == null) return false;

        // First quest in each chain is always unlocked
        if (quest.orderIndex() == 0) {
            return true;
        }

        // Find previous quest in the same chain
        int previousQuestId = getPreviousQuestInChain(questId);
        if (previousQuestId == -1) {
            return true; // Fallback: unlock if no previous quest found
        }

        // Previous quest must be both completed AND claimed
        return isQuestCompleted(playerId, previousQuestId) &&
               hasClaimedReward(playerId, previousQuestId);
    }

    /**
     * Get the previous quest ID in the same chain.
     */
    private int getPreviousQuestInChain(int questId) {
        WinterQuest currentQuest = getQuest(questId);
        if (currentQuest == null) return -1;

        for (WinterQuest q : quests) {
            if (q.chainType().equals(currentQuest.chainType()) &&
                q.orderIndex() == currentQuest.orderIndex() - 1) {
                return q.id();
            }
        }
        return -1;
    }

    /**
     * Check if a quest is accepted by a player.
     */
    public boolean isQuestAccepted(UUID playerId, int questId) {
        Set<Integer> accepted = acceptedQuests.get(playerId);
        return accepted != null && accepted.contains(questId);
    }

    /**
     * Check if a quest is completed by a player.
     */
    public boolean isQuestCompleted(UUID playerId, int questId) {
        Set<Integer> completed = completedQuests.get(playerId);
        return completed != null && completed.contains(questId);
    }

    /**
     * Check if a player has claimed rewards for a quest.
     */
    public boolean hasClaimedReward(UUID playerId, int questId) {
        Set<Integer> claimed = claimedRewards.get(playerId);
        return claimed != null && claimed.contains(questId);
    }

    /**
     * Get quest progress for a player.
     */
    public int getQuestProgress(UUID playerId, int questId) {
        Map<Integer, Integer> progress = playerProgress.get(playerId);
        if (progress == null) return 0;
        return progress.getOrDefault(questId, 0);
    }

    /**
     * Add progress to all matching quests for a player.
     * Returns true if any quest was completed.
     */
    public boolean addQuestProgress(UUID playerId, String targetType, int amount, boolean isBloodMode) {
        boolean anyCompleted = false;

        for (WinterQuest quest : quests) {
            // Skip if not matching target
            if (!quest.targetMobType().equalsIgnoreCase(targetType)) continue;

            // Skip if not unlocked or not accepted
            if (!isQuestUnlocked(playerId, quest.id())) continue;
            if (!isQuestAccepted(playerId, quest.id())) continue;

            // Skip if already completed
            if (isQuestCompleted(playerId, quest.id())) continue;

            // Blood-only quests check
            if (quest.isBloodOnly() && !isBloodMode) continue;

            // Add progress
            Map<Integer, Integer> progress = playerProgress.computeIfAbsent(playerId, k -> new HashMap<>());
            int current = progress.getOrDefault(quest.id(), 0);
            int newProgress = Math.min(current + amount, quest.requiredKills());
            progress.put(quest.id(), newProgress);

            // Save to database
            saveProgress(playerId, quest.id(), newProgress);

            // Check completion
            if (newProgress >= quest.requiredKills()) {
                completeQuest(playerId, quest.id());
                anyCompleted = true;
            }
        }

        return anyCompleted;
    }

    /**
     * Accept a quest.
     */
    public boolean acceptQuest(UUID playerId, int questId) {
        if (!isQuestUnlocked(playerId, questId)) return false;
        if (isQuestAccepted(playerId, questId)) return false;
        if (isQuestCompleted(playerId, questId)) return false;

        Set<Integer> accepted = acceptedQuests.computeIfAbsent(playerId, k -> new HashSet<>());
        accepted.add(questId);
        saveAcceptance(playerId, questId);
        return true;
    }

    /**
     * Mark a quest as completed.
     */
    private void completeQuest(UUID playerId, int questId) {
        Set<Integer> completed = completedQuests.computeIfAbsent(playerId, k -> new HashSet<>());
        completed.add(questId);
        saveCompletion(playerId, questId);
    }

    /**
     * Claim rewards for a quest.
     */
    public boolean claimReward(UUID playerId, int questId) {
        if (!isQuestCompleted(playerId, questId)) return false;
        if (hasClaimedReward(playerId, questId)) return false;

        Set<Integer> claimed = claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>());
        claimed.add(questId);
        saveClaim(playerId, questId);
        return true;
    }

    /**
     * Check if Bear chain is completed (all quests 1-7 completed & claimed).
     */
    public boolean hasBearChainCompleted(UUID playerId) {
        for (int questId = 1; questId <= 7; questId++) {
            if (!isQuestCompleted(playerId, questId) || !hasClaimedReward(playerId, questId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if Krampus chain is completed (all quests 8-14 completed & claimed).
     */
    public boolean hasKrampusChainCompleted(UUID playerId) {
        for (int questId = 8; questId <= 14; questId++) {
            if (!isQuestCompleted(playerId, questId) || !hasClaimedReward(playerId, questId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if both chains are completed.
     */
    public boolean hasBothChainsCompleted(UUID playerId) {
        return hasBearChainCompleted(playerId) && hasKrampusChainCompleted(playerId);
    }

    // ===== ADMIN METHODS =====

    /**
     * Add a reward to a quest.
     */
    public void addQuestReward(int questId, ItemStack item) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO winter_event_quest_rewards(event_id, quest_id, item) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setInt(2, questId);
            ps.setString(3, serializeItem(item));
            ps.executeUpdate();

            // Update quest in memory
            WinterQuest quest = getQuest(questId);
            if (quest != null) {
                quest.rewards().add(item);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to add quest reward: " + e.getMessage());
        }
    }

    /**
     * Clear all rewards for a quest.
     */
    public void clearQuestRewards(int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "DELETE FROM winter_event_quest_rewards WHERE event_id=? AND quest_id=?")) {
            ps.setString(1, eventId);
            ps.setInt(2, questId);
            ps.executeUpdate();

            // Update quest in memory
            WinterQuest quest = getQuest(questId);
            if (quest != null) {
                quest.rewards().clear();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to clear quest rewards: " + e.getMessage());
        }
    }

    /**
     * Get rewards for a quest.
     */
    public List<ItemStack> getQuestRewards(int questId) {
        WinterQuest quest = getQuest(questId);
        return quest != null ? new ArrayList<>(quest.rewards()) : new ArrayList<>();
    }

    // ===== DATABASE PERSISTENCE =====

    private void loadAllProgress() {
        loadProgress();
        loadCompleted();
        loadAccepted();
        loadClaimed();
    }

    private void loadProgress() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT player_uuid, quest_id, progress FROM winter_event_quest_progress WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);
                    int progress = rs.getInt(3);
                    playerProgress.computeIfAbsent(playerId, k -> new HashMap<>()).put(questId, progress);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to load quest progress: " + e.getMessage());
        }
    }

    private void loadCompleted() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT player_uuid, quest_id FROM winter_event_quest_completed WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);
                    completedQuests.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to load completed quests: " + e.getMessage());
        }
    }

    private void loadAccepted() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT player_uuid, quest_id FROM winter_event_quest_accepted WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);
                    acceptedQuests.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to load accepted quests: " + e.getMessage());
        }
    }

    private void loadClaimed() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT player_uuid, quest_id FROM winter_event_quest_claimed WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);
                    claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to load claimed rewards: " + e.getMessage());
        }
    }

    private void saveProgress(UUID playerId, int questId, int progress) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "REPLACE INTO winter_event_quest_progress(event_id, player_uuid, quest_id, progress) VALUES (?,?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.setInt(4, progress);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to save quest progress: " + e.getMessage());
        }
    }

    private void saveCompletion(UUID playerId, int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "REPLACE INTO winter_event_quest_completed(event_id, player_uuid, quest_id) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to save quest completion: " + e.getMessage());
        }
    }

    private void saveAcceptance(UUID playerId, int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "REPLACE INTO winter_event_quest_accepted(event_id, player_uuid, quest_id) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to save quest acceptance: " + e.getMessage());
        }
    }

    private void saveClaim(UUID playerId, int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "REPLACE INTO winter_event_quest_claimed(event_id, player_uuid, quest_id) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to save reward claim: " + e.getMessage());
        }
    }

    private List<ItemStack> loadRewardsFromDatabase(int questId) {
        List<ItemStack> rewards = new ArrayList<>();
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT item FROM winter_event_quest_rewards WHERE event_id=? AND quest_id=?")) {
            ps.setString(1, eventId);
            ps.setInt(2, questId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String serialized = rs.getString(1);
                    ItemStack item = deserializeItem(serialized);
                    if (item != null) {
                        rewards.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[Winter Event] Failed to load quest rewards: " + e.getMessage());
        }
        return rewards;
    }

    // ===== ITEM SERIALIZATION =====

    private String serializeItem(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack deserializeItem(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Winter Event] Failed to deserialize item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cleanup in-memory data (called when event stops).
     */
    public void cleanup() {
        playerProgress.clear();
        completedQuests.clear();
        acceptedQuests.clear();
        claimedRewards.clear();
    }
}
