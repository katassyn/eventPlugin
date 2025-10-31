package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.gui.QuestGUI;

/**
 * Command handler for /fullmoon_quests.
 * Opens the quest GUI for players to view their quest progress.
 */
public class FullMoonQuestsCommand implements CommandExecutor {

    private final FullMoonManager fullMoonManager;
    private final QuestGUI questGUI;

    public FullMoonQuestsCommand(FullMoonManager fullMoonManager, QuestGUI questGUI) {
        this.fullMoonManager = fullMoonManager;
        this.questGUI = questGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("eventplugin.fullmoon_quests")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        // Check if event is active
        if (!fullMoonManager.isEventActive()) {
            player.sendMessage("§c§l[Full Moon] §cThe Full Moon event is not currently active!");
            return true;
        }

        questGUI.open(player);
        return true;
    }
}
