package org.maks.eventPlugin.newmoon.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.newmoon.NewMoonManager;
import org.maks.eventPlugin.newmoon.gui.Map1SelectionGUI;
import org.maks.eventPlugin.newmoon.gui.AdminQuestRewardEditorGUI;

/**
 * Command handler for /new_moon.
 * Provides player GUI access and admin management commands.
 *
 * Usage:
 * /new_moon - Open Map 1 (Black Bog) difficulty selection GUI
 * /new_moon admin - Show admin command list
 * /new_moon admin quest_rewards - Manage quest rewards
 * /new_moon admin reset - Reset all New Moon data
 * /new_moon admin resetplayer <player> - Reset specific player's data
 */
public class NewMoonCommand implements CommandExecutor {

    private final NewMoonManager newMoonManager;
    private final Map1SelectionGUI map1SelectionGUI;
    private final AdminQuestRewardEditorGUI adminQuestRewardGUI;

    public NewMoonCommand(NewMoonManager newMoonManager, Map1SelectionGUI map1SelectionGUI, AdminQuestRewardEditorGUI adminQuestRewardGUI) {
        this.newMoonManager = newMoonManager;
        this.map1SelectionGUI = map1SelectionGUI;
        this.adminQuestRewardGUI = adminQuestRewardGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        // Admin commands don't require event to be active
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return handleAdminCommand(player, args);
        }

        // All other commands require event to be active
        if (!newMoonManager.isEventActive()) {
            player.sendMessage("§c§l[New Moon] §cThe New Moon event is not currently active!");
            return true;
        }

        if (args.length == 0) {
            // Default: open Map 1 (Black Bog) selection GUI
            if (!player.hasPermission("eventplugin.newmoon.gui")) {
                player.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            map1SelectionGUI.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (!player.hasPermission("eventplugin.newmoon.gui")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                map1SelectionGUI.open(player);
            }
            default -> {
                player.sendMessage("§c§l[New Moon] §7Available commands:");
                player.sendMessage("§e/new_moon §7- Open Black Bog difficulty selection");
                player.sendMessage("§e/new_moon_quest §7- View quest progress");
                player.sendMessage("§e/new_moon admin §7- Admin commands");
            }
        }

        return true;
    }

    /**
     * Handle admin subcommands.
     */
    private boolean handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("eventplugin.newmoon.admin")) {
            player.sendMessage("§cYou don't have permission to use admin commands!");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§c§l[New Moon Admin] §7Available commands:");
            player.sendMessage("§e/new_moon admin quest_rewards §7- Manage quest rewards");
            player.sendMessage("§e/new_moon admin reset §7- Reset all New Moon data");
            player.sendMessage("§e/new_moon admin resetplayer <player> §7- Reset player's data");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "quest_rewards", "questrewards" -> {
                if (adminQuestRewardGUI != null) {
                    adminQuestRewardGUI.open(player);
                    player.sendMessage("§a§l[New Moon Admin] §aOpening quest rewards editor...");
                } else {
                    player.sendMessage("§cQuest reward editor is not available!");
                }
            }
            case "reset" -> {
                newMoonManager.resetAllData();
                player.sendMessage("§a§l[New Moon Admin] §aAll New Moon data has been reset!");
            }
            case "resetplayer" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /new_moon admin resetplayer <player>");
                    return true;
                }

                Player target = player.getServer().getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found!");
                    return true;
                }

                newMoonManager.resetPlayerData(target.getUniqueId());
                player.sendMessage("§a§l[New Moon Admin] §aReset data for player: " + target.getName());
            }
            default -> {
                player.sendMessage("§cUnknown admin command. Use §e/new_moon admin §cfor help.");
            }
        }

        return true;
    }
}
