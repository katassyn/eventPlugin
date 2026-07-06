package org.maks.eventPlugin.winterevent.bigpresent;

public enum BigPresentTier {
    INFERNAL,
    HELL,
    BLOOD;

    public static BigPresentTier fromString(String s) {
        if (s == null) return null;
        switch (s.toLowerCase()) {
            case "infernal": return INFERNAL;
            case "hell": return HELL;
            case "blood": return BLOOD;
            default: return null;
        }
    }

    public String getDisplayName() {
        switch (this) {
            case INFERNAL: return "Infernal";
            case HELL: return "Hell";
            case BLOOD: return "Blood";
            default: return name();
        }
    }
}