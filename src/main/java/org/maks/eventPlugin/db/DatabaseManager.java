package org.maks.eventPlugin.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private HikariDataSource dataSource;

    public void connect(String host, String port, String database, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername(user);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource = new HikariDataSource(config);
        Bukkit.getLogger().info("[EventPlugin] Connected to MySQL");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Migrate existing event_rewards table to new schema with reward_id
     */
    private void migrateRewardsTable() {
        try (Connection conn = getConnection();
             var st = conn.createStatement()) {

            // Check if old table exists
            var tableRs = conn.getMetaData().getTables(null, null, "event_rewards", null);
            boolean tableExists = tableRs.next();
            tableRs.close();

            if (!tableExists) {
                // Table doesn't exist yet - will be created by setupTables()
                Bukkit.getLogger().info("[EventPlugin] event_rewards table doesn't exist yet - will be created with new schema");
                return;
            }

            // Table exists - check if it has the old schema (no reward_id column)
            var colRs = conn.getMetaData().getColumns(null, null, "event_rewards", "reward_id");
            boolean hasRewardId = colRs.next();
            colRs.close();

            if (!hasRewardId) {
                // Old schema detected - migrate
                Bukkit.getLogger().warning("[EventPlugin] ===================================");
                Bukkit.getLogger().warning("[EventPlugin] OLD DATABASE SCHEMA DETECTED!");
                Bukkit.getLogger().warning("[EventPlugin] Migrating event_rewards table...");
                Bukkit.getLogger().warning("[EventPlugin] ===================================");

                // Create new table with correct schema
                st.executeUpdate("CREATE TABLE event_rewards_new(" +
                        "reward_id INT AUTO_INCREMENT PRIMARY KEY," +
                        "event_id VARCHAR(100)," +
                        "required INT," +
                        "item TEXT NOT NULL," +
                        "INDEX(event_id))");

                // Copy data from old table
                var copyRs = st.executeQuery("SELECT COUNT(*) FROM event_rewards");
                int oldCount = 0;
                if (copyRs.next()) {
                    oldCount = copyRs.getInt(1);
                }
                copyRs.close();

                if (oldCount > 0) {
                    st.executeUpdate("INSERT INTO event_rewards_new (event_id, required, item) " +
                            "SELECT event_id, required, item FROM event_rewards");
                    Bukkit.getLogger().info("[EventPlugin] Copied " + oldCount + " rewards to new table");
                }

                // Drop old table and rename new one
                st.executeUpdate("DROP TABLE event_rewards");
                st.executeUpdate("RENAME TABLE event_rewards_new TO event_rewards");

                Bukkit.getLogger().warning("[EventPlugin] ===================================");
                Bukkit.getLogger().warning("[EventPlugin] MIGRATION COMPLETED!");
                Bukkit.getLogger().warning("[EventPlugin] You can now add multiple rewards");
                Bukkit.getLogger().warning("[EventPlugin] with the same progress requirement!");
                Bukkit.getLogger().warning("[EventPlugin] ===================================");
            } else {
                Bukkit.getLogger().info("[EventPlugin] event_rewards table already has new schema - no migration needed");
            }
        } catch (SQLException ex) {
            Bukkit.getLogger().severe("[EventPlugin] ===================================");
            Bukkit.getLogger().severe("[EventPlugin] MIGRATION FAILED!");
            Bukkit.getLogger().severe("[EventPlugin] Error: " + ex.getMessage());
            Bukkit.getLogger().severe("[EventPlugin] ===================================");
            ex.printStackTrace();
        }
    }

    /**
     * Create tables used by the plugin if they don't exist.
     */
    public void setupTables() {
        migrateRewardsTable();
        try (Connection conn = getConnection();
             var st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS events(" +
                    "event_id VARCHAR(100) PRIMARY KEY," +
                    "name VARCHAR(100)," +
                    "description TEXT," +
                    "end_time BIGINT," +
                    "max_progress INT," +
                    "active BOOLEAN DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_progress(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "progress INT NOT NULL," +
                    "PRIMARY KEY(event_id, player_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_rewards(" +
                    "reward_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "event_id VARCHAR(100)," +
                    "required INT," +
                    "item TEXT NOT NULL," +
                    "INDEX(event_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_claimed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "reward INT," +
                    "PRIMARY KEY(event_id, player_uuid, reward))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_buffs(" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "buff_end BIGINT NOT NULL)");

            // Full Moon quest tables
            st.executeUpdate("CREATE TABLE IF NOT EXISTS full_moon_quest_progress(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "progress INT NOT NULL," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS full_moon_quest_completed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS full_moon_quest_accepted(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS full_moon_quest_claimed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");

            // Full Moon quest rewards (similar to event_rewards)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS full_moon_quest_rewards(" +
                    "reward_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "event_id VARCHAR(100)," +
                    "quest_id INT," +
                    "item TEXT NOT NULL," +
                    "INDEX(event_id, quest_id))");

            // New Moon quest tables
            st.executeUpdate("CREATE TABLE IF NOT EXISTS new_moon_quest_progress(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "progress INT NOT NULL," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS new_moon_quest_completed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS new_moon_quest_accepted(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS new_moon_quest_claimed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");

            // New Moon quest rewards
            st.executeUpdate("CREATE TABLE IF NOT EXISTS new_moon_quest_rewards(" +
                    "reward_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "event_id VARCHAR(100)," +
                    "quest_id INT," +
                    "item TEXT NOT NULL," +
                    "INDEX(event_id, quest_id))");

            // Event showcase rewards for rewards preview GUI
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_showcase_rewards(" +
                    "event_id VARCHAR(100) PRIMARY KEY," +
                    "gui_title VARCHAR(255)," +
                    "serialized_inventory TEXT NOT NULL)");

            // Winter Event tables
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_cave_daily_rewards(" +
                    "event_id VARCHAR(64) NOT NULL," +
                    "day INT NOT NULL," +
                    "item TEXT NOT NULL," +
                    "PRIMARY KEY(event_id, day))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_cave_claims(" +
                    "event_id VARCHAR(64) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "event_day INT NOT NULL," +
                    "claimed_at BIGINT NOT NULL," +
                    "PRIMARY KEY(event_id, player_uuid, event_day))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_cave_active_instance(" +
                    "event_id VARCHAR(64) PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "entry_time BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_summit_instances(" +
                    "instance_id VARCHAR(36) PRIMARY KEY," +
                    "event_id VARCHAR(64) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "boss_type VARCHAR(32) NOT NULL," +
                    "difficulty VARCHAR(32) NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "INDEX(player_uuid))");

            // Winter Event Quest System tables
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_event_quest_progress(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "progress INT NOT NULL," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_event_quest_completed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_event_quest_accepted(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_event_quest_claimed(" +
                    "event_id VARCHAR(100)," +
                    "player_uuid VARCHAR(36)," +
                    "quest_id INT," +
                    "PRIMARY KEY(event_id, player_uuid, quest_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS winter_event_quest_rewards(" +
                    "reward_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "event_id VARCHAR(100)," +
                    "quest_id INT," +
                    "item TEXT NOT NULL," +
                    "INDEX(event_id, quest_id))");

            Bukkit.getLogger().info("[EventPlugin] Database tables setup complete");

        } catch (SQLException ex) {
            Bukkit.getLogger().severe("[EventPlugin] Could not setup database tables: " + ex.getMessage());
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
