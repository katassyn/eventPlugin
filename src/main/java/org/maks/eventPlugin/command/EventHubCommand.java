package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.gui.EventsMainGUI;

public class EventHubCommand implements CommandExecutor {
    private final EventsMainGUI eventsMainGUI;

    public EventHubCommand(EventsMainGUI eventsMainGUI) {
        this.eventsMainGUI = eventsMainGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

        return true;
    }
}
