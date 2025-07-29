package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.gui.AdminRewardEditorGUI;
import org.maks.eventPlugin.gui.PlayerProgressGUI;

import java.util.Map;

public class EventCommand implements CommandExecutor {
    private final Map<String, EventManager> events;
    private final DatabaseManager database;
    private final PlayerProgressGUI progressGUI;
    private final AdminRewardEditorGUI rewardGUI;

    public EventCommand(Map<String, EventManager> events, DatabaseManager database, PlayerProgressGUI progressGUI, AdminRewardEditorGUI rewardGUI) {
        this.events = events;
        this.database = database;
        this.progressGUI = progressGUI;
        this.rewardGUI = rewardGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!sender.hasPermission("eventplugin.admin")) return true;
                if (args.length < 3) {
                    sender.sendMessage("Usage: /event start <id> <durationSeconds> [maxProgress]");
                    return true;
                }
                String id = args[1];
                long duration = Long.parseLong(args[2]);
                int max = args.length >= 4 ? Integer.parseInt(args[3]) : 25000;
                EventManager manager = events.computeIfAbsent(id, k -> new EventManager(database, k));
                manager.start(id, "", max, duration);
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
                    m.stop();
                    sender.sendMessage("Stopped event " + id);
                } else {
                    sender.sendMessage("Event not found: " + id);
                }
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) return true;
                String id = args.length >= 2 ? args[1] : events.keySet().stream().findFirst().orElse(null);
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
                EventManager manager = events.computeIfAbsent(id, k -> new EventManager(database, k));
                rewardGUI.open(player, manager);
            }
            default -> sender.sendMessage("Unknown subcommand");
        }
        return true;
    }
}
