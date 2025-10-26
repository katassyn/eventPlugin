package org.maks.eventPlugin.fullmoon;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Manages quests for the Full Moon event.
 * Handles quest progress tracking, unlocking, acceptance, and completion.
 */
public class QuestManager {
    private final DatabaseManager database;
    private final ConfigManager config;
    private final String eventId = "full_moon";

    // All quests in the Full Moon event
    private final List<Quest> quests = new ArrayList<>();

    // Player quest progress: UUID -> Quest ID -> Kill count
    private final Map<UUID, Map<Integer, Integer>> playerProgress = new HashMap<>();

    // Player completed quests: UUID -> Set of completed quest IDs
    private final Map<UUID, Set<Integer>> completedQuests = new HashMap<>();

    // Player accepted quests: UUID -> Set of accepted quest IDs
    private final Map<UUID, Set<Integer>> acceptedQuests = new HashMap<>();

    // Player claimed rewards: UUID -> Set of claimed quest IDs
    private final Map<UUID, Set<Integer>> claimedRewards = new HashMap<>();

    public QuestManager(DatabaseManager database, ConfigManager config) {
        this.database = database;
        this.config = config;
        initializeQuests();
        loadAllProgress();
    }

    /**
     * Initialize all quests for the Full Moon event from config.
     * Quests unlock sequentially based on orderIndex.
     * Rewards are loaded from database, not config.
     */
    private void initializeQuests() {
        var questsSection = config.getSection("full_moon.quests");
        if (questsSection == null) {
            // Fallback to hardcoded quests if config not found
            initializeDefaultQuests();
            return;
        }

        for (String questIdStr : questsSection.getKeys(false)) {
            int questId = Integer.parseInt(questIdStr);
            var questSection = questsSection.getConfigurationSection(questIdStr);
            if (questSection == null) continue;

            String description = questSection.getString("description", "Unknown Quest");
            String targetMob = questSection.getString("target_mob", "werewolf");
            int requiredKills = questSection.getInt("required_kills", 1);
            int orderIndex = questSection.getInt("order_index", 0);

            // Load rewards from database
            List<ItemStack> rewards = loadRewardsFromDatabase(questId);

            quests.add(new Quest(questId, description, targetMob, requiredKills, orderIndex, rewards));
        }

        // Sort by ID to ensure proper order
        quests.sort(Comparator.comparingInt(Quest::id));
    }

    /**
     * Fallback to default hardcoded quests if config is not available.
     */
    private void initializeDefaultQuests() {
        quests.add(new Quest(1, "Kill 100 Werewolves", "werewolf", 100, 0, new ArrayList<>()));
        quests.add(new Quest(2, "Defeat Amarok, First Werewolf", "amarok", 1, 1, new ArrayList<>()));
        quests.add(new Quest(3, "Kill 500 Werewolves", "werewolf", 500, 2, new ArrayList<>()));
        quests.add(new Quest(4, "Defeat Amarok 5 times", "amarok", 5, 3, new ArrayList<>()));
        quests.add(new Quest(5, "Defeat Sanguis the Blood Mage", "sanguis", 1, 4, new ArrayList<>()));
        quests.add(new Quest(6, "Defeat Sanguis 5 times (Hard)", "sanguis_hard", 5, 5, new ArrayList<>()));
    }

    /**
     * Get all quests.
     */
    public List<Quest> getAllQuests() {
        return new ArrayList<>(quests);
    }

