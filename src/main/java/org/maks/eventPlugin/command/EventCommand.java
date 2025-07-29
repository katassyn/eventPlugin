package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.gui.PlayerProgressGUI;

public class EventCommand implements CommandExecutor {
    private final EventManager eventManager;
    private final PlayerProgressGUI gui;

    public EventCommand(EventManager eventManager, PlayerProgressGUI gui) {
        this.eventManager = eventManager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "toggle" -> {
                if (!sender.hasPermission("eventplugin.admin")) return true;
                eventManager.toggle();
                sender.sendMessage("Event toggled. Active: " + eventManager.isActive());
            }
            case "gui" -> {
                if (sender instanceof Player player) {
                    gui.open(player);
                }
            }
            case "addreward" -> {
                if (!(sender instanceof Player player)) return true;
                if (!sender.hasPermission("eventplugin.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage("Usage: /event addreward <progress>");
                    return true;
                }
                int prog;
                try {
                    prog = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid number");
                    return true;
                }
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    sender.sendMessage("Hold the reward item in your hand");
                    return true;
                }
                eventManager.addReward(prog, item.clone());
                sender.sendMessage("Reward added for progress " + prog);
            }
            default -> sender.sendMessage("Unknown subcommand");
        }
        return true;
    }
}
