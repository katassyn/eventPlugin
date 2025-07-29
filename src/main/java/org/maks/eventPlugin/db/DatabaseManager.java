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
     * Create tables used by the plugin if they don't exist.
     */
    public void setupTables() {
        try (Connection conn = getConnection();
             var st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_progress(" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "progress INT NOT NULL," +
                    "buff_end BIGINT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_rewards(" +
                    "required INT PRIMARY KEY," +
                    "item TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS event_claimed(" +
                    "player_uuid VARCHAR(36)," +
                    "reward INT," +
                    "PRIMARY KEY(player_uuid, reward))");
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
