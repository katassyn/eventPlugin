package org.maks.eventPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.gui.AdminQuestRewardEditorGUI;
import org.maks.eventPlugin.fullmoon.gui.MapSelectionGUI;

/**
 * Command handler for /fullmoon.
 * All subcommands require the event to be active (except admin commands).
 */
public class FullMoonCommand implements CommandExecutor {

    private final FullMoonManager fullMoonManager;
    private final MapSelectionGUI mapSelectionGUI;
    private final AdminQuestRewardEditorGUI adminQuestRewardGUI;

    public FullMoonCommand(FullMoonManager fullMoonManager, MapSelectionGUI mapSelectionGUI, AdminQuestRewardEditorGUI adminQuestRewardGUI) {
        this.fullMoonManager = fullMoonManager;
        this.mapSelectionGUI = mapSelectionGUI;
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
        if (!fullMoonManager.isEventActive()) {
            player.sendMessage("§c§l[Full Moon] §cThe Full Moon event is not currently active!");
            return true;
        }

        if (args.length == 0) {
            // Default: open map selection GUI
            if (!player.hasPermission("eventplugin.fullmoon.gui")) {
                player.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            mapSelectionGUI.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (!player.hasPermission("eventplugin.fullmoon.gui")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                mapSelectionGUI.open(player);
            }
            default -> {
                player.sendMessage("§c§l[Full Moon] §7Available commands:");
                player.sendMessage("§e/fullmoon §7- Open difficulty selection");
                player.sendMessage("§e/fullmoon_quests §7- View quest progress");
                player.sendMessage("§e/fullmoon admin §7- Admin commands");
            }
        }

        return true;
    }

    /**
     * Handle admin subcommands.
     */
    private boolean handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("eventplugin.fullmoon.admin")) {
            player.sendMessage("§cYou don't have permission to use admin commands!");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§c§l[Full Moon Admin] §7Available commands:");
            player.sendMessage("§e/fullmoon admin quest_rewards §7- Manage quest rewards");
            player.sendMessage("§e/fullmoon admin reset §7- Reset all Full Moon data");
            player.sendMessage("§e/fullmoon admin resetplayer <player> §7- Reset player's data");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "quest_rewards", "questrewards" -> {
                if (adminQuestRewardGUI != null) {
                    adminQuestRewardGUI.open(player);
                    player.sendMessage("§a§l[Full Moon Admin] §aOpening quest rewards editor...");
                } else {
                    player.sendMessage("§cQuest reward editor is not available!");
                }
            }
            case "reset" -> {
                fullMoonManager.resetAllData();
                player.sendMessage("§a§l[Full Moon Admin] §aAll Full Moon data has been reset!");
            }
            case "resetplayer" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /fullmoon admin resetplayer <player>");
                    return true;
                }

                Player target = player.getServer().getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found!");
                    return true;
                }

                fullMoonManager.resetPlayerData(target.getUniqueId());
                player.sendMessage("§a§l[Full Moon Admin] §aReset data for player: " + target.getName());
            }
            default -> {
                player.sendMessage("§cUnknown admin command. Use §e/fullmoon admin §cfor help.");
            }
        }

        return true;
    }
}
