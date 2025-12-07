package org.maks.eventPlugin.winterevent.wintercave.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveGUI;
import org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveRewardsGUI;

/**
 * Command handler for /winter_cave and /winter_cave_rewards.
 */
public class WinterCaveCommand implements CommandExecutor {
    private final WinterCaveGUI caveGUI;
    private final WinterCaveRewardsGUI rewardsGUI;

    public WinterCaveCommand(WinterCaveGUI caveGUI, WinterCaveRewardsGUI rewardsGUI) {
        this.caveGUI = caveGUI;
        this.rewardsGUI = rewardsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // /winter_cave - open entry GUI
        if (command.getName().equalsIgnoreCase("winter_cave")) {
            if (!player.hasPermission("eventplugin.winter_cave")) {
                player.sendMessage("§c§l[Winter Event] §cYou don't have permission to enter Winter Cave!");
                return true;
            }

            caveGUI.open(player);
            return true;
        }

        // /winter_cave_rewards - open admin rewards GUI
        if (command.getName().equalsIgnoreCase("winter_cave_rewards")) {
            if (!player.hasPermission("eventplugin.admin.winter_cave_rewards")) {
                player.sendMessage("§c§l[Winter Event] §cYou don't have permission to manage rewards!");
                return true;
            }

            rewardsGUI.open(player);
            return true;
        }

        return false;
    }
}
