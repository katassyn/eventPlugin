package org.maks.eventPlugin.eventsystem;

import org.bukkit.entity.Player;
import org.maks.eventPlugin.db.DatabaseManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the attrie buff for players.
 */
import java.sql.SQLException;

public class BuffManager {
    private final Map<UUID, Instant> buffEnd = new HashMap<>();
    private final DatabaseManager database;

    public BuffManager(DatabaseManager database) {
        this.database = database;
        loadBuffs();
    }

    public boolean hasBuff(Player player) {
        Instant end = buffEnd.get(player.getUniqueId());
        return end != null && end.isAfter(Instant.now());
    }

    /**
     * Get remaining buff duration for the given player in milliseconds.
     * Returns 0 if the player has no active buff.
     */
    public long getRemaining(Player player) {
        Instant end = buffEnd.get(player.getUniqueId());
        if (end == null) return 0L;
        long remaining = end.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0L, remaining);
    }

    public void applyBuff(Player player, int days) {
        Instant end = Instant.now().plusSeconds(days * 24L * 3600L);
        buffEnd.put(player.getUniqueId(), end);
        saveBuff(player.getUniqueId(), end);
    }

    private void loadBuffs() {
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("SELECT player_uuid, buff_end FROM event_buffs")) {

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    long end = rs.getLong(2);
                    if (end > 0) buffEnd.put(id, Instant.ofEpochMilli(end));
                }
            }
        } catch (SQLException ignored) {
        }
    }

    private void saveBuff(UUID uuid, Instant end) {
        long millis = end.toEpochMilli();
        try (var conn = database.getConnection();
             var ps = conn.prepareStatement("UPDATE event_buffs SET buff_end=? WHERE player_uuid=?")) {
            ps.setLong(1, millis);
            ps.setString(2, uuid.toString());
            if (ps.executeUpdate() == 0) {
                try (var ins = conn.prepareStatement("INSERT INTO event_buffs(player_uuid, buff_end) VALUES (?,?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setLong(2, millis);

                    ins.executeUpdate();
                }
            }
        } catch (SQLException ignored) {
        }
    }
}
