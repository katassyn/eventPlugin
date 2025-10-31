package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.gui.PlayerProgressGUI;

import java.util.Map;

/**
 * Command handler for /event_gui.
 * Opens the event progress GUI for the player.
 */
public class EventGUICommand implements CommandExecutor {
    private final Map<String, EventManager> events;
    private final PlayerProgressGUI progressGUI;

    public EventGUICommand(Map<String, EventManager> events, PlayerProgressGUI progressGUI) {
        this.events = events;
        this.progressGUI = progressGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("eventplugin.event.gui")) {
            player.sendMessage("§cYou don't have permission to view event progress!");
            return true;
        }

        // Prefer an explicitly provided ID; otherwise choose the first ACTIVE event
        String id = null;
        if (args.length >= 1) {
            id = args[0];
        } else {
            id = events.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().isActive())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        if (id == null) {
            player.sendMessage("§cNo active event.");
            return true;
        }

        EventManager m = events.get(id);
        if (m == null || !m.isActive()) {
            player.sendMessage("§cEvent not active.");
            return true;
        }

        m.checkExpiry();
        if (!m.isActive()) {
            player.sendMessage("§cEvent expired.");
            return true;
        }

        progressGUI.open(player, m);
        return true;
    }
}
