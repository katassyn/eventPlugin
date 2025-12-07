package org.maks.eventPlugin.winterevent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.winterevent.gui.AdminWinterQuestRewardEditorGUI;
import org.maks.eventPlugin.winterevent.gui.WinterQuestGUI;

/**
 * Command handler for /winter_quests
 * Usage:
 *   /winter_quests - Opens quest GUI
 *   /winter_quests admin - Opens admin reward editor (requires permission)
 */
public class WinterQuestCommand implements CommandExecutor {

    private final WinterQuestGUI questGUI;
    private final AdminWinterQuestRewardEditorGUI adminGUI;

    public WinterQuestCommand(WinterQuestGUI questGUI, AdminWinterQuestRewardEditorGUI adminGUI) {
        this.questGUI = questGUI;
        this.adminGUI = adminGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        // /winter_quests admin - Open admin GUI
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("eventplugin.admin.winter_quest_rewards")) {
                player.sendMessage("§c§lYou don't have permission to use this!");
                return true;
            }

            adminGUI.open(player);
            return true;
        }

        // /winter_quests - Open player quest GUI
        if (!player.hasPermission("eventplugin.winter_quests")) {
            player.sendMessage("§c§lYou don't have permission to use this!");
            return true;
        }

        questGUI.open(player);
        return true;
    }
}
