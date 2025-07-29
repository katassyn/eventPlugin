package org.maks.eventPlugin.util;

public class TimeUtil {
    public static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        return days + "d " + hours + "h " + minutes + "m";
    }
}
