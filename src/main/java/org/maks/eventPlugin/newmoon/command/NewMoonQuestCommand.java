package org.maks.eventPlugin.newmoon.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.gui.NewMoonQuestGUI;

/**
 * Command handler for /new_moon_quest
 * Opens the New Moon quest GUI for the player.
 * No parameters required.
 */
public class NewMoonQuestCommand implements CommandExecutor {

    private final NewMoonManager newMoonManager;
    private final NewMoonQuestGUI questGUI;

    public NewMoonQuestCommand(NewMoonManager newMoonManager, NewMoonQuestGUI questGUI) {
        this.newMoonManager = newMoonManager;
        this.questGUI = questGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§l[New Moon] §cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check if event is active
        if (!newMoonManager.isEventActive()) {
            player.sendMessage("§c§l[New Moon] §cThe New Moon event is not currently active!");
            return true;
        }

        // Open quest GUI
        questGUI.open(player);
        return true;
    }
}
