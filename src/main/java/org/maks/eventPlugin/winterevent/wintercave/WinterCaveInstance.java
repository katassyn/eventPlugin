package org.maks.eventPlugin.winterevent.wintercave;

import java.util.UUID;

/**
 * Represents a single Winter Cave instance for one player.
 * Tracks entry time, mob kill status, and timer task.
 */
public class WinterCaveInstance {
    private final UUID playerId;
    private final long entryTime;
    private boolean mobKilled;
    private int timerTaskId;

    public WinterCaveInstance(UUID playerId, long entryTime) {
        this.playerId = playerId;
        this.entryTime = entryTime;
        this.mobKilled = false;
        this.timerTaskId = -1;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getEntryTime() {
        return entryTime;
    }

    public boolean isMobKilled() {
        return mobKilled;
    }

    public void setMobKilled(boolean mobKilled) {
        this.mobKilled = mobKilled;
    }

    public int getTimerTaskId() {
        return timerTaskId;
    }

    public void setTimerTaskId(int timerTaskId) {
        this.timerTaskId = timerTaskId;
    }

    /**
     * Check if the instance has expired (5 minutes).
     */
    public boolean hasExpired(int timerMinutes) {
        long elapsed = System.currentTimeMillis() - entryTime;
        return elapsed >= timerMinutes * 60 * 1000L;
    }
}
