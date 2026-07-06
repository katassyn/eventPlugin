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
    private boolean rewardClaimed;
    private int timerTaskId;
    private int postClaimTaskId;

    public WinterCaveInstance(UUID playerId, long entryTime) {
        this.playerId = playerId;
        this.entryTime = entryTime;
        this.mobKilled = false;
        this.rewardClaimed = false;
        this.timerTaskId = -1;
        this.postClaimTaskId = -1;
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

    public int getPostClaimTaskId() {
        return postClaimTaskId;
    }

    public void setPostClaimTaskId(int postClaimTaskId) {
        this.postClaimTaskId = postClaimTaskId;
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean rewardClaimed) {
        this.rewardClaimed = rewardClaimed;
    }

    /**
     * Check if the instance has expired (5 minutes).
     */
    public boolean hasExpired(int timerMinutes) {
        long elapsed = System.currentTimeMillis() - entryTime;
        return elapsed >= timerMinutes * 60 * 1000L;
    }
}
