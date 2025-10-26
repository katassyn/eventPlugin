package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.gui.AdminRewardEditorGUI;
import org.maks.eventPlugin.gui.PlayerProgressGUI;

import java.util.Map;

public class EventCommand implements CommandExecutor {
    private final Map<String, EventManager> events;
    private final DatabaseManager database;
    private final PlayerProgressGUI progressGUI;
    private final AdminRewardEditorGUI rewardGUI;
    private final org.maks.eventPlugin.config.ConfigManager config;
    private org.maks.eventPlugin.gui.EventsMainGUI eventsMainGUI;
    private FullMoonManager fullMoonManager;

    public EventCommand(Map<String, EventManager> events, DatabaseManager database,
                        PlayerProgressGUI progressGUI, AdminRewardEditorGUI rewardGUI,
                        org.maks.eventPlugin.config.ConfigManager config) {
        this.events = events;
        this.database = database;
        this.progressGUI = progressGUI;
        this.rewardGUI = rewardGUI;
        this.config = config;
    }

    /**
     * Set the EventsMainGUI instance (called after initialization).
     */
    public void setEventsMainGUI(org.maks.eventPlugin.gui.EventsMainGUI eventsMainGUI) {
        this.eventsMainGUI = eventsMainGUI;
    }

    /**
     * Set the FullMoonManager instance (called after initialization).
     */
    public void setFullMoonManager(FullMoonManager fullMoonManager) {
        this.fullMoonManager = fullMoonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "hub" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }
                if (!player.hasPermission("eventplugin.hub")) {
                    player.sendMessage("§cYou don't have permission to use the events hub!");
                    return true;
                }
                if (eventsMainGUI != null) {
                    eventsMainGUI.open(player);
                } else {
                    player.sendMessage("§cEvents hub is not available!");
                }
            }
            case "start" -> {
                if (!sender.hasPermission("eventplugin.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage("Usage: /event start <id>");
                    return true;
                }
                String id = args[1];
                EventManager manager = events.computeIfAbsent(id, k -> {
                    EventManager em = new EventManager(database, k);
                    em.setConfigManager(config);
                    return em;
                });

                var sec = config.getSection("events." + id);
                String name = sec != null ? sec.getString("name", id) : manager.getName();
                String desc = sec != null ? sec.getString("description", "") : manager.getDescription();
                int max = sec != null ? sec.getInt("max_progress", manager.getMaxProgress()) : manager.getMaxProgress();
                long duration = 0L;
                if (sec != null) {
                    int days = sec.getInt("duration_days", 0);
                    if (days > 0) duration = days * 86400L;
                }

                manager.start(name, desc, max, duration);
                config.set("events." + id + ".active", true);
                sender.sendMessage("Started event " + id);
            }
            case "stop" -> {
                if (!sender.hasPermission("eventplugin.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage("Usage: /event stop <id>");
                    return true;
                }
                String id = args[1];
                EventManager m = events.get(id);
                if (m != null) {
                    // Special handling for Full Moon event - reset quests
                    if (id.equalsIgnoreCase("full_moon") && fullMoonManager != null) {
                        fullMoonManager.stopEvent();
                        sender.sendMessage("Stopped event " + id + " (quest progress reset)");
                    } else {
                        m.stop();
                        sender.sendMessage("Stopped event " + id);
                    }
                    config.set("events." + id + ".active", false);
                } else {
                    sender.sendMessage("Event not found: " + id);
                }
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) return true;
                // Prefer an explicitly provided ID; otherwise choose the first ACTIVE event
                String id = null;
                if (args.length >= 2) {
                    id = args[1];
                } else {
                    id = events.entrySet().stream()
                            .filter(e -> e.getValue() != null && e.getValue().isActive())
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(null);
                }
                if (id == null) {
                    player.sendMessage("No active event.");
                    return true;
                }
                EventManager m = events.get(id);
                if (m == null || !m.isActive()) {
                    player.sendMessage("Event not active.");
                    return true;
                }
                m.checkExpiry();
                if (!m.isActive()) {
                    player.sendMessage("Event expired.");
                    return true;
                }
                progressGUI.open(player, m);
            }
            case "rewards" -> {
                if (!(sender instanceof Player player)) return true;
                if (!sender.hasPermission("eventplugin.admin")) return true;
                if (args.length < 2) {
                    player.sendMessage("Usage: /event rewards <id>");
                    return true;
                }
                String id = args[1];
                EventManager manager = events.computeIfAbsent(id, k -> {
                    EventManager em = new EventManager(database, k);
                    em.setConfigManager(config);
                    return em;
                });
                rewardGUI.open(player, manager);
            }
            default -> sender.sendMessage("Unknown subcommand");
        }
        return true;
    }
}