    /**
     * Get a quest by ID.
     */
    public Quest getQuest(int questId) {
        return quests.stream()
                .filter(q -> q.id() == questId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a quest is unlocked for a player.
     * Quests unlock sequentially - previous quest must be completed AND claimed.
     */
    public boolean isQuestUnlocked(UUID playerId, int questId) {
        Quest quest = getQuest(questId);
        if (quest == null) return false;

        // First quest is always unlocked
        if (quest.orderIndex() == 0) return true;

        // Check if previous quest is completed AND rewards were claimed
        int previousQuestId = questId - 1;
        return isQuestCompleted(playerId, previousQuestId) && hasClaimedReward(playerId, previousQuestId);
    }

    /**
     * Check if a quest is accepted by a player.
     */
    public boolean isQuestAccepted(UUID playerId, int questId) {
        Set<Integer> accepted = acceptedQuests.get(playerId);
        return accepted != null && accepted.contains(questId);
    }

    /**
     * Accept a quest (player must click to start tracking).
     * Returns true if successfully accepted.
     */
    public boolean acceptQuest(UUID playerId, int questId) {
        // Check if quest is unlocked
        if (!isQuestUnlocked(playerId, questId)) {
            return false;
        }

        // Check if already accepted
        if (isQuestAccepted(playerId, questId)) {
            return false;
        }

        // Check if already completed
        if (isQuestCompleted(playerId, questId)) {
            return false;
        }

        // Accept the quest
        Set<Integer> accepted = acceptedQuests.computeIfAbsent(playerId, k -> new HashSet<>());
        accepted.add(questId);
        saveAcceptance(playerId, questId);
        return true;
    }

    /**
     * Check if player has claimed reward for a quest.
     */
    public boolean hasClaimedReward(UUID playerId, int questId) {
        Set<Integer> claimed = claimedRewards.get(playerId);
        return claimed != null && claimed.contains(questId);
    }

    /**
     * Claim reward for a completed quest.
     * Returns true if reward was successfully claimed.
     */
    public boolean claimReward(UUID playerId, int questId) {
        // Check if quest is completed
        if (!isQuestCompleted(playerId, questId)) {
            return false;
        }

        // Check if reward already claimed
        if (hasClaimedReward(playerId, questId)) {
            return false;
        }

        // Mark as claimed
        Set<Integer> claimed = claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>());
        claimed.add(questId);
        saveClaim(playerId, questId);
        return true;
    }

    /**
     * Check if a quest is completed by a player.
     */
    public boolean isQuestCompleted(UUID playerId, int questId) {
        Set<Integer> completed = completedQuests.get(playerId);
        return completed != null && completed.contains(questId);
    }

    /**
     * Get current progress for a quest.
     */
    public int getQuestProgress(UUID playerId, int questId) {
        Map<Integer, Integer> progress = playerProgress.get(playerId);
        if (progress == null) return 0;
        return progress.getOrDefault(questId, 0);
    }

    /**
     * Add progress to a quest. Returns true if quest was just completed.
     * IMPORTANT: Quest must be accepted first to gain progress!
     *
     * @param playerId Player UUID
     * @param mobType Base mob type (without _normal/_hard suffix)
     * @param amount Amount to add
     * @param isHard Whether this was a hard mode kill
     */
    public boolean addQuestProgress(UUID playerId, String mobType, int amount, boolean isHard) {
        boolean anyCompleted = false;

        // Find all quests matching this mob type that are accepted and not completed
        for (Quest quest : quests) {
            // For quests that require hard mode (target ends with _hard), check if this was hard mode
            String questTarget = quest.targetMobType();
            boolean matchesQuest = false;

            if (questTarget.endsWith("_hard")) {
                // Quest requires hard mode - check base type matches AND it was hard mode
                String baseQuestType = questTarget.substring(0, questTarget.length() - 5);
                matchesQuest = baseQuestType.equalsIgnoreCase(mobType) && isHard;
            } else {
                // Normal quest - just check mob type matches
                matchesQuest = questTarget.equalsIgnoreCase(mobType);
            }

            if (!matchesQuest) continue;
            if (!isQuestUnlocked(playerId, quest.id())) continue;
            if (!isQuestAccepted(playerId, quest.id())) continue; // Must be accepted!
            if (isQuestCompleted(playerId, quest.id())) continue;

            // Add progress
            Map<Integer, Integer> progress = playerProgress.computeIfAbsent(playerId, k -> new HashMap<>());
            int current = progress.getOrDefault(quest.id(), 0);
            int newProgress = Math.min(current + amount, quest.requiredKills());
            progress.put(quest.id(), newProgress);

            // Check if completed
            if (newProgress >= quest.requiredKills() && !isQuestCompleted(playerId, quest.id())) {
                completeQuest(playerId, quest.id());
                anyCompleted = true;
            }

            saveProgress(playerId, quest.id(), newProgress);
        }

        return anyCompleted;
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
     * Check if player has completed quest 4 (unlocks Map 2).
     */
    public boolean hasUnlockedMap2(UUID playerId) {
        return isQuestCompleted(playerId, 4);
    }

    /**
     * Reset all quest progress for a player (for event rerun).
     */
    public void resetPlayerProgress(UUID playerId) {
        playerProgress.remove(playerId);
        completedQuests.remove(playerId);
        acceptedQuests.remove(playerId);
        claimedRewards.remove(playerId);

        try (var conn = database.getConnection();
             var ps1 = conn.prepareStatement("DELETE FROM full_moon_quest_progress WHERE event_id=? AND player_uuid=?");
             var ps2 = conn.prepareStatement("DELETE FROM full_moon_quest_completed WHERE event_id=? AND player_uuid=?");
             var ps3 = conn.prepareStatement("DELETE FROM full_moon_quest_accepted WHERE event_id=? AND player_uuid=?");
             var ps4 = conn.prepareStatement("DELETE FROM full_moon_quest_claimed WHERE event_id=? AND player_uuid=?")) {

            ps1.setString(1, eventId);
            ps1.setString(2, playerId.toString());
            ps1.executeUpdate();

            ps2.setString(1, eventId);
            ps2.setString(2, playerId.toString());
            ps2.executeUpdate();

            ps3.setString(1, eventId);
            ps3.setString(2, playerId.toString());
            ps3.executeUpdate();

            ps4.setString(1, eventId);
            ps4.setString(2, playerId.toString());
            ps4.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reset all quest progress for all players (for event rerun).
     */
    public void resetAllProgress() {
        playerProgress.clear();
        completedQuests.clear();
        acceptedQuests.clear();
        claimedRewards.clear();

        try (var conn = database.getConnection();
             var ps1 = conn.prepareStatement("DELETE FROM full_moon_quest_progress WHERE event_id=?");
             var ps2 = conn.prepareStatement("DELETE FROM full_moon_quest_completed WHERE event_id=?");
             var ps3 = conn.prepareStatement("DELETE FROM full_moon_quest_accepted WHERE event_id=?");
             var ps4 = conn.prepareStatement("DELETE FROM full_moon_quest_claimed WHERE event_id=?")) {

            ps1.setString(1, eventId);
            ps1.executeUpdate();

            ps2.setString(1, eventId);
            ps2.executeUpdate();

            ps3.setString(1, eventId);
            ps3.executeUpdate();

            ps4.setString(1, eventId);
            ps4.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== QUEST REWARDS MANAGEMENT ====================

    /**
     * Load rewards for a specific quest from database.
     */
    private List<ItemStack> loadRewardsFromDatabase(int questId) {
        List<ItemStack> rewards = new ArrayList<>();
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT item FROM full_moon_quest_rewards WHERE event_id=? AND quest_id=?")) {
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
            e.printStackTrace();
        }
        return rewards;
    }

    /**
     * Get all rewards for a specific quest (refreshed from database).
     */
    public List<ItemStack> getQuestRewards(int questId) {
        return loadRewardsFromDatabase(questId);
    }

    /**
     * Add a reward to a quest.
     */
    public void addQuestReward(int questId, ItemStack item) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("INSERT INTO full_moon_quest_rewards(event_id, quest_id, item) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setInt(2, questId);
            ps.setString(3, serializeItem(item));
            ps.executeUpdate();

            // Refresh quest rewards in memory
            refreshQuestRewards(questId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove all rewards for a quest.
     */
    public void clearQuestRewards(int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("DELETE FROM full_moon_quest_rewards WHERE event_id=? AND quest_id=?")) {
            ps.setString(1, eventId);
            ps.setInt(2, questId);
            ps.executeUpdate();

            // Refresh quest rewards in memory
            refreshQuestRewards(questId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Refresh rewards for a specific quest from database.
     */
    private void refreshQuestRewards(int questId) {
        Quest quest = getQuest(questId);
        if (quest == null) return;

        List<ItemStack> newRewards = loadRewardsFromDatabase(questId);

        // Create new quest with updated rewards
        Quest updatedQuest = new Quest(
                quest.id(),
                quest.description(),
                quest.targetMobType(),
                quest.requiredKills(),
                quest.orderIndex(),
                newRewards
        );

        // Replace in list
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id() == questId) {
                quests.set(i, updatedQuest);
                break;
            }
        }
    }

    /**
     * Serialize ItemStack to Base64 string for database storage.
     */
    private String serializeItem(ItemStack item) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream boos = new org.bukkit.util.io.BukkitObjectOutputStream(baos);
            boos.writeObject(item);
            boos.close();
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Deserialize ItemStack from Base64 string.
     */
    private ItemStack deserializeItem(String data) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(data);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
            org.bukkit.util.io.BukkitObjectInputStream bois = new org.bukkit.util.io.BukkitObjectInputStream(bais);
            ItemStack item = (ItemStack) bois.readObject();
            bois.close();
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== DATABASE OPERATIONS ====================

    private void loadAllProgress() {
        // Load quest progress
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, quest_id, progress FROM full_moon_quest_progress WHERE event_id=?")) {
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
            e.printStackTrace();
        }

        // Load completed quests
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, quest_id FROM full_moon_quest_completed WHERE event_id=?")) {
            ps.setString(1, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);

                    completedQuests.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Load accepted quests
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, quest_id FROM full_moon_quest_accepted WHERE event_id=?")) {
            ps.setString(1, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);

                    acceptedQuests.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Load claimed rewards
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, quest_id FROM full_moon_quest_claimed WHERE event_id=?")) {
            ps.setString(1, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    int questId = rs.getInt(2);

                    claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveProgress(UUID playerId, int questId, int progress) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO full_moon_quest_progress(event_id, player_uuid, quest_id, progress) VALUES (?,?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.setInt(4, progress);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveCompletion(UUID playerId, int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO full_moon_quest_completed(event_id, player_uuid, quest_id) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveAcceptance(UUID playerId, int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO full_moon_quest_accepted(event_id, player_uuid, quest_id) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveClaim(UUID playerId, int questId) {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("REPLACE INTO full_moon_quest_claimed(event_id, player_uuid, quest_id) VALUES (?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
