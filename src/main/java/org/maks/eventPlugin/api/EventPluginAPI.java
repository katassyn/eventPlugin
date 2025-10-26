package org.maks.eventPlugin.api;

import org.maks.eventPlugin.eventsystem.EventManager;

import java.util.*;

/**
 * Public API for EventPlugin.
 * Other plugins can use this to check event status and get event information.
 */
public class EventPluginAPI {

    private static Map<String, EventManager> eventManagers = new HashMap<>();

    /**
     * Initialize the API with event managers.
     * Called by EventPlugin on startup.
     * @param managers Map of event ID -> EventManager
     */
    public static void initialize(Map<String, EventManager> managers) {
        eventManagers = managers;
    }

    /**
     * Get all available event IDs.
     * @return List of event IDs (e.g., "monster_hunt", "full_moon")
     */
    public static List<String> getAllEventIds() {
        return new ArrayList<>(eventManagers.keySet());
    }

    /**
     * Get all events with their information.
     * @return Map of event ID -> EventInfo
     */
    public static Map<String, EventInfo> getAllEvents() {
        Map<String, EventInfo> events = new LinkedHashMap<>();

        for (Map.Entry<String, EventManager> entry : eventManagers.entrySet()) {
            String eventId = entry.getKey();
            EventManager manager = entry.getValue();

            events.put(eventId, new EventInfo(
                eventId,
                manager.getName(),
                manager.getDescription(),
                manager.isActive()
            ));
        }

        return events;
    }

    /**
     * Check if an event is currently active.
     * @param eventId The event ID to check
     * @return True if the event is active, false otherwise
     */
    public static boolean isEventActive(String eventId) {
        EventManager manager = eventManagers.get(eventId);
        return manager != null && manager.isActive();
    }

    /**
     * Get the display name of an event.
     * @param eventId The event ID
     * @return The event name, or the eventId if not found
     */
    public static String getEventName(String eventId) {
        EventManager manager = eventManagers.get(eventId);
        return manager != null ? manager.getName() : eventId;
    }

    /**
     * Get the description of an event.
     * @param eventId The event ID
     * @return The event description, or empty string if not found
     */
    public static String getEventDescription(String eventId) {
        EventManager manager = eventManagers.get(eventId);
        return manager != null ? manager.getDescription() : "";
    }

    /**
     * Check if an event exists.
     * @param eventId The event ID to check
     * @return True if the event exists, false otherwise
     */
    public static boolean eventExists(String eventId) {
        return eventManagers.containsKey(eventId);
    }

    /**
     * Get the EventManager for an event (for advanced usage).
     * @param eventId The event ID
     * @return The EventManager, or null if not found
     */
    public static EventManager getEventManager(String eventId) {
        return eventManagers.get(eventId);
    }

    /**
     * Class to hold event information.
     */
    public static class EventInfo {
        private final String id;
        private final String name;
        private final String description;
        private final boolean active;

        public EventInfo(String id, String name, String description, boolean active) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.active = active;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isActive() {
            return active;
        }
    }
}
