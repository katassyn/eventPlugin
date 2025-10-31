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
     * Set the FullMoonManager instance (called after initialization).
     */
    public void setFullMoonManager(FullMoonManager fullMoonManager) {
        this.fullMoonManager = fullMoonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
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
